package io.writeahead.log;

import io.writeahead.log.concurrency.LockableOperation;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.segments.SegmentManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WriteAheadLog {

  private final int batchSize;
  private final List<LogEntry> batch;
  private final SegmentManager segmentManager;
  private final LockableOperation appendLock;

  public WriteAheadLog(int batchSize, String logPath) throws IOException {
    this.batchSize = batchSize;
    this.batch = new ArrayList<>();
    this.segmentManager = new SegmentManager(logPath);
    this.appendLock = new LockableOperation();
  }

  public void append(LogEntry entry) throws IOException {
    appendLock.executeWithWriteLock(() -> {
      batch.add(entry);
      if (batch.size() == batchSize) {
        segmentManager.writeBatch(batch);
        batch.clear();
      }
      return null;
    });
  }

  public List<LogEntry> read() {
    try {
      return appendLock.executeWithReadLock(() ->
              Collections.unmodifiableList(batch)
      );
    } catch(IOException ex) {
        throw new RuntimeException(ex);
      }
  }

  public List<LogEntry> readAll() throws IOException {
    return appendLock.executeWithReadLock(() -> {
      List<LogEntry> entries = new ArrayList<>();
      entries.addAll(segmentManager.readAllSegments());
      entries.addAll(batch);
      return Collections.unmodifiableList(entries);
    });
  }

  public List<LogEntry> readFromDisk() throws IOException {
    return appendLock.executeWithReadLock(segmentManager::readAllSegments
    );
  }

  public List<LogEntry> readBuffer() {
    try {
      return appendLock.executeWithReadLock(() -> Collections.unmodifiableList(batch));
    } catch (IOException e) {
      throw new  RuntimeException(e);
    }
  }

  public List<LogEntry> readAllAfterTimestamp(long timestamp) throws IOException {
    return appendLock.executeWithReadLock(() ->
            segmentManager.readAllAfterTimestamp(timestamp)
    );
  }

  public void truncateBeforeTimestamp(long timestamp) throws IOException {
    appendLock.executeWithWriteLock(() -> {
      segmentManager.truncateBeforeTimestamp(timestamp);
      return null;
    });
  }

  public void close() throws IOException {
    appendLock.executeWithWriteLock(() -> {
      if (!batch.isEmpty()) {
        segmentManager.writeBatch(batch);
        batch.clear();
      }
      segmentManager.close();
      return null;
    });
  }
}
