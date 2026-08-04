package io.writeahead.log.models;

import java.util.List;

public record BatchFlushResult(List<LogEntry> entries, BatchState newBatchState) {
  public BatchFlushResult {
    if (entries == null) {
      throw new IllegalArgumentException("entries cannot be null");
    }

    if (newBatchState == null) {
      throw new IllegalArgumentException("newBatchState cannot be null");
    }

    if (!newBatchState.isEmpty()) {
      throw new IllegalArgumentException(
          "After flush, batch must be empty. Got "
              + newBatchState.entriesPendingInBatch()
              + " entries");
    }
  }
}
