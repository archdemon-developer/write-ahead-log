package io.writeahead.log.segments.operators;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.results.BatchFlushResult;
import io.writeahead.log.models.states.BatchState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BatchBuffer")
class BatchBufferTest {

  @Nested
  @DisplayName("Initialization")
  class InitializationTests {

    @Test
    @DisplayName("creates empty buffer")
    void createsEmptyBuffer() {
      BatchBuffer buffer = new BatchBuffer();

      assertTrue(buffer.isEmpty());
      assertEquals(0, buffer.size());
    }

    @Test
    @DisplayName("initial batch state is empty")
    void initialBatchStateIsEmpty() {
      BatchBuffer buffer = new BatchBuffer();

      BatchState state = buffer.getBatchState();

      assertTrue(state.isEmpty());
      assertEquals(0, state.entriesPendingInBatch());
      assertEquals(0, state.totalBytesInBatch());
    }
  }

  @Nested
  @DisplayName("Appending Entries")
  class AppendingEntriesTests {

    BatchBuffer buffer;

    @BeforeEach
    void setUp() {
      buffer = new BatchBuffer();
    }

    @Test
    @DisplayName("appends single entry")
    void appendsSingleEntry() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);

      buffer.append(entryOne);

      assertEquals(1, buffer.size());
      assertFalse(buffer.isEmpty());
    }

    @Test
    @DisplayName("appends multiple entries in order")
    void appendsMultipleEntriesInOrder() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(1100L, 128);
      LogEntry entryThree = OperatorsTestUtils.createLogEntry(1200L, 256);

      buffer.append(entryOne);
      buffer.append(entryTwo);
      buffer.append(entryThree);

      assertEquals(3, buffer.size());
    }

    @Test
    @DisplayName("tracks total bytes correctly")
    void tracksTotalBytesCorrectly() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 100);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(1100L, 200);

      buffer.append(entryOne);
      assertEquals(100, buffer.getBatchState().totalBytesInBatch());

      buffer.append(entryTwo);
      assertEquals(300, buffer.getBatchState().totalBytesInBatch());
    }

    @Test
    @DisplayName("tracks oldest timestamp correctly")
    void tracksOldestTimestampCorrectly() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(5000L, 50);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(3000L, 50);
      LogEntry entryThree = OperatorsTestUtils.createLogEntry(4000L, 50);

      buffer.append(entryOne);
      buffer.append(entryTwo);
      buffer.append(entryThree);

      assertEquals(3000L, buffer.getBatchState().oldestEntryTimestamp());
    }

    @Test
    @DisplayName("tracks newest timestamp correctly")
    void tracksNewestTimestampCorrectly() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(5000L, 50);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(3000L, 50);
      LogEntry entryThree = OperatorsTestUtils.createLogEntry(8000L, 50);

      buffer.append(entryOne);
      buffer.append(entryTwo);
      buffer.append(entryThree);

      assertEquals(8000L, buffer.getBatchState().newestEntryTimestamp());
    }
  }

  @Nested
  @DisplayName("Flushing Batch")
  class FlushingBatchTests {

    BatchBuffer buffer;

    @BeforeEach
    void setUp() {
      buffer = new BatchBuffer();
    }

    @Test
    @DisplayName("writeBatch returns copy of entries")
    void writeBatchReturnsCopyOfEntries() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(1100L, 128);

      buffer.append(entryOne);
      buffer.append(entryTwo);

      BatchFlushResult result = buffer.writeBatch();

      assertEquals(2, result.entries().size());
      assertEquals(entryOne, result.entries().get(0));
      assertEquals(entryTwo, result.entries().get(1));
    }

    @Test
    @DisplayName("writeBatch returns empty batch state after flush")
    void writeBatchReturnsEmptyBatchStateAfterFlush() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);
      buffer.append(entryOne);

      BatchFlushResult result = buffer.writeBatch();

      assertTrue(result.newBatchState().isEmpty());
      assertEquals(0, result.newBatchState().entriesPendingInBatch());
    }

    @Test
    @DisplayName("writeBatch clears the buffer")
    void writeBatchClearsTheBuffer() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);
      buffer.append(entryOne);

      buffer.writeBatch();

      assertTrue(buffer.isEmpty());
      assertEquals(0, buffer.size());
    }

    @Test
    @DisplayName("writeBatch from empty buffer returns empty result")
    void writeBatchFromEmptyBufferReturnsEmptyResult() {
      BatchFlushResult result = buffer.writeBatch();

      assertTrue(result.entries().isEmpty());
      assertTrue(result.newBatchState().isEmpty());
    }

    @Test
    @DisplayName("multiple sequential flushes work correctly")
    void multipleSequentialFlushesWorkCorrectly() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);
      buffer.append(entryOne);

      BatchFlushResult resultOne = buffer.writeBatch();
      assertEquals(1, resultOne.entries().size());
      assertTrue(buffer.isEmpty());

      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(2000L, 128);
      buffer.append(entryTwo);

      BatchFlushResult resultTwo = buffer.writeBatch();
      assertEquals(1, resultTwo.entries().size());
      assertTrue(buffer.isEmpty());
    }
  }

  @Nested
  @DisplayName("Clearing Buffer")
  class ClearingBufferTests {

    BatchBuffer buffer;

    @BeforeEach
    void setUp() {
      buffer = new BatchBuffer();
    }

    @Test
    @DisplayName("clear empties entries")
    void clearEmptiesEntries() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(1100L, 128);

      buffer.append(entryOne);
      buffer.append(entryTwo);

      buffer.clear();

      assertTrue(buffer.isEmpty());
      assertEquals(0, buffer.size());
    }

    @Test
    @DisplayName("clear resets totalBytes")
    void clearResetsTotalBytes() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 100);
      buffer.append(entryOne);

      buffer.clear();

      assertEquals(0, buffer.getBatchState().totalBytesInBatch());
    }

    @Test
    @DisplayName("clear resets timestamps to initial state")
    void clearResetsTimestampsToInitialState() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(5000L, 50);
      buffer.append(entryOne);

      buffer.clear();

      BatchState state = buffer.getBatchState();
      assertEquals(Long.MIN_VALUE, state.oldestEntryTimestamp());
      assertEquals(Long.MAX_VALUE, state.newestEntryTimestamp());
    }

    @Test
    @DisplayName("clear allows buffer reuse")
    void clearAllowsBufferReuse() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);
      buffer.append(entryOne);
      buffer.clear();

      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(2000L, 128);
      buffer.append(entryTwo);

      assertEquals(1, buffer.size());
      assertEquals(128, buffer.getBatchState().totalBytesInBatch());
    }
  }

  @Nested
  @DisplayName("Capacity Checking")
  class CapacityCheckingTests {

    BatchBuffer buffer;

    @BeforeEach
    void setUp() {
      buffer = new BatchBuffer();
    }

    @Test
    @DisplayName("canAccept returns true for small entry")
    void canAcceptReturnsTrueForSmallEntry() {
      LogEntry smallEntry = OperatorsTestUtils.createLogEntry(1000L, 100);

      assertTrue(buffer.canAccept(smallEntry));
    }

    @Test
    @DisplayName("canAccept returns false when adding would exceed capacity")
    void canAcceptReturnsFalseWhenAddingWouldExceedCapacity() {
      long firstSize = 60 * 1024 * 1024;
      long secondSize = 50 * 1024 * 1024;
      LogEntry firstLarge = OperatorsTestUtils.createLogEntry(1000L, (int) firstSize);
      LogEntry secondLarge = OperatorsTestUtils.createLogEntry(2000L, (int) secondSize);

      buffer.append(firstLarge);
      assertFalse(buffer.canAccept(secondLarge));
    }

    @Test
    @DisplayName("canAccept returns true when entry fits exactly at limit")
    void canAcceptReturnsTrueWhenEntryFitsExactlyAtLimit() {
      long maxCapacity = 100L * 1024 * 1024;
      long fillSize = 60L * 1024 * 1024;
      long remainingSize = maxCapacity - fillSize;

      LogEntry firstEntry = OperatorsTestUtils.createLogEntry(1000L, (int) fillSize);
      LogEntry secondEntry = OperatorsTestUtils.createLogEntry(2000L, (int) remainingSize);

      buffer.append(firstEntry);
      assertTrue(buffer.canAccept(secondEntry));
    }

    @Test
    @DisplayName("canAccept with empty buffer")
    void canAcceptWithEmptyBuffer() {
      LogEntry entry = OperatorsTestUtils.createLogEntry(1000L, 64);

      assertTrue(buffer.canAccept(entry));
    }
  }

  @Nested
  @DisplayName("Batch State Queries")
  class BatchStateQueriesTests {

    BatchBuffer buffer;

    @BeforeEach
    void setUp() {
      buffer = new BatchBuffer();
    }

    @Test
    @DisplayName("getBatchState returns correct state for non-empty buffer")
    void getBatchStateReturnsCorrectStateForNonEmptyBuffer() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(1100L, 128);

      buffer.append(entryOne);
      buffer.append(entryTwo);

      BatchState state = buffer.getBatchState();

      assertFalse(state.isEmpty());
      assertEquals(2, state.entriesPendingInBatch());
      assertEquals(192, state.totalBytesInBatch());
      assertEquals(1000L, state.oldestEntryTimestamp());
      assertEquals(1100L, state.newestEntryTimestamp());
    }

    @Test
    @DisplayName("getBatchState consistent after repeated calls")
    void getBatchStateConsistentAfterRepeatedCalls() {
      LogEntry entry = OperatorsTestUtils.createLogEntry(1000L, 64);
      buffer.append(entry);

      BatchState stateOne = buffer.getBatchState();
      BatchState stateTwo = buffer.getBatchState();

      assertEquals(stateOne.entriesPendingInBatch(), stateTwo.entriesPendingInBatch());
      assertEquals(stateOne.totalBytesInBatch(), stateTwo.totalBytesInBatch());
      assertEquals(stateOne.oldestEntryTimestamp(), stateTwo.oldestEntryTimestamp());
    }

    @Test
    @DisplayName("getBatchState reflects single entry correctly")
    void getBatchStateReflectsSingleEntryCorrectly() {
      LogEntry entry = OperatorsTestUtils.createLogEntry(5000L, 256);

      buffer.append(entry);

      BatchState state = buffer.getBatchState();

      assertEquals(1, state.entriesPendingInBatch());
      assertEquals(256, state.totalBytesInBatch());
      assertEquals(5000L, state.oldestEntryTimestamp());
      assertEquals(5000L, state.newestEntryTimestamp());
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    BatchBuffer buffer;

    @BeforeEach
    void setUp() {
      buffer = new BatchBuffer();
    }

    @Test
    @DisplayName("handles same timestamp for multiple entries")
    void handlesSameTimestampForMultipleEntries() {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(1000L, 128);

      buffer.append(entryOne);
      buffer.append(entryTwo);

      BatchState state = buffer.getBatchState();
      assertEquals(1000L, state.oldestEntryTimestamp());
      assertEquals(1000L, state.newestEntryTimestamp());
    }

    @Test
    @DisplayName("handles large number of small entries")
    void handlesLargeNumberOfSmallEntries() {
      for (int i = 0; i < 1000; i++) {
        LogEntry entry = OperatorsTestUtils.createLogEntry(1000L + i, 10);
        buffer.append(entry);
      }

      assertEquals(1000, buffer.size());
      assertEquals(10000, buffer.getBatchState().totalBytesInBatch());
    }

    @Test
    @DisplayName("writeBatch returns independent copy")
    void writeBatchReturnsIndependentCopy() {
      LogEntry entry = OperatorsTestUtils.createLogEntry(1000L, 64);
      buffer.append(entry);

      BatchFlushResult result = buffer.writeBatch();

      buffer.append(OperatorsTestUtils.createLogEntry(2000L, 128));

      assertEquals(1, result.entries().size());
      assertEquals(1, buffer.size());
    }
  }
}
