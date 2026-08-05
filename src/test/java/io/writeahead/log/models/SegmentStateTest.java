package io.writeahead.log.models;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.states.SegmentState;
import org.junit.jupiter.api.Test;

class SegmentStateTest {

  private static final long maxSegmentSize = 1048576; // 1 MB

  @Test
  void emptyOpenSegment_createsCorrectState() {
    SegmentState state = SegmentState.emptyOpenSegment(1, 1000L);

    assertEquals(1, state.segmentSequenceNumber());
    assertEquals(0, state.entryCount());
    assertEquals(48, state.totalByteCount());
    assertEquals(Long.MIN_VALUE, state.minTimestamp());
    assertEquals(Long.MAX_VALUE, state.maxTimestamp());
    assertEquals(1000L, state.createdAtTimestamp());
    assertFalse(state.isFinalized());
  }

  @Test
  void withEntries_createsCorrectState() {
    SegmentState state = SegmentState.withEntries(5, 100, 51200, 1000L, 5000L, 100L, false);

    assertEquals(5, state.segmentSequenceNumber());
    assertEquals(100, state.entryCount());
    assertEquals(51200, state.totalByteCount());
    assertEquals(1000L, state.minTimestamp());
    assertEquals(5000L, state.maxTimestamp());
    assertEquals(100L, state.createdAtTimestamp());
    assertFalse(state.isFinalized());
  }

  @Test
  void withEntries_finalizedSegment() {
    SegmentState state = SegmentState.withEntries(1, 50, 25600, 500L, 3000L, 100L, true);

    assertTrue(state.isFinalized());
    assertFalse(state.canAcceptMoreEntries());
  }

  @Test
  void withEntries_throwsOnNegativeSequenceNumber() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SegmentState.withEntries(-1, 100, 51200, 1000L, 5000L, 100L, false));
  }

  @Test
  void withEntries_throwsOnZeroSequenceNumber() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SegmentState.withEntries(0, 100, 51200, 1000L, 5000L, 100L, false));
  }

  @Test
  void withEntries_throwsOnNegativeEntryCount() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SegmentState.withEntries(1, -1, 51200, 1000L, 5000L, 100L, false));
  }

  @Test
  void withEntries_throwsOnInvalidByteCount_lessThanHeader() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SegmentState.withEntries(1, 100, 47, 1000L, 5000L, 100L, false));
  }

  @Test
  void withEntries_throwsOnInvalidTimestampRange() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SegmentState.withEntries(1, 100, 51200, 5000L, 1000L, 100L, false));
  }

  @Test
  void withEntries_throwsOnNegativeCreatedAt() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SegmentState.withEntries(1, 100, 51200, 1000L, 5000L, -1L, false));
  }

  @Test
  void withEntries_throwsOnFinalizeEmptySegment() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SegmentState.withEntries(1, 0, 51200, 1000L, 5000L, 100L, true));
  }

  @Test
  void withEntries_throwsOnFinalizeZeroEntryCount() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SegmentState.withEntries(1, 0, 48, Long.MAX_VALUE, Long.MIN_VALUE, 100L, true));
  }

  @Test
  void withEntries_allowsEqualTimestamps() {
    SegmentState state = SegmentState.withEntries(1, 100, 51200, 1000L, 1000L, 100L, false);
    assertEquals(1000L, state.minTimestamp());
    assertEquals(1000L, state.maxTimestamp());
  }

  @Test
  void estimatedFillPercent_emptySegment() {
    SegmentState state = SegmentState.emptyOpenSegment(1, 1000L);
    // 48 bytes / 1MB ≈ 0%
    int percent = state.estimatedFillPercent(maxSegmentSize);
    assertTrue(percent >= 0 && percent <= 1, "Expected ~0%, got " + percent);
  }

  @Test
  void estimatedFillPercent_quarter() {
    SegmentState state = SegmentState.withEntries(1, 250, 262144, 1000L, 5000L, 100L, false);
    // 262144 / 1048576 ≈ 25%
    int percent = state.estimatedFillPercent(maxSegmentSize);
    assertTrue(percent >= 20 && percent <= 30, "Expected ~25%, got " + percent);
  }

  @Test
  void estimatedFillPercent_half() {
    SegmentState state = SegmentState.withEntries(1, 500, 524288, 1000L, 5000L, 100L, false);
    // 524288 / 1048576 ≈ 50%
    int percent = state.estimatedFillPercent(maxSegmentSize);
    assertTrue(percent >= 45 && percent <= 55, "Expected ~50%, got " + percent);
  }

  @Test
  void estimatedFillPercent_full() {
    SegmentState state = SegmentState.withEntries(1, 1000, 1048576, 1000L, 5000L, 100L, false);
    // 1048576 / 1048576 = 100%
    int percent = state.estimatedFillPercent(maxSegmentSize);
    assertEquals(100, percent);
  }

  @Test
  void estimatedFillPercent_overfull() {
    SegmentState state = SegmentState.withEntries(1, 1000, 2000000, 1000L, 5000L, 100L, false);
    // 2000000 / 1048576 ≈ 190%, should cap at 100
    int percent = state.estimatedFillPercent(maxSegmentSize);
    assertEquals(100, percent);
  }

  @Test
  void estimatedFillPercent_invalidMaxSegmentSize_zero() {
    SegmentState state = SegmentState.withEntries(1, 100, 51200, 1000L, 5000L, 100L, false);
    assertEquals(0, state.estimatedFillPercent(0));
  }

  @Test
  void estimatedFillPercent_invalidMaxSegmentSize_negative() {
    SegmentState state = SegmentState.withEntries(1, 100, 51200, 1000L, 5000L, 100L, false);
    assertEquals(0, state.estimatedFillPercent(-1));
  }

  @Test
  void ageInMilliseconds_freshSegment() {
    SegmentState state = SegmentState.emptyOpenSegment(1, 5000L);
    long age = state.ageInMilliseconds(5000L);
    assertEquals(0, age);
  }

  @Test
  void ageInMilliseconds_oldSegment() {
    SegmentState state = SegmentState.emptyOpenSegment(1, 1000L);
    long age = state.ageInMilliseconds(11000L);
    assertEquals(10000, age);
  }

  @Test
  void ageInMilliseconds_clockMovedBackward() {
    SegmentState state = SegmentState.emptyOpenSegment(1, 5000L);
    long age = state.ageInMilliseconds(3000L); // currentTime < createdAt
    assertEquals(0, age);
  }

  @Test
  void ageInMilliseconds_progression() {
    SegmentState state = SegmentState.emptyOpenSegment(1, 1000L);

    long[] ageMillisValues = {0, 100, 1000, 10000, 100000};
    for (long ageMillis : ageMillisValues) {
      long currentTime = 1000L + ageMillis;
      long calculatedAge = state.ageInMilliseconds(currentTime);
      assertTrue(
          calculatedAge >= (ageMillis - 1) && calculatedAge <= (ageMillis + 1),
          "Age " + ageMillis + ", calculated " + calculatedAge);
    }
  }

  @Test
  void averageBytesPerEntry_emptySegment() {
    SegmentState state = SegmentState.emptyOpenSegment(1, 1000L);
    assertEquals(0, state.averageBytesPerEntry());
  }

  @Test
  void averageBytesPerEntry_singleEntry() {
    SegmentState state = SegmentState.withEntries(1, 1, 1024, 100L, 500L, 100L, false);
    assertEquals(1024, state.averageBytesPerEntry());
  }

  @Test
  void averageBytesPerEntry_multipleEntries() {
    SegmentState state = SegmentState.withEntries(1, 100, 102400, 1000L, 5000L, 100L, false);
    // 102400 / 100 = 1024
    assertEquals(1024, state.averageBytesPerEntry());
  }

  @Test
  void averageBytesPerEntry_manySmallEntries() {
    SegmentState state = SegmentState.withEntries(1, 1000, 51200, 1000L, 5000L, 100L, false);
    // 51200 / 1000 = 51
    assertEquals(51, state.averageBytesPerEntry());
  }

  @Test
  void canAcceptMoreEntries_openSegment() {
    SegmentState state = SegmentState.emptyOpenSegment(1, 1000L);
    assertTrue(state.canAcceptMoreEntries());
  }

  @Test
  void canAcceptMoreEntries_openSegmentWithData() {
    SegmentState state = SegmentState.withEntries(1, 50, 25600, 500L, 3000L, 100L, false);
    assertTrue(state.canAcceptMoreEntries());
  }

  @Test
  void canAcceptMoreEntries_finalizedSegment() {
    SegmentState state = SegmentState.withEntries(1, 50, 25600, 500L, 3000L, 100L, true);
    assertFalse(state.canAcceptMoreEntries());
  }

  @Test
  void timestampRange_preserved() {
    long minTime = 1000L;
    long maxTime = 9999L;

    SegmentState state = SegmentState.withEntries(1, 100, 51200, minTime, maxTime, 100L, false);

    assertEquals(minTime, state.minTimestamp());
    assertEquals(maxTime, state.maxTimestamp());
  }

  @Test
  void immutability_recordIsUnmodifiable() {
    SegmentState state = SegmentState.withEntries(1, 100, 51200, 1000L, 5000L, 100L, false);

    assertEquals(1, state.segmentSequenceNumber());
    assertEquals(100, state.entryCount());
    // Records have no setters (compile-time safety)
  }

  @Test
  void compactConstructor_enforces_nonPositiveSequence() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SegmentState(0, 100, 51200, 1000L, 5000L, 100L, false));

    assertThrows(
        IllegalArgumentException.class,
        () -> new SegmentState(-1, 100, 51200, 1000L, 5000L, 100L, false));
  }

  @Test
  void compactConstructor_enforces_negativeEntryCount() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SegmentState(1, -1, 51200, 1000L, 5000L, 100L, false));
  }

  @Test
  void compactConstructor_enforces_minimumByteCount() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SegmentState(1, 100, 47, 1000L, 5000L, 100L, false));
  }

  @Test
  void compactConstructor_enforces_timestampRange() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SegmentState(1, 100, 51200, 5000L, 1000L, 100L, false));
  }

  @Test
  void compactConstructor_enforces_negativeCreatedAt() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SegmentState(1, 100, 51200, 1000L, 5000L, -1L, false));
  }

  @Test
  void compactConstructor_enforces_finalization_noEntries() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SegmentState(1, 0, 51200, 1000L, 5000L, 100L, true));
  }

  @Test
  void compactConstructor_enforces_finalization_requiresEntries() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SegmentState(1, 0, 48, Long.MAX_VALUE, Long.MIN_VALUE, 100L, true));
  }

  @Test
  void progressiveSegmentFill() {
    long[] entryCounts = {1, 10, 50, 100, 500, 1000};
    long bytesPerEntry = 1024;

    for (long entryCount : entryCounts) {
      SegmentState state =
          SegmentState.withEntries(
              1, entryCount, entryCount * bytesPerEntry, 100L, 5000L, 100L, false);

      assertEquals(bytesPerEntry, state.averageBytesPerEntry());
      assertTrue(state.canAcceptMoreEntries());
    }
  }

  @Test
  void sequenceNumbersProgression() {
    long[] sequenceNumbers = {1, 5, 10, 100, 1000};

    for (long seq : sequenceNumbers) {
      SegmentState state = SegmentState.emptyOpenSegment(seq, 1000L);
      assertEquals(seq, state.segmentSequenceNumber());
    }
  }

  @Test
  void fillProgression_incremental() {
    int[] fillPercentExpected = {0, 10, 25, 50, 75, 90, 100};
    long[] byteCounts = {48, 104857, 262144, 524288, 786432, 943718, 1048576};

    for (int i = 0; i < fillPercentExpected.length; i++) {
      SegmentState state =
          SegmentState.withEntries(1, 100, byteCounts[i], 1000L, 5000L, 100L, false);

      int percent = state.estimatedFillPercent(maxSegmentSize);
      int expected = fillPercentExpected[i];

      assertTrue(
          percent >= (expected - 5) && percent <= (expected + 5),
          "ByteCount " + byteCounts[i] + ": expected ~" + expected + "%, got " + percent);
    }
  }

  @Test
  void emptyVsFull_stateComparison() {
    SegmentState empty = SegmentState.emptyOpenSegment(1, 1000L);
    SegmentState full = SegmentState.withEntries(1, 1000, 1048576, 1000L, 5000L, 100L, false);

    assertTrue(empty.entryCount() < full.entryCount());
    assertTrue(empty.totalByteCount() < full.totalByteCount());
    assertTrue(empty.canAcceptMoreEntries());
    assertTrue(full.canAcceptMoreEntries());
  }

  @Test
  void openVsFinalized_stateComparison() {
    SegmentState open = SegmentState.withEntries(1, 100, 51200, 1000L, 5000L, 100L, false);
    SegmentState finalized = SegmentState.withEntries(1, 100, 51200, 1000L, 5000L, 100L, true);

    assertTrue(open.canAcceptMoreEntries());
    assertFalse(finalized.canAcceptMoreEntries());
    assertFalse(open.isFinalized());
    assertTrue(finalized.isFinalized());
  }

  @Test
  void directConstructor_via_factory_emptyOpenSegment() {
    SegmentState state = SegmentState.emptyOpenSegment(1, 1000L);

    assertFalse(state.isFinalized());
    assertEquals(0, state.entryCount());
    assertEquals(48, state.totalByteCount());
  }

  @Test
  void directConstructor_via_factory_withEntries() {
    SegmentState state = SegmentState.withEntries(5, 100, 51200, 1000L, 5000L, 100L, false);

    assertFalse(state.isFinalized());
    assertEquals(5, state.segmentSequenceNumber());
    assertEquals(100, state.entryCount());
    assertEquals(51200, state.totalByteCount());
  }

  @Test
  void largeSegmentSize() {
    SegmentState state = SegmentState.withEntries(1, 1000, 1000000, 1000L, 5000L, 100L, false);
    long average = state.averageBytesPerEntry();
    assertEquals(1000, average);
  }

  @Test
  void estimatedFillPercent_varyingSegmentSizes() {
    SegmentState state = SegmentState.withEntries(1, 100, 262144, 1000L, 5000L, 100L, false);

    // Same state, different max sizes
    int percent1 = state.estimatedFillPercent(1048576); // 25% of 1MB
    assertTrue(percent1 >= 20 && percent1 <= 30);

    int percent2 = state.estimatedFillPercent(262144); // 100% of 256KB
    assertEquals(100, percent2);

    int percent3 = state.estimatedFillPercent(524288); // 50% of 512KB
    assertTrue(percent3 >= 45 && percent3 <= 55);
  }
}
