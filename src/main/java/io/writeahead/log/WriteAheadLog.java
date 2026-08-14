package io.writeahead.log;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.metrics.WalMetricsQuery;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.results.AppendResult;
import io.writeahead.log.models.results.TruncateResult;
import io.writeahead.log.models.states.BatchState;
import io.writeahead.log.models.states.WalSnapshot;
import io.writeahead.log.segments.SegmentStoreManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class WriteAheadLog {

  private static final Logger log = LoggerFactory.getLogger(WriteAheadLog.class);

  private final BlockingQueue<WriteTask> writeQueue;
  private final ExecutorService executor;

  private final SegmentStoreManager segmentStore;
  private final WalConfiguration config;

  private final ReentrantLock lsnLock = new ReentrantLock();
  private final Condition lsnFlushed = lsnLock.newCondition();
  private long currentLsn = 0;
  private long flushedLsn = 0;

  private volatile boolean shutdownRequested = false;
  private volatile Throwable writerCrashCause = null;

  public WriteAheadLog(WalConfiguration config) throws IOException {
    this.config = config;
    this.segmentStore = new SegmentStoreManager(config);

    this.executor = Executors.newVirtualThreadPerTaskExecutor();
    this.writeQueue = new LinkedBlockingQueue<>(config.batchSize() * 2);

    executor.submit(
        () -> {
          try {
            writerLoop();
          } catch (Throwable t) {
            writerCrashCause = t;
            shutdownRequested = true;
            lsnLock.lock();
            try {
              lsnFlushed.signalAll();
            } finally {
              lsnLock.unlock();
            }
          }
        });

    log.info(
        "WriteAheadLog initialized: logDir={}, batchSize={}, maxSegmentSize={}",
        config.logDir(),
        config.batchSize(),
        config.maxSegmentSize());
  }

  public AppendResult append(LogEntry entry) throws IOException {
    checkOpen();

    try {
      long myLsn = allocateLsn(entry.size());
      if (!writeQueue.offer(new WriteTask(entry, myLsn), 5, TimeUnit.SECONDS)) {
        checkOpen();
        throw new IOException("Write queue full — writer thread stalled");
      }

      long minTs = segmentStore.getCurrentMinTimestamp();
      long maxTs = segmentStore.getCurrentMaxTimestamp();

      if (minTs == Long.MAX_VALUE || maxTs == Long.MIN_VALUE) {
        minTs = entry.timestamp();
        maxTs = entry.timestamp();
      }
      return AppendResult.successfulAppendNoFlush(
          (int) writeQueue.size(),
          segmentStore.getCurrentSequenceNumber(),
          segmentStore.getCurrentEntryCount(),
          segmentStore.getCurrentStreamSize(),
          minTs,
          maxTs);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted enqueueing entry", e);
    }
  }

  public AppendResult writeBatch() throws IOException {
    checkOpen();

    lsnLock.lock();
    try {
      long targetLsn = currentLsn;

      while (flushedLsn < targetLsn) {
        try {
          lsnFlushed.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted waiting for flush", e);
        }
        Throwable crash = writerCrashCause;
        if (crash != null) {
          throw new IOException("WriteAheadLog writer crashed", crash);
        }
        if (shutdownRequested && flushedLsn < targetLsn) {
          throw new IOException("WriteAheadLog is closed");
        }
      }

      long entryCount = segmentStore.getCurrentEntryCount();
      return AppendResult.successfulAppendWithFlush(
          segmentStore.getCurrentSequenceNumber(),
          entryCount,
          segmentStore.getCurrentStreamSize(),
          entryCount > 0 ? segmentStore.getCurrentMinTimestamp() : 0L,
          entryCount > 0 ? segmentStore.getCurrentMaxTimestamp() : 0L);

    } finally {
      lsnLock.unlock();
    }
  }

  public List<LogEntry> readAllSegments() throws IOException {
    return segmentStore.readAllSegments();
  }

  public List<LogEntry> readAllAfterTimestamp(long timestamp) throws IOException {
    return segmentStore.readAllAfterTimestamp(timestamp);
  }

  public TruncateResult truncateBeforeTimestamp(long timestamp) throws IOException {
    checkOpen();
    return segmentStore.truncateBeforeTimestamp(timestamp);
  }

  public WalSnapshot getSnapshot() throws IOException {
    return segmentStore.getSnapshot();
  }

  public BatchState getBatchState() {
    return segmentStore.getBatchState();
  }

  public WalMetricsQuery getMetrics() throws IOException {
    return segmentStore.getMetrics();
  }

  public void close() throws IOException {
    log.info("Closing WriteAheadLog");

    shutdownRequested = true;

    executor.shutdown();

    try {
      if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        log.warn("WriteAheadLog writer did not shutdown gracefully, forcing");
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      executor.shutdownNow();
    }

    segmentStore.close();
    log.info("WriteAheadLog closed");
  }

  private void checkOpen() throws IOException {
    Throwable crash = writerCrashCause;
    if (crash != null) {
      throw new IOException("WriteAheadLog writer crashed", crash);
    }
    if (shutdownRequested) {
      throw new IOException("WriteAheadLog is closed");
    }
  }

  private void writerLoop() {
    log.info("WriteAheadLog writer thread started");
    List<WriteTask> batch = new ArrayList<>();

    try {
      while (!shutdownRequested) {
        batch.clear();

        try {
          WriteTask first = writeQueue.poll(100, TimeUnit.MILLISECONDS);

          if (first == null) {
            lsnLock.lock();
            try {
              if (flushedLsn < currentLsn) {
                lsnLock.unlock();
                try {
                  segmentStore.writeBatch();
                } finally {
                  lsnLock.lock();
                }
                flushedLsn = currentLsn;
                lsnFlushed.signalAll();
              }
            } finally {
              lsnLock.unlock();
            }
            continue;
          }

          batch.add(first);
          writeQueue.drainTo(batch, config.batchSize() - 1);

          for (WriteTask task : batch) {
            segmentStore.appendDirectly(task.entry());
          }

          if (batch.size() >= config.batchSize()) {
            flushBatch(batch);
          }

        } catch (InterruptedException e) {
          if (!shutdownRequested) {
            log.warn("WriteAheadLog writer interrupted (not shutdown)");
          }
          Thread.currentThread().interrupt();
          break;
        }
      }

      batch.clear();
      writeQueue.drainTo(batch);
      for (WriteTask task : batch) {
        segmentStore.appendDirectly(task.entry());
      }
      if (!batch.isEmpty()) {
        flushBatch(batch);
      } else {
        lsnLock.lock();
        try {
          if (flushedLsn < currentLsn) {
            lsnLock.unlock();
            try {
              segmentStore.writeBatch();
            } finally {
              lsnLock.lock();
            }
            flushedLsn = currentLsn;
            lsnFlushed.signalAll();
          }
        } finally {
          lsnLock.unlock();
        }
      }

      log.info("WriteAheadLog writer thread exited cleanly");

    } catch (IOException e) {
      log.error("WriteAheadLog writer thread crashed: {}", e.getMessage(), e);
      throw new RuntimeException("WriteAheadLog writer thread failed", e);
    }
  }

  private void flushBatch(List<WriteTask> batch) throws IOException {
    segmentStore.writeBatch();

    lsnLock.lock();
    try {
      WriteTask lastTask = batch.getLast();
      long newFlushedLsn = lastTask.lsn() + lastTask.entry().size();
      flushedLsn = Math.max(flushedLsn, newFlushedLsn);
      lsnFlushed.signalAll();
      log.debug("Flushed batch of {} entries, LSN now {}", batch.size(), flushedLsn);
    } finally {
      lsnLock.unlock();
    }
  }

  private long allocateLsn(int size) {
    lsnLock.lock();
    try {
      long lsn = currentLsn;
      currentLsn += size;
      return lsn;
    } finally {
      lsnLock.unlock();
    }
  }

  private record WriteTask(LogEntry entry, long lsn) {}
}
