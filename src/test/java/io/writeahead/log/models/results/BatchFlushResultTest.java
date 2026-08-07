package io.writeahead.log.models.results;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.states.BatchState;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BatchFlushResult Tests — 100% Validation Coverage")
class BatchFlushResultTest {

  @Nested
  @DisplayName("Compact Constructor Validation")
  class ConstructorValidation {

    @Test
    void rejectsEntriesNull() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new BatchFlushResult(null, BatchState.emptyBatch()));
      assertTrue(ex.getMessage().contains("entries cannot be null"));
    }

    @Test
    void rejectsBatchStateNull() {
      LogEntry entry = new LogEntry(100, new byte[100], 1000L);
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new BatchFlushResult(List.of(entry), null));
      assertTrue(ex.getMessage().contains("newBatchState cannot be null"));
    }

    @Test
    void rejectsBatchStateNotEmpty() {
      LogEntry entry = new LogEntry(100, new byte[100], 1000L);
      BatchState nonEmpty = BatchState.withPendingEntries(1, 100L, 100L, 200L);
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new BatchFlushResult(List.of(entry), nonEmpty));
      assertTrue(ex.getMessage().contains("batch must be empty"));
    }

    @Test
    void acceptsValidBatchFlushResult() {
      LogEntry entry = new LogEntry(100, new byte[100], 1000L);
      BatchFlushResult result = new BatchFlushResult(List.of(entry), BatchState.emptyBatch());
      assertEquals(1, result.entries().size());
      assertTrue(result.newBatchState().isEmpty());
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    void handlesEmptyEntryList() {
      BatchFlushResult result = new BatchFlushResult(List.of(), BatchState.emptyBatch());
      assertTrue(result.entries().isEmpty());
    }

    @Test
    void handlesLargeEntryList() {
      List<LogEntry> entries = new java.util.ArrayList<>();
      for (int i = 0; i < 1000; i++) {
        entries.add(new LogEntry(100, new byte[100], 1000L + i));
      }
      BatchFlushResult result = new BatchFlushResult(entries, BatchState.emptyBatch());
      assertEquals(1000, result.entries().size());
    }

    @Test
    void handlesEntryWithLargePayload() {
      LogEntry largeEntry = new LogEntry(1000000, new byte[1000000], 1000L);
      BatchFlushResult result = new BatchFlushResult(List.of(largeEntry), BatchState.emptyBatch());
      assertEquals(1000000, result.entries().getFirst().size());
    }
  }
}
