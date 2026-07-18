package io.writeahead.log;

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

  public WriteAheadLog(int batchSize, String logPath) throws IOException {
    this.batchSize = batchSize;
    this.batch = new ArrayList<>();
    this.segmentManager = new SegmentManager(logPath);
  }

  public void append(LogEntry entry) throws IOException {
    batch.add(entry);
    if (batch.size() == batchSize) {
      segmentManager.writeBatch(batch);
      batch.clear();
    }
  }

  public List<LogEntry> read() {
    return Collections.unmodifiableList(batch);
  }

  public List<LogEntry> readAll() throws IOException {
    List<LogEntry> entries = new ArrayList<>();
    entries.addAll(segmentManager.readAllSegments());
    entries.addAll(batch);
    return Collections.unmodifiableList(entries);
  }

  public List<LogEntry> readFromDisk() throws IOException {
    return Collections.unmodifiableList(segmentManager.readAllSegments());
  }

  public List<LogEntry> readBuffer() {
    return Collections.unmodifiableList(batch);
  }

  public List<LogEntry> readAllAfterTimestamp(long timestamp) throws IOException {
    return Collections.unmodifiableList(segmentManager.readAllAfterTimestamp(timestamp));
  }

  public void truncateBeforeTimestamp(long timestamp) throws IOException {
    segmentManager.truncateBeforeTimestamp(timestamp);
  }

  public void close() throws IOException {
    if (!batch.isEmpty()) {
      segmentManager.writeBatch(batch);
      batch.clear();
    }
    segmentManager.close();
  }
}
