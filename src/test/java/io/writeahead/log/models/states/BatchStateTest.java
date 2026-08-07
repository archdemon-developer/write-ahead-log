package io.writeahead.log.models.states;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BatchState Tests — 100% Validation Coverage")
class BatchStateTest {

  @Nested
  @DisplayName("Compact Constructor Validation — entriesPendingInBatch < 0")
  class ConstructorValidation_EntriesNegative {

    @Test
    @DisplayName("constructor rejects entriesPendingInBatch = -1")
    void rejectsEntriesNegativeOne() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new BatchState(-1L, 100L, 1000L, 2000L, false));
      assertTrue(ex.getMessage().contains("entriesPendingInBatch cannot be negative"));
    }

    @Test
    @DisplayName("constructor rejects entriesPendingInBatch = Long.MIN_VALUE")
    void rejectsEntriesMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new BatchState(Long.MIN_VALUE, 100L, 1000L, 2000L, false));
      assertTrue(ex.getMessage().contains("entriesPendingInBatch cannot be negative"));
    }

    @Test
    @DisplayName("constructor accepts entriesPendingInBatch = 0")
    void acceptsEntriesZero() {
      BatchState state = new BatchState(0L, 0L, Long.MIN_VALUE, Long.MAX_VALUE, true);
      assertEquals(0L, state.entriesPendingInBatch());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — totalBytesInBatch < 0")
  class ConstructorValidation_BytesNegative {

    @Test
    @DisplayName("constructor rejects totalBytesInBatch = -1")
    void rejectsBytesNegativeOne() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new BatchState(10L, -1L, 1000L, 2000L, false));
      assertTrue(ex.getMessage().contains("totalBytesInBatch cannot be negative"));
    }

    @Test
    @DisplayName("constructor rejects totalBytesInBatch = Long.MIN_VALUE")
    void rejectsBytesMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new BatchState(10L, Long.MIN_VALUE, 1000L, 2000L, false));
      assertTrue(ex.getMessage().contains("totalBytesInBatch cannot be negative"));
    }

    @Test
    @DisplayName("constructor accepts totalBytesInBatch = 0")
    void acceptsBytesZero() {
      BatchState state = new BatchState(0L, 0L, Long.MIN_VALUE, Long.MAX_VALUE, true);
      assertEquals(0L, state.totalBytesInBatch());
    }
  }

  @Nested
  @DisplayName(
      "Compact Constructor Validation — isEmpty logic (entries and bytes must both be 0 or both > 0)")
  class ConstructorValidation_IsEmptyLogic {

    @Test
    @DisplayName("constructor rejects isEmpty=true with entriesPendingInBatch > 0")
    void rejectsEmptyWithEntriesButNoBytes() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new BatchState(5L, 0L, 1000L, 2000L, true));
      assertTrue(ex.getMessage().contains("isEmpty = true requires entriesPendingInBatch = 0"));
    }

    @Test
    @DisplayName("constructor rejects isEmpty=true with totalBytesInBatch > 0")
    void rejectsEmptyWithBytesButNoEntries() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new BatchState(0L, 100L, 1000L, 2000L, true));
      assertTrue(ex.getMessage().contains("isEmpty = true requires entriesPendingInBatch = 0"));
    }

    @Test
    @DisplayName("constructor rejects isEmpty=false with entriesPendingInBatch = 0")
    void rejectsNotEmptyWithZeroEntries() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new BatchState(0L, 100L, 1000L, 2000L, false));
      assertTrue(ex.getMessage().contains("isEmpty = false requires entriesPendingInBatch > 0"));
    }

    @Test
    @DisplayName("constructor rejects isEmpty=false with totalBytesInBatch = 0")
    void rejectsNotEmptyWithZeroBytes() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new BatchState(5L, 0L, 1000L, 2000L, false));
      assertTrue(ex.getMessage().contains("isEmpty = false requires"));
    }

    @Test
    @DisplayName("constructor accepts isEmpty=true with entries=0 and bytes=0")
    void acceptsEmptyWithBothZero() {
      BatchState state = new BatchState(0L, 0L, Long.MIN_VALUE, Long.MAX_VALUE, true);
      assertTrue(state.isEmpty());
      assertEquals(0L, state.entriesPendingInBatch());
      assertEquals(0L, state.totalBytesInBatch());
    }

    @Test
    @DisplayName("constructor accepts isEmpty=false with entries > 0 and bytes > 0")
    void acceptsNotEmptyWithBothPositive() {
      BatchState state = new BatchState(5L, 100L, 1000L, 2000L, false);
      assertFalse(state.isEmpty());
      assertEquals(5L, state.entriesPendingInBatch());
      assertEquals(100L, state.totalBytesInBatch());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — oldestEntryTimestamp > newestEntryTimestamp")
  class ConstructorValidation_TimestampOrdering {

    @Test
    @DisplayName("constructor rejects oldestEntryTimestamp > newestEntryTimestamp")
    void rejectsOldestGreaterThanNewest() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new BatchState(5L, 100L, 2000L, 1000L, false));
      assertTrue(
          ex.getMessage().contains("oldestEntryTimestamp")
              && ex.getMessage().contains("cannot be > newestEntryTimestamp"));
    }

    @Test
    @DisplayName("constructor rejects oldestEntryTimestamp >> newestEntryTimestamp")
    void rejectsOldestMuchGreaterThanNewest() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new BatchState(5L, 100L, Long.MAX_VALUE, Long.MIN_VALUE, false));
      assertTrue(ex.getMessage().contains("oldestEntryTimestamp"));
    }

    @Test
    @DisplayName("constructor accepts oldestEntryTimestamp == newestEntryTimestamp")
    void acceptsEqualTimestamps() {
      BatchState state = new BatchState(5L, 100L, 1000L, 1000L, false);
      assertEquals(1000L, state.oldestEntryTimestamp());
      assertEquals(1000L, state.newestEntryTimestamp());
    }

    @Test
    @DisplayName("constructor accepts oldestEntryTimestamp < newestEntryTimestamp")
    void acceptsOldestLessThanNewest() {
      BatchState state = new BatchState(5L, 100L, 1000L, 2000L, false);
      assertEquals(1000L, state.oldestEntryTimestamp());
      assertEquals(2000L, state.newestEntryTimestamp());
    }
  }

  @Nested
  @DisplayName("Factory Method Tests — emptyBatch()")
  class FactoryMethod_EmptyBatch {

    @Test
    @DisplayName("emptyBatch() sets entriesPendingInBatch to 0")
    void setsEntriesZero() {
      BatchState state = BatchState.emptyBatch();
      assertEquals(0L, state.entriesPendingInBatch());
    }

    @Test
    @DisplayName("emptyBatch() sets totalBytesInBatch to 0")
    void setsBytesZero() {
      BatchState state = BatchState.emptyBatch();
      assertEquals(0L, state.totalBytesInBatch());
    }

    @Test
    @DisplayName("emptyBatch() sets oldestEntryTimestamp to Long.MIN_VALUE")
    void setsOldestTimestampToMin() {
      BatchState state = BatchState.emptyBatch();
      assertEquals(Long.MIN_VALUE, state.oldestEntryTimestamp());
    }

    @Test
    @DisplayName("emptyBatch() sets newestEntryTimestamp to Long.MAX_VALUE")
    void setsNewestTimestampToMax() {
      BatchState state = BatchState.emptyBatch();
      assertEquals(Long.MAX_VALUE, state.newestEntryTimestamp());
    }

    @Test
    @DisplayName("emptyBatch() sets isEmpty to true")
    void setsIsEmptyTrue() {
      BatchState state = BatchState.emptyBatch();
      assertTrue(state.isEmpty());
    }
  }

  @Nested
  @DisplayName("Factory Method Tests — withPendingEntries()")
  class FactoryMethod_WithPendingEntries {

    @Test
    @DisplayName("withPendingEntries() sets entriesPendingInBatch")
    void setsEntries() {
      BatchState state = BatchState.withPendingEntries(42, 1000L, 100L, 200L);
      assertEquals(42L, state.entriesPendingInBatch());
    }

    @Test
    @DisplayName("withPendingEntries() sets totalBytesInBatch")
    void setsBytes() {
      BatchState state = BatchState.withPendingEntries(5, 5000L, 100L, 200L);
      assertEquals(5000L, state.totalBytesInBatch());
    }

    @Test
    @DisplayName("withPendingEntries() sets oldestEntryTimestamp")
    void setsOldestTimestamp() {
      BatchState state = BatchState.withPendingEntries(5, 1000L, 111111L, 222222L);
      assertEquals(111111L, state.oldestEntryTimestamp());
    }

    @Test
    @DisplayName("withPendingEntries() sets newestEntryTimestamp")
    void setsNewestTimestamp() {
      BatchState state = BatchState.withPendingEntries(5, 1000L, 111111L, 222222L);
      assertEquals(222222L, state.newestEntryTimestamp());
    }

    @Test
    @DisplayName("withPendingEntries() sets isEmpty to false")
    void setsIsEmptyFalse() {
      BatchState state = BatchState.withPendingEntries(5, 1000L, 100L, 200L);
      assertFalse(state.isEmpty());
    }

    @Test
    @DisplayName("withPendingEntries() validates all parameters")
    void validatesAllParameters() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> BatchState.withPendingEntries(0, 1000L, 100L, 200L));
      assertTrue(ex.getMessage().contains("isEmpty"));
    }
  }

  @Nested
  @DisplayName("Helper Tests — estimatedFillPercent()")
  class EstimatedFillPercent {

    @Test
    @DisplayName("estimatedFillPercent() returns 0 for empty batch")
    void returns0ForEmpty() {
      BatchState state = BatchState.emptyBatch();
      int percent = state.estimatedFillPercent(100);
      assertEquals(0, percent);
    }

    @Test
    @DisplayName("estimatedFillPercent() calculates percentage correctly")
    void calculatesPercentageCorrectly() {
      BatchState state = BatchState.withPendingEntries(5, 1000L, 100L, 200L);
      int percent = state.estimatedFillPercent(10);
      assertEquals(50, percent);
    }

    @Test
    @DisplayName("estimatedFillPercent() returns 100 for full batch")
    void returns100ForFull() {
      BatchState state = BatchState.withPendingEntries(100, 1000L, 100L, 200L);
      int percent = state.estimatedFillPercent(100);
      assertEquals(100, percent);
    }

    @Test
    @DisplayName("estimatedFillPercent() caps at 100 when batch exceeds size")
    void capsAt100() {
      BatchState state = BatchState.withPendingEntries(150, 1000L, 100L, 200L);
      int percent = state.estimatedFillPercent(100);
      assertEquals(100, percent);
    }

    @Test
    @DisplayName("estimatedFillPercent() returns 0 for invalid batch size")
    void returns0ForInvalidBatchSize() {
      BatchState state = BatchState.withPendingEntries(5, 1000L, 100L, 200L);
      int percent = state.estimatedFillPercent(0);
      assertEquals(0, percent);
    }

    @Test
    @DisplayName("estimatedFillPercent() returns 0 for negative batch size")
    void returns0ForNegativeBatchSize() {
      BatchState state = BatchState.withPendingEntries(5, 1000L, 100L, 200L);
      int percent = state.estimatedFillPercent(-100);
      assertEquals(0, percent);
    }
  }

  @Nested
  @DisplayName("Helper Tests — averageBytesPerEntry()")
  class AverageBytesPerEntry {

    @Test
    @DisplayName("averageBytesPerEntry() returns 0 for empty batch")
    void returns0ForEmpty() {
      BatchState state = BatchState.emptyBatch();
      long avg = state.averageBytesPerEntry();
      assertEquals(0L, avg);
    }

    @Test
    @DisplayName("averageBytesPerEntry() calculates average correctly")
    void calculatesAverageCorrectly() {
      BatchState state = BatchState.withPendingEntries(10, 1000L, 100L, 200L);
      long avg = state.averageBytesPerEntry();
      assertEquals(100L, avg);
    }

    @Test
    @DisplayName("averageBytesPerEntry() handles single entry")
    void handlesSingleEntry() {
      BatchState state = BatchState.withPendingEntries(1, 500L, 100L, 200L);
      long avg = state.averageBytesPerEntry();
      assertEquals(500L, avg);
    }

    @Test
    @DisplayName("averageBytesPerEntry() returns integer division result")
    void returnsIntegerDivision() {
      BatchState state = BatchState.withPendingEntries(3, 100L, 100L, 200L);
      long avg = state.averageBytesPerEntry();
      assertEquals(33L, avg);
    }
  }

  @Nested
  @DisplayName("Helper Tests — wouldExceedCapacity()")
  class WouldExceedCapacity {

    @Test
    @DisplayName("wouldExceedCapacity() returns false for empty batch with valid add")
    void returnsFalseForEmptyWithValidAdd() {
      BatchState state = BatchState.emptyBatch();
      boolean wouldExceed = state.wouldExceedCapacity(100L, 10);
      assertFalse(wouldExceed);
    }

    @Test
    @DisplayName("wouldExceedCapacity() returns true when adding would exceed capacity")
    void returnsTrueWhenExceeded() {
      BatchState state = BatchState.withPendingEntries(10, 1000L, 100L, 200L);
      boolean wouldExceed = state.wouldExceedCapacity(100L, 10);
      assertTrue(wouldExceed);
    }

    @Test
    @DisplayName("wouldExceedCapacity() returns false when at capacity boundary")
    void returnsFalseAtBoundary() {
      BatchState state = BatchState.withPendingEntries(9, 1000L, 100L, 200L);
      boolean wouldExceed = state.wouldExceedCapacity(100L, 10);
      assertFalse(wouldExceed);
    }

    @Test
    @DisplayName("wouldExceedCapacity() returns true when already at capacity")
    void returnsTrueAlreadyAtCapacity() {
      BatchState state = BatchState.withPendingEntries(10, 1000L, 100L, 200L);
      boolean wouldExceed = state.wouldExceedCapacity(100L, 10);
      assertTrue(wouldExceed);
    }

    @Test
    @DisplayName("wouldExceedCapacity() returns true for invalid batch size")
    void returnsTrueForInvalidBatchSize() {
      BatchState state = BatchState.withPendingEntries(5, 1000L, 100L, 200L);
      boolean wouldExceed = state.wouldExceedCapacity(100L, 0);
      assertTrue(wouldExceed);
    }

    @Test
    @DisplayName("wouldExceedCapacity() returns true for negative batch size")
    void returnsTrueForNegativeBatchSize() {
      BatchState state = BatchState.withPendingEntries(5, 1000L, 100L, 200L);
      boolean wouldExceed = state.wouldExceedCapacity(100L, -100);
      assertTrue(wouldExceed);
    }
  }

  @Nested
  @DisplayName("Edge Cases & Boundary Values")
  class EdgeCases {

    @Test
    @DisplayName("handles Long.MAX_VALUE entries and bytes")
    void handlesMaxValues() {
      BatchState state =
          new BatchState(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE - 1, Long.MAX_VALUE, false);
      assertEquals(Long.MAX_VALUE, state.entriesPendingInBatch());
      assertEquals(Long.MAX_VALUE, state.totalBytesInBatch());
    }

    @Test
    @DisplayName("handles single entry with minimal bytes")
    void handlesSingleEntryMinimalBytes() {
      BatchState state = BatchState.withPendingEntries(1, 1L, 0L, 0L);
      assertEquals(1L, state.entriesPendingInBatch());
      assertEquals(1L, state.totalBytesInBatch());
    }

    @Test
    @DisplayName("handles batch with timestamps spanning Long min/max")
    void handlesExtremeBatchTimestamps() {

      BatchState state = BatchState.emptyBatch();
      assertEquals(Long.MIN_VALUE, state.oldestEntryTimestamp());
      assertEquals(Long.MAX_VALUE, state.newestEntryTimestamp());
    }

    @Test
    @DisplayName("handles estimatedFillPercent with very large batch size")
    void handlesLargeBatchSize() {
      BatchState state = BatchState.withPendingEntries(1000, 10000L, 100L, 200L);
      int percent = state.estimatedFillPercent(Integer.MAX_VALUE);
      assertEquals(0, percent);
    }
  }
}
