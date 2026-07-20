package io.writeahead.log.storage;

import io.writeahead.log.metrics.SimpleWalMetrics;
import io.writeahead.log.models.LogEntry;
import java.io.IOException;
import java.util.List;

public interface SegmentStore {
  void writeBatch(List<LogEntry> batch) throws IOException;

  List<LogEntry> readAllSegments() throws IOException;

  List<LogEntry> readAllAfterTimestamp(long timestamp) throws IOException;

  void truncateBeforeTimestamp(long timestamp) throws IOException;

  SimpleWalMetrics getMetrics();

  void close() throws IOException;
}
