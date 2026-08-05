package io.writeahead.log.models;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.results.BatchFlushResult;
import io.writeahead.log.models.states.BatchState;
import io.writeahead.log.segments.operators.BatchBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BatchBufferTest {

  private BatchBuffer batchBuffer;
  private static final int ENTRY_SIZE_100 = 100;
  private static final int ENTRY_SIZE_500 = 500;
  private static final long TIMESTAMP_1000 = 1000L;
  private static final long TIMESTAMP_2000 = 2000L;
  private static final long TIMESTAMP_3000 = 3000L;
  private static final long TIMESTAMP_5000 = 5000L;

  @BeforeEach
  void setUp() {
    batchBuffer = new BatchBuffer();
  }

  @Test
  void constructor_initializesEmptyBatch() {
    assertTrue(batchBuffer.isEmpty());
    assertEquals(0, batchBuffer.size());
    assertFalse(batchBuffer.getBatchState().isEmpty());
  }

  @Test
  void append_singleEntry_updatesState() {
    LogEntry entry = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000);

    batchBuffer.append(entry);

    assertEquals(1, batchBuffer.size());
    assertFalse(batchBuffer.isEmpty());

    BatchState state = batchBuffer.getBatchState();
    assertEquals(1, state.entriesPendingInBatch());
    assertEquals(ENTRY_SIZE_100, state.totalBytesInBatch());
    assertEquals(TIMESTAMP_1000, state.oldestEntryTimestamp());
    assertEquals(TIMESTAMP_1000, state.newestEntryTimestamp());
  }

  @Test
  void append_multipleEntries_updatesBatchState() {
    LogEntry entry1 = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000);
    LogEntry entry2 = new LogEntry(ENTRY_SIZE_500, new byte[ENTRY_SIZE_500], TIMESTAMP_2000);
    LogEntry entry3 = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_3000);

    batchBuffer.append(entry1);
    batchBuffer.append(entry2);
    batchBuffer.append(entry3);

    assertEquals(3, batchBuffer.size());

    BatchState state = batchBuffer.getBatchState();
    assertEquals(3, state.entriesPendingInBatch());
    assertEquals(ENTRY_SIZE_100 + ENTRY_SIZE_500 + ENTRY_SIZE_100, state.totalBytesInBatch());
    assertEquals(TIMESTAMP_1000, state.oldestEntryTimestamp());
    assertEquals(TIMESTAMP_3000, state.newestEntryTimestamp());
  }

  @Test
  void append_entriesOutOfOrder_tracksCorrectTimestamps() {
    LogEntry entry3 = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_3000);
    LogEntry entry1 = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000);
    LogEntry entry5 = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_5000);

    batchBuffer.append(entry3);
    batchBuffer.append(entry1);
    batchBuffer.append(entry5);

    BatchState state = batchBuffer.getBatchState();
    assertEquals(TIMESTAMP_1000, state.oldestEntryTimestamp());
    assertEquals(TIMESTAMP_5000, state.newestEntryTimestamp());
  }

  @Test
  void append_sameTimestamp_handlesCorrectly() {
    LogEntry entry1 = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_2000);
    LogEntry entry2 = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_2000);
    LogEntry entry3 = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_2000);

    batchBuffer.append(entry1);
    batchBuffer.append(entry2);
    batchBuffer.append(entry3);

    BatchState state = batchBuffer.getBatchState();
    assertEquals(TIMESTAMP_2000, state.oldestEntryTimestamp());
    assertEquals(TIMESTAMP_2000, state.newestEntryTimestamp());
  }

  @Test
  void writeBatch_flushesAndClears() {
    LogEntry entry1 = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000);
    LogEntry entry2 = new LogEntry(ENTRY_SIZE_500, new byte[ENTRY_SIZE_500], TIMESTAMP_2000);

    batchBuffer.append(entry1);
    batchBuffer.append(entry2);

    BatchFlushResult result = batchBuffer.writeBatch();

    // Check returned entries
    assertEquals(2, result.entries().size());
    assertEquals(ENTRY_SIZE_100, result.entries().get(0).size());
    assertEquals(ENTRY_SIZE_500, result.entries().get(1).size());

    // Check new batch state is empty
    assertTrue(result.newBatchState().isEmpty());
    assertEquals(0, result.newBatchState().entriesPendingInBatch());

    // Check buffer is cleared
    assertTrue(batchBuffer.isEmpty());
    assertEquals(0, batchBuffer.size());
  }

  @Test
  void writeBatch_returnsNewBatchState_afterFlush() {
    LogEntry entry = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000);
    batchBuffer.append(entry);

    BatchFlushResult result = batchBuffer.writeBatch();

    BatchState newState = result.newBatchState();
    assertTrue(newState.isEmpty());
    assertEquals(0, newState.entriesPendingInBatch());
    assertEquals(0, newState.totalBytesInBatch());
  }

  @Test
  void writeBatch_emptyBatch_returnsEmptyFlushResult() {
    BatchFlushResult result = batchBuffer.writeBatch();

    assertTrue(result.entries().isEmpty());
    assertTrue(result.newBatchState().isEmpty());
  }

  @Test
  void isEmpty_returnsTrueWhenEmpty() {
    assertTrue(batchBuffer.isEmpty());
  }

  @Test
  void isEmpty_returnsFalseAfterAppend() {
    LogEntry entry = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000);
    batchBuffer.append(entry);

    assertFalse(batchBuffer.isEmpty());
  }

  @Test
  void isEmpty_returnsTrueAfterClear() {
    LogEntry entry = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000);
    batchBuffer.append(entry);
    assertFalse(batchBuffer.isEmpty());

    batchBuffer.clear();

    assertTrue(batchBuffer.isEmpty());
  }

  @Test
  void size_returnsCorrectCount() {
    assertEquals(0, batchBuffer.size());

    batchBuffer.append(new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000));
    assertEquals(1, batchBuffer.size());

    batchBuffer.append(new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_2000));
    assertEquals(2, batchBuffer.size());

    batchBuffer.append(new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_3000));
    assertEquals(3, batchBuffer.size());
  }

  @Test
  void clear_resetsAllState() {
    LogEntry entry1 = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000);
    LogEntry entry2 = new LogEntry(ENTRY_SIZE_500, new byte[ENTRY_SIZE_500], TIMESTAMP_5000);

    batchBuffer.append(entry1);
    batchBuffer.append(entry2);

    batchBuffer.clear();

    assertTrue(batchBuffer.isEmpty());
    assertEquals(0, batchBuffer.size());

    BatchState state = batchBuffer.getBatchState();
    assertEquals(0, state.entriesPendingInBatch());
    assertEquals(0, state.totalBytesInBatch());
    assertEquals(Long.MAX_VALUE, state.oldestEntryTimestamp());
    assertEquals(Long.MIN_VALUE, state.newestEntryTimestamp());
  }

  @Test
  void getBatchState_emptyBatch_returnsSentinels() {
    BatchState state = batchBuffer.getBatchState();

    assertTrue(state.isEmpty());
    assertEquals(0, state.entriesPendingInBatch());
    assertEquals(0, state.totalBytesInBatch());
    assertEquals(Long.MAX_VALUE, state.oldestEntryTimestamp());
    assertEquals(Long.MIN_VALUE, state.newestEntryTimestamp());
  }

  @Test
  void getBatchState_withEntries_returnsCorrectState() {
    LogEntry entry1 = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000);
    LogEntry entry2 = new LogEntry(ENTRY_SIZE_500, new byte[ENTRY_SIZE_500], TIMESTAMP_3000);

    batchBuffer.append(entry1);
    batchBuffer.append(entry2);

    BatchState state = batchBuffer.getBatchState();

    assertFalse(state.isEmpty());
    assertEquals(2, state.entriesPendingInBatch());
    assertEquals(ENTRY_SIZE_100 + ENTRY_SIZE_500, state.totalBytesInBatch());
    assertEquals(TIMESTAMP_1000, state.oldestEntryTimestamp());
    assertEquals(TIMESTAMP_3000, state.newestEntryTimestamp());
  }

  @Test
  void writeBatch_sequentialFlushes() {
    // First batch
    batchBuffer.append(new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000));
    batchBuffer.append(new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_2000));

    BatchFlushResult result1 = batchBuffer.writeBatch();
    assertEquals(2, result1.entries().size());
    assertTrue(batchBuffer.isEmpty());

    // Second batch
    batchBuffer.append(new LogEntry(ENTRY_SIZE_500, new byte[ENTRY_SIZE_500], TIMESTAMP_3000));

    BatchFlushResult result2 = batchBuffer.writeBatch();
    assertEquals(1, result2.entries().size());
    assertEquals(ENTRY_SIZE_500, result2.entries().get(0).size());
    assertTrue(batchBuffer.isEmpty());
  }

  @Test
  void totalBytesInBatch_accumulatesCorrectly() {
    int[] sizes = {100, 200, 300, 150, 250};
    long totalExpected = 0;

    for (int i = 0; i < sizes.length; i++) {
      byte[] data = new byte[sizes[i]];
      LogEntry entry = new LogEntry(sizes[i], data, TIMESTAMP_1000 + i);
      batchBuffer.append(entry);
      totalExpected += sizes[i];

      BatchState state = batchBuffer.getBatchState();
      assertEquals(totalExpected, state.totalBytesInBatch());
    }
  }

  @Test
  void append_largeNumberOfEntries() {
    int numEntries = 1000;
    long expectedBytes = 0;

    for (int i = 0; i < numEntries; i++) {
      int size = 100 + (i % 500);
      byte[] data = new byte[size];
      LogEntry entry = new LogEntry(size, data, TIMESTAMP_1000 + i);
      batchBuffer.append(entry);
      expectedBytes += size;
    }

    assertEquals(numEntries, batchBuffer.size());

    BatchState state = batchBuffer.getBatchState();
    assertEquals(numEntries, state.entriesPendingInBatch());
    assertEquals(expectedBytes, state.totalBytesInBatch());
  }

  @Test
  void writeBatch_largeNumberOfEntries() {
    int numEntries = 500;
    for (int i = 0; i < numEntries; i++) {
      byte[] data = new byte[100];
      LogEntry entry = new LogEntry(100, data, TIMESTAMP_1000 + i);
      batchBuffer.append(entry);
    }

    BatchFlushResult result = batchBuffer.writeBatch();

    assertEquals(numEntries, result.entries().size());
    assertEquals(numEntries * 100, result.newBatchState().totalBytesInBatch() + numEntries * 100);
    assertTrue(batchBuffer.isEmpty());
  }

  @Test
  void batchFlushResult_invariant_newBatchStateIsEmpty() {
    batchBuffer.append(new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000));
    batchBuffer.append(new LogEntry(ENTRY_SIZE_500, new byte[ENTRY_SIZE_500], TIMESTAMP_2000));

    BatchFlushResult result = batchBuffer.writeBatch();

    assertTrue(result.newBatchState().isEmpty());
    assertEquals(0, result.newBatchState().entriesPendingInBatch());
  }

  @Test
  void batchFlushResult_entriesList_isCorrect() {
    LogEntry entry1 = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000);
    LogEntry entry2 = new LogEntry(ENTRY_SIZE_500, new byte[ENTRY_SIZE_500], TIMESTAMP_2000);

    batchBuffer.append(entry1);
    batchBuffer.append(entry2);

    BatchFlushResult result = batchBuffer.writeBatch();

    assertEquals(2, result.entries().size());
    assertEquals(TIMESTAMP_1000, result.entries().get(0).timestamp());
    assertEquals(TIMESTAMP_2000, result.entries().get(1).timestamp());
  }

  @Test
  void timestampTracking_progressiveUpdates() {
    batchBuffer.append(new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_3000));

    BatchState state1 = batchBuffer.getBatchState();
    assertEquals(TIMESTAMP_3000, state1.oldestEntryTimestamp());
    assertEquals(TIMESTAMP_3000, state1.newestEntryTimestamp());

    batchBuffer.append(new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000));

    BatchState state2 = batchBuffer.getBatchState();
    assertEquals(TIMESTAMP_1000, state2.oldestEntryTimestamp());
    assertEquals(TIMESTAMP_3000, state2.newestEntryTimestamp());

    batchBuffer.append(new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_5000));

    BatchState state3 = batchBuffer.getBatchState();
    assertEquals(TIMESTAMP_1000, state3.oldestEntryTimestamp());
    assertEquals(TIMESTAMP_5000, state3.newestEntryTimestamp());
  }

  @Test
  void clear_afterWriteBatch_resetsEverything() {
    batchBuffer.append(new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000));
    batchBuffer.writeBatch();
    batchBuffer.clear();

    assertTrue(batchBuffer.isEmpty());
    assertEquals(0, batchBuffer.size());

    BatchState state = batchBuffer.getBatchState();
    assertTrue(state.isEmpty());
  }

  @Test
  void multipleCycles_appendFlushClear() {
    for (int cycle = 0; cycle < 5; cycle++) {
      // Append
      batchBuffer.append(
          new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000 + cycle));
      assertFalse(batchBuffer.isEmpty());

      // Flush
      BatchFlushResult result = batchBuffer.writeBatch();
      assertEquals(1, result.entries().size());
      assertTrue(batchBuffer.isEmpty());

      // Verify empty state
      assertTrue(batchBuffer.getBatchState().isEmpty());
    }
  }

  @Test
  void batchFlushResult_throwsOnNullEntries() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BatchFlushResult(null, batchBuffer.getBatchState()));
  }

  @Test
  void batchFlushResult_throwsOnNullBatchState() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BatchFlushResult(new java.util.ArrayList<>(), null));
  }

  @Test
  void batchFlushResult_throwsOnNonEmptyBatchState() {
    LogEntry entry = new LogEntry(ENTRY_SIZE_100, new byte[ENTRY_SIZE_100], TIMESTAMP_1000);
    batchBuffer.append(entry);
    BatchState nonEmptyState = batchBuffer.getBatchState();

    assertThrows(
        IllegalArgumentException.class,
        () -> new BatchFlushResult(new java.util.ArrayList<>(), nonEmptyState));
  }
}
