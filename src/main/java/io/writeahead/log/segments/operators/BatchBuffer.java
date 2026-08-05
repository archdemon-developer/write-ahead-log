package io.writeahead.log.segments.operators;

import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.results.BatchFlushResult;
import io.writeahead.log.models.states.BatchState;
import java.util.ArrayList;
import java.util.List;

public class BatchBuffer {

  private final List<LogEntry> entries;
  private long totalBytesInBatch;
  private long oldestTimestamp;
  private long newestTimestamp;

  private static final long MAX_BUFFER_BYTES = 100 * 1024 * 1024;

  public BatchBuffer() {
    entries = new ArrayList<>();
    totalBytesInBatch = 0;
    oldestTimestamp = Long.MAX_VALUE;
    newestTimestamp = Long.MIN_VALUE;
  }

  public void append(LogEntry entry) {
    entries.add(entry);
    totalBytesInBatch += entry.size();

    if (entry.timestamp() < oldestTimestamp) {
      oldestTimestamp = entry.timestamp();
    }
    if (entry.timestamp() > newestTimestamp) {
      newestTimestamp = entry.timestamp();
    }
  }

  public BatchFlushResult writeBatch() {
    List<LogEntry> entriesToReturn = new ArrayList<>(entries);
    clear();
    BatchState newState = getBatchState();
    return new BatchFlushResult(entriesToReturn, newState);
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  public int size() {
    return entries.size();
  }

  public void clear() {
    entries.clear();
    totalBytesInBatch = 0;
    oldestTimestamp = Long.MAX_VALUE;
    newestTimestamp = Long.MIN_VALUE;
  }

  public BatchState getBatchState() {
    if (isEmpty()) {
      return BatchState.emptyBatch();
    }
    return BatchState.withPendingEntries(
        size(), totalBytesInBatch, oldestTimestamp, newestTimestamp);
  }

  public boolean canAccept(LogEntry entry) {
    return totalBytesInBatch + entry.size() <= MAX_BUFFER_BYTES;
  }
}
