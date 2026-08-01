package io.writeahead.log.segments;

import io.writeahead.log.metrics.SimpleWalMetrics;
import io.writeahead.log.models.AppendResult;
import io.writeahead.log.models.CloseResult;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.TruncateResult;
import java.io.IOException;
import java.util.List;

public interface SegmentStore {
  AppendResult append(LogEntry entry) throws IOException;

  AppendResult writeBatch() throws IOException;

  List<LogEntry> readAllSegments() throws IOException;

  List<LogEntry> readAllAfterTimestamp(long timestamp) throws IOException;

  TruncateResult truncateBeforeTimestamp(long timestamp) throws IOException;

  SimpleWalMetrics getMetrics();

  CloseResult close() throws IOException;
}
