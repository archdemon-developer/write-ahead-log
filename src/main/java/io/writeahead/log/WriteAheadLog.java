package io.writeahead.log;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.metrics.WalMetricsQuery;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.results.AppendResult;
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

  public WriteAheadLog(WalConfiguration config) throws IOException {
    this.config = config;
    this.segmentStore = new SegmentStoreManager(config);

    this.executor = Executors.newVirtualThreadPerTaskExecutor();
    this.writeQueue = new LinkedBlockingQueue<>(config.batchSize() * 2);
    executor.submit(this::writerLoop);

    log.info(
        "WriteAheadLog initialized: logDir={}, batchSize={}, maxSegmentSize={}",
        config.logDir(),
        config.batchSize(),
        config.maxSegmentSize());
  }

  public AppendResult append(LogEntry entry) throws IOException {
    if (shutdownRequested) {
      throw new IOException("WriteAheadLog is closed");
    }

    try {
      long myLsn = allocateLsn(entry.size());
      writeQueue.put(new WriteTask(entry, myLsn));

      return AppendResult.successfulAppendNoFlush(
          (int) writeQueue.size(),
          segmentStore.getCurrentSequenceNumber(),
          segmentStore.getCurrentEntryCount(),
          segmentStore.getCurrentStreamSize(),
          segmentStore.getCurrentMinTimestamp(),
          segmentStore.getCurrentMaxTimestamp());

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted enqueueing entry", e);
    }
  }

  public AppendResult writeBatch() throws IOException {
    if (shutdownRequested) {
      throw new IOException("WriteAheadLog is closed");
    }

    lsnLock.lock();
    try {
      long targetLsn = currentLsn;

      while (flushedLsn < targetLsn) {
        try {
          boolean timedOut = lsnFlushed.await(1, TimeUnit.SECONDS);

          if (shutdownRequested) {
            throw new IOException("WriteAheadLog is closed");
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted waiting for flush", e);
        }
      }

      return AppendResult.successfulAppendWithFlush(
          segmentStore.getCurrentSequenceNumber(),
          segmentStore.getCurrentEntryCount(),
          segmentStore.getCurrentStreamSize(),
          segmentStore.getCurrentMinTimestamp(),
          segmentStore.getCurrentMaxTimestamp());

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

  private void writerLoop() {
    log.info("WriteAheadLog writer thread started");

    List<WriteTask> batch = new ArrayList<>();

    try {
      while (!shutdownRequested) {
        batch.clear();

        try {
          // Wait for first entry (timeout allows periodic flush)
          WriteTask first = writeQueue.poll(100, TimeUnit.MILLISECONDS);

          if (first == null) {
            // Timeout: flush pending entries if any
            int pending = segmentStore.getCurrentEntryCount();
            if (pending > 0) {
              flushBatch(batch);
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

      if (!batch.isEmpty()) {
        flushBatch(batch);
      }

      log.info("WriteAheadLog writer thread exited cleanly");

    } catch (IOException e) {
      log.error("WriteAheadLog writer thread crashed: {}", e.getMessage(), e);
      shutdownRequested = true;
      throw new RuntimeException("WriteAheadLog writer thread failed", e);
    }
  }

  private void flushBatch(List<WriteTask> batch) throws IOException {
    if (batch.isEmpty()) {
      return;
    }

    segmentStore.writeBatch();

    lsnLock.lock();
    try {
      WriteTask lastTask = batch.getLast();
      long lastLsn = lastTask.lsn() + lastTask.entry().size();
      flushedLsn = Math.max(flushedLsn, lastLsn);
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
