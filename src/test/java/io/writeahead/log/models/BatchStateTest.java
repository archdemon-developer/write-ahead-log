package io.writeahead.log.models;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.states.BatchState;
import org.junit.jupiter.api.Test;

class BatchStateTest {

  private static final int batchSize = 10;

  @Test
  void emptyBatch_createsCorrectState() {
    BatchState state = BatchState.emptyBatch();

    assertEquals(0, state.entriesPendingInBatch());
    assertEquals(0, state.totalBytesInBatch());
    assertTrue(state.isEmpty());
    assertEquals(Long.MIN_VALUE, state.oldestEntryTimestamp());
    assertEquals(Long.MAX_VALUE, state.newestEntryTimestamp());
  }

  @Test
  void withPendingEntries_createsCorrectState() {
    BatchState state = BatchState.withPendingEntries(5, 10240, 1000L, 5000L);

    assertEquals(5, state.entriesPendingInBatch());
    assertEquals(10240, state.totalBytesInBatch());
    assertFalse(state.isEmpty());
    assertEquals(1000L, state.oldestEntryTimestamp());
    assertEquals(5000L, state.newestEntryTimestamp());
  }

  @Test
  void withPendingEntries_throwsOnZeroEntries() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BatchState.withPendingEntries(0, 10240, 1000L, 5000L));
  }

  @Test
  void withPendingEntries_throwsOnNegativeEntries() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BatchState.withPendingEntries(-1, 10240, 1000L, 5000L));
  }

  @Test
  void withPendingEntries_throwsOnZeroBytes() {
    assertThrows(
        IllegalArgumentException.class, () -> BatchState.withPendingEntries(5, 0, 1000L, 5000L));
  }

  @Test
  void withPendingEntries_throwsOnNegativeBytes() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BatchState.withPendingEntries(5, -1024, 1000L, 5000L));
  }

  @Test
  void withPendingEntries_throwsOnInvalidTimestampRange() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BatchState.withPendingEntries(5, 10240, 5000L, 1000L));
  }

  @Test
  void withPendingEntries_allowsEqualTimestamps() {
    // oldestEntryTimestamp == newestEntryTimestamp is valid (all entries same time)
    BatchState state = BatchState.withPendingEntries(5, 10240, 1000L, 1000L);
    assertEquals(1000L, state.oldestEntryTimestamp());
    assertEquals(1000L, state.newestEntryTimestamp());
  }

  @Test
  void estimatedFillPercent_emptyBatch_returnsZero() {
    BatchState state = BatchState.emptyBatch();
    assertEquals(0, state.estimatedFillPercent(10));
  }

  @Test
  void estimatedFillPercent_halfFull() {
    BatchState state = BatchState.withPendingEntries(5, 5120, 100L, 5000L);
    // 5 entries / 10 batchSize = 50%
    int percent = state.estimatedFillPercent(10);
    assertTrue(percent >= 45 && percent <= 55, "Expected ~50%, got " + percent);
  }

  @Test
  void estimatedFillPercent_almostFull() {
    BatchState state = BatchState.withPendingEntries(9, 9216, 100L, 9000L);
    // 9 entries / 10 batchSize = 90%
    int percent = state.estimatedFillPercent(10);
    assertTrue(percent >= 85 && percent <= 95, "Expected ~90%, got " + percent);
  }

  @Test
  void estimatedFillPercent_full() {
    BatchState state = BatchState.withPendingEntries(10, 10240, 100L, 10000L);
    // 10 entries / 10 batchSize = 100%
    int percent = state.estimatedFillPercent(10);
    assertEquals(100, percent);
  }

  @Test
  void estimatedFillPercent_overFull_capped() {
    BatchState state = BatchState.withPendingEntries(15, 15360, 100L, 15000L);
    // 15 entries / 10 batchSize = 150%, should cap at 100
    int percent = state.estimatedFillPercent(10);
    assertEquals(100, percent);
  }

  @Test
  void estimatedFillPercent_invalidBatchSize_zero() {
    BatchState state = BatchState.withPendingEntries(5, 5120, 100L, 5000L);
    assertEquals(0, state.estimatedFillPercent(0));
  }

  @Test
  void estimatedFillPercent_invalidBatchSize_negative() {
    BatchState state = BatchState.withPendingEntries(5, 5120, 100L, 5000L);
    assertEquals(0, state.estimatedFillPercent(-1));
  }

  @Test
  void averageBytesPerEntry_emptyBatch_returnsZero() {
    BatchState state = BatchState.emptyBatch();
    assertEquals(0, state.averageBytesPerEntry());
  }

  @Test
  void averageBytesPerEntry_singleEntry() {
    BatchState state = BatchState.withPendingEntries(1, 1024, 100L, 500L);
    assertEquals(1024, state.averageBytesPerEntry());
  }

  @Test
  void averageBytesPerEntry_multipleEntries() {
    BatchState state = BatchState.withPendingEntries(5, 10240, 100L, 5000L);
    // 10240 / 5 = 2048
    assertEquals(2048, state.averageBytesPerEntry());
  }

  @Test
  void averageBytesPerEntry_largeEntries() {
    BatchState state = BatchState.withPendingEntries(10, 102400, 100L, 10000L);
    // 102400 / 10 = 10240
    assertEquals(10240, state.averageBytesPerEntry());
  }

  @Test
  void wouldExceedCapacity_emptyBatch_doesNotExceed() {
    BatchState state = BatchState.emptyBatch();
    // First entry always fits
    assertFalse(state.wouldExceedCapacity(1024, 10));
  }

  @Test
  void wouldExceedCapacity_almostFull() {
    BatchState state = BatchState.withPendingEntries(9, 9216, 100L, 9000L);
    // 9 entries, batch size 10 → adding 1 more would be exactly at capacity (not exceeding)
    assertFalse(state.wouldExceedCapacity(1024, 10));
  }

  @Test
  void wouldExceedCapacity_atCapacity() {
    BatchState state = BatchState.withPendingEntries(10, 10240, 100L, 10000L);
    // 10 entries, batch size 10 → adding 1 more WOULD exceed
    assertTrue(state.wouldExceedCapacity(1024, 10));
  }

  @Test
  void wouldExceedCapacity_overCapacity() {
    BatchState state = BatchState.withPendingEntries(15, 15360, 100L, 15000L);
    // Already over capacity, still returns true
    assertTrue(state.wouldExceedCapacity(1024, 10));
  }

  @Test
  void wouldExceedCapacity_invalidBatchSize_zero() {
    BatchState state = BatchState.withPendingEntries(5, 5120, 100L, 5000L);
    assertTrue(state.wouldExceedCapacity(1024, 0));
  }

  @Test
  void wouldExceedCapacity_invalidBatchSize_negative() {
    BatchState state = BatchState.withPendingEntries(5, 5120, 100L, 5000L);
    assertTrue(state.wouldExceedCapacity(1024, -1));
  }

  @Test
  void timestampRange_preserved() {
    long minTime = 1000L;
    long maxTime = 9999L;

    BatchState state = BatchState.withPendingEntries(5, 5120, minTime, maxTime);

    assertEquals(minTime, state.oldestEntryTimestamp());
    assertEquals(maxTime, state.newestEntryTimestamp());
  }

  @Test
  void immutability_recordIsUnmodifiable() {
    BatchState state = BatchState.withPendingEntries(5, 5120, 100L, 5000L);

    // All accessors return expected values
    assertEquals(5, state.entriesPendingInBatch());
    assertEquals(5120, state.totalBytesInBatch());
    // Records have no setters (compile-time safety)
  }

  @Test
  void emptyVsFull_stateComparison() {
    BatchState emptyState = BatchState.emptyBatch();
    BatchState fullState = BatchState.withPendingEntries(10, 10240, 100L, 10000L);

    assertTrue(emptyState.isEmpty());
    assertFalse(fullState.isEmpty());

    assertTrue(emptyState.entriesPendingInBatch() < fullState.entriesPendingInBatch());
    assertTrue(emptyState.totalBytesInBatch() < fullState.totalBytesInBatch());
  }

  @Test
  void fillProgression_incremental() {
    int[] sizes = {1, 2, 3, 5, 7, 10};
    long bytesPerEntry = 1024;

    for (int size : sizes) {
      BatchState state = BatchState.withPendingEntries(size, size * bytesPerEntry, 100L, 5000L);

      int percent = state.estimatedFillPercent(batchSize);
      double expectedPercent = (double) size / batchSize * 100.0;

      assertTrue(
          percent >= (expectedPercent - 5) && percent <= (expectedPercent + 5),
          "Size " + size + ": expected ~" + expectedPercent + "%, got " + percent);
    }
  }

  @Test
  void averageBytesProgression() {
    int[][] testCases = {
      {1, 512, 512}, // 1 entry, 512 bytes = 512 avg
      {2, 1024, 512}, // 2 entries, 1024 bytes = 512 avg
      {5, 10240, 2048}, // 5 entries, 10240 bytes = 2048 avg
      {10, 102400, 10240} // 10 entries, 102400 bytes = 10240 avg
    };

    for (int[] testCase : testCases) {
      int entries = testCase[0];
      long bytes = testCase[1];
      long expectedAvg = testCase[2];

      BatchState state = BatchState.withPendingEntries(entries, bytes, 100L, 5000L);

      assertEquals(
          expectedAvg, state.averageBytesPerEntry(), "Entries=" + entries + ", bytes=" + bytes);
    }
  }

  @Test
  void singleEntry_batch() {
    BatchState state = BatchState.withPendingEntries(1, 2048, 1000L, 1000L);

    assertEquals(1, state.entriesPendingInBatch());
    assertEquals(2048, state.totalBytesInBatch());
    assertEquals(2048, state.averageBytesPerEntry());
    assertFalse(state.isEmpty());
    assertEquals(10, state.estimatedFillPercent(batchSize)); // 1/10 = 10%
  }

  @Test
  void largeEntry_batch() {
    // Single very large entry
    BatchState state = BatchState.withPendingEntries(1, 1000000, 100L, 500L);

    assertEquals(1, state.entriesPendingInBatch());
    assertEquals(1000000, state.totalBytesInBatch());
    assertEquals(1000000, state.averageBytesPerEntry());
    assertFalse(state.isEmpty());
  }

  @Test
  void manySmallEntries_batch() {
    // Many tiny entries
    BatchState state = BatchState.withPendingEntries(100, 10240, 100L, 10000L);

    assertEquals(100, state.entriesPendingInBatch());
    assertEquals(10240, state.totalBytesInBatch());
    assertEquals(102, state.averageBytesPerEntry()); // 10240 / 100 = 102
    assertFalse(state.isEmpty());
  }

  @Test
  void compactConstructor_enforces_isEmpty_consistency_empty() {
    // Compact constructor should reject isEmpty=true with non-zero values
    assertThrows(
        IllegalArgumentException.class,
        () -> new BatchState(5, 5120, 100L, 5000L, true)); // isEmpty=true but entries > 0

    assertThrows(
        IllegalArgumentException.class,
        () -> new BatchState(0, 5120, 100L, 5000L, true)); // isEmpty=true but bytes > 0
  }

  @Test
  void compactConstructor_enforces_isEmpty_consistency_full() {
    // Compact constructor should reject isEmpty=false with zero values
    assertThrows(
        IllegalArgumentException.class,
        () -> new BatchState(0, 5120, 100L, 5000L, false)); // isEmpty=false but entries = 0

    assertThrows(
        IllegalArgumentException.class,
        () -> new BatchState(5, 0, 100L, 5000L, false)); // isEmpty=false but bytes = 0
  }

  @Test
  void compactConstructor_enforces_negative_entries() {
    assertThrows(
        IllegalArgumentException.class, () -> new BatchState(-1, 5120, 100L, 5000L, false));
  }

  @Test
  void compactConstructor_enforces_negative_bytes() {
    assertThrows(IllegalArgumentException.class, () -> new BatchState(5, -1, 100L, 5000L, false));
  }

  @Test
  void compactConstructor_enforces_timestamp_range() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BatchState(5, 5120, 5000L, 1000L, false)); // oldestEntryTimestamp > newest
  }

  @Test
  void wouldExceedCapacity_boundaryCondition_exactCapacity() {
    // Exactly at capacity
    BatchState state = BatchState.withPendingEntries(10, 10240, 100L, 10000L);

    // Adding 1 more would exceed
    assertTrue(state.wouldExceedCapacity(1024, 10));

    // But with smaller batch size, it's different
    assertFalse(state.wouldExceedCapacity(1024, 11)); // Would not exceed if batch size is 11
  }

  @Test
  void estimatedFillPercent_varyingBatchSizes() {
    BatchState state = BatchState.withPendingEntries(5, 5120, 100L, 5000L);

    // 5 entries with different batch sizes
    assertEquals(50, state.estimatedFillPercent(10)); // 5/10 = 50%
    assertEquals(100, state.estimatedFillPercent(5)); // 5/5 = 100%
    int percent25 = state.estimatedFillPercent(20); // 5/20 = 25%
    assertTrue(percent25 >= 20 && percent25 <= 30);
  }

  @Test
  void directConstructor_via_factory_emptyBatch() {
    // Verify factory creates valid object via constructor
    BatchState state = BatchState.emptyBatch();

    assertTrue(state.isEmpty());
    assertEquals(0, state.entriesPendingInBatch());
    assertEquals(0, state.totalBytesInBatch());
  }

  @Test
  void directConstructor_via_factory_withPendingEntries() {
    // Verify factory creates valid object via constructor
    BatchState state = BatchState.withPendingEntries(5, 5120, 100L, 5000L);

    assertFalse(state.isEmpty());
    assertEquals(5, state.entriesPendingInBatch());
    assertEquals(5120, state.totalBytesInBatch());
  }
}
