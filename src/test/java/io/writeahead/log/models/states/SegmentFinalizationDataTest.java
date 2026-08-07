package io.writeahead.log.models.states;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SegmentFinalizationData Tests — 100% Validation Coverage")
class SegmentFinalizationDataTest {

  @Nested
  @DisplayName("Compact Constructor Validation — entryCount <= 0")
  class ConstructorValidation_EntryCount {

    @Test
    @DisplayName("constructor rejects entryCount = 0")
    void rejectsEntryCountZero() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new SegmentFinalizationData(0, 1000L, 2000L));
      assertTrue(ex.getMessage().contains("Cannot finalize segment with 0 entries"));
    }

    @Test
    @DisplayName("constructor rejects entryCount = -1")
    void rejectsEntryCountNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new SegmentFinalizationData(-1, 1000L, 2000L));
      assertTrue(ex.getMessage().contains("Cannot finalize segment with 0 entries"));
    }

    @Test
    @DisplayName("constructor rejects entryCount = Integer.MIN_VALUE")
    void rejectsEntryCountMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentFinalizationData(Integer.MIN_VALUE, 1000L, 2000L));
      assertTrue(ex.getMessage().contains("Cannot finalize segment with 0 entries"));
    }

    @Test
    @DisplayName("constructor accepts entryCount = 1")
    void acceptsEntryCountOne() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 1000L, 2000L);
      assertEquals(1, data.entryCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100, 1000, Integer.MAX_VALUE})
    @DisplayName("constructor accepts various positive entryCount values")
    void acceptsPositiveEntryCounts(int entryCount) {
      SegmentFinalizationData data = new SegmentFinalizationData(entryCount, 1000L, 2000L);
      assertEquals(entryCount, data.entryCount());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — minTimestamp < 0")
  class ConstructorValidation_MinTimestamp {

    @Test
    @DisplayName("constructor rejects minTimestamp = -1")
    void rejectsMinTimestampNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new SegmentFinalizationData(1, -1L, 2000L));
      assertTrue(ex.getMessage().contains("minTimestamp cannot be negative"));
    }

    @Test
    @DisplayName("constructor rejects minTimestamp = Long.MIN_VALUE")
    void rejectsMinTimestampMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentFinalizationData(1, Long.MIN_VALUE, 2000L));
      assertTrue(ex.getMessage().contains("minTimestamp cannot be negative"));
    }

    @Test
    @DisplayName("constructor accepts minTimestamp = 0")
    void acceptsMinTimestampZero() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 0L, 2000L);
      assertEquals(0L, data.minTimestamp());
    }

    @Test
    @DisplayName("constructor accepts minTimestamp = Long.MAX_VALUE")
    void acceptsMinTimestampMaxValue() {
      SegmentFinalizationData data =
          new SegmentFinalizationData(1, Long.MAX_VALUE - 1, Long.MAX_VALUE);
      assertEquals(Long.MAX_VALUE - 1, data.minTimestamp());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — maxTimestamp < 0")
  class ConstructorValidation_MaxTimestamp {

    @Test
    @DisplayName("constructor rejects maxTimestamp = -1")
    void rejectsMaxTimestampNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new SegmentFinalizationData(1, 1000L, -1L));
      assertTrue(ex.getMessage().contains("maxTimestamp cannot be negative"));
    }

    @Test
    @DisplayName("constructor rejects maxTimestamp = Long.MIN_VALUE")
    void rejectsMaxTimestampMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentFinalizationData(1, 1000L, Long.MIN_VALUE));
      assertTrue(ex.getMessage().contains("maxTimestamp cannot be negative"));
    }

    @Test
    @DisplayName("constructor accepts maxTimestamp = 0")
    void acceptsMaxTimestampZero() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 0L, 0L);
      assertEquals(0L, data.maxTimestamp());
    }

    @Test
    @DisplayName("constructor accepts maxTimestamp = Long.MAX_VALUE")
    void acceptsMaxTimestampMaxValue() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 0L, Long.MAX_VALUE);
      assertEquals(Long.MAX_VALUE, data.maxTimestamp());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — minTimestamp > maxTimestamp")
  class ConstructorValidation_TimestampOrdering {

    @Test
    @DisplayName("constructor rejects minTimestamp > maxTimestamp")
    void rejectsMinGreaterThanMax() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new SegmentFinalizationData(1, 2000L, 1000L));
      assertTrue(
          ex.getMessage().contains("minTimestamp")
              && ex.getMessage().contains("cannot be > maxTimestamp"));
    }

    @Test
    @DisplayName("constructor rejects minTimestamp >> maxTimestamp")
    void rejectsMinMuchGreaterThanMax() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentFinalizationData(1, Long.MAX_VALUE, 0L));
      assertTrue(
          ex.getMessage().contains("minTimestamp")
              && ex.getMessage().contains("cannot be > maxTimestamp"));
    }

    @Test
    @DisplayName("constructor accepts minTimestamp == maxTimestamp")
    void acceptsEqualTimestamps() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 1000L, 1000L);
      assertEquals(1000L, data.minTimestamp());
      assertEquals(1000L, data.maxTimestamp());
    }

    @Test
    @DisplayName("constructor accepts minTimestamp < maxTimestamp")
    void acceptsMinLessThanMax() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 1000L, 2000L);
      assertEquals(1000L, data.minTimestamp());
      assertEquals(2000L, data.maxTimestamp());
    }
  }

  @Nested
  @DisplayName("Factory Method Tests — of()")
  class FactoryMethod_Of {

    @Test
    @DisplayName("of() sets entryCount correctly")
    void setsEntryCount() {
      SegmentFinalizationData data = SegmentFinalizationData.of(42, 1000L, 2000L);
      assertEquals(42, data.entryCount());
    }

    @Test
    @DisplayName("of() sets minTimestamp correctly")
    void setsMinTimestamp() {
      SegmentFinalizationData data = SegmentFinalizationData.of(1, 111111L, 222222L);
      assertEquals(111111L, data.minTimestamp());
    }

    @Test
    @DisplayName("of() sets maxTimestamp correctly")
    void setsMaxTimestamp() {
      SegmentFinalizationData data = SegmentFinalizationData.of(1, 111111L, 222222L);
      assertEquals(222222L, data.maxTimestamp());
    }

    @Test
    @DisplayName("of() validates all parameters")
    void validatesAllParameters() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> SegmentFinalizationData.of(0, 100L, 200L));
      assertTrue(
          ex.getMessage().contains("entryCount") || ex.getMessage().contains("Cannot finalize"));
    }
  }

  @Nested
  @DisplayName("Helper Tests — getTimestampRange()")
  class GetTimestampRange {

    @Test
    @DisplayName("getTimestampRange() calculates difference correctly")
    void calculatesRangeCorrectly() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 500L, 1500L);
      assertEquals(1000L, data.getTimestampRange());
    }

    @Test
    @DisplayName("getTimestampRange() returns zero when timestamps are equal")
    void returnsZeroWhenEqual() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 1000L, 1000L);
      assertEquals(0L, data.getTimestampRange());
    }

    @Test
    @DisplayName("getTimestampRange() handles large ranges")
    void handlesLargeRanges() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 0L, Long.MAX_VALUE);
      assertEquals(Long.MAX_VALUE, data.getTimestampRange());
    }

    @Test
    @DisplayName("getTimestampRange() handles zero timestamps")
    void handlesZeroTimestamps() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 0L, 1000L);
      assertEquals(1000L, data.getTimestampRange());
    }
  }

  @Nested
  @DisplayName("Helper Tests — getAverageTimestampPerEntry()")
  class GetAverageTimestampPerEntry {

    @Test
    @DisplayName("getAverageTimestampPerEntry() calculates average correctly")
    void calculatesAverageCorrectly() {
      SegmentFinalizationData data = new SegmentFinalizationData(10, 0L, 1000L);
      assertEquals(100L, data.getAverageTimestampPerEntry());
    }

    @Test
    @DisplayName("getAverageTimestampPerEntry() returns zero for single entry")
    void returnsZeroForSingleEntry() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 0L, 0L);
      assertEquals(0L, data.getAverageTimestampPerEntry());
    }

    @Test
    @DisplayName("getAverageTimestampPerEntry() handles large ranges")
    void handlesLargeRanges() {
      SegmentFinalizationData data = new SegmentFinalizationData(1000000, 0L, Long.MAX_VALUE);
      long average = data.getAverageTimestampPerEntry();
      assertTrue(average > 0);
    }

    @Test
    @DisplayName("getAverageTimestampPerEntry() returns integer division result")
    void returnsIntegerDivision() {
      SegmentFinalizationData data = new SegmentFinalizationData(3, 0L, 100L);
      assertEquals(33L, data.getAverageTimestampPerEntry());
    }

    @Test
    @DisplayName("getAverageTimestampPerEntry() returns 0 for timestamp difference of 0")
    void returns0ForZeroDifference() {
      SegmentFinalizationData data = new SegmentFinalizationData(10, 1000L, 1000L);
      assertEquals(0L, data.getAverageTimestampPerEntry());
    }
  }

  @Nested
  @DisplayName("Helper Tests — hasValidTimestampRange()")
  class HasValidTimestampRange {

    @Test
    @DisplayName("hasValidTimestampRange() returns true when min <= max")
    void returnsTrueWhenValid() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 1000L, 2000L);
      assertTrue(data.hasValidTimestampRange());
    }

    @Test
    @DisplayName("hasValidTimestampRange() returns true when min == max")
    void returnsTrueWhenEqual() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 1000L, 1000L);
      assertTrue(data.hasValidTimestampRange());
    }

    @Test
    @DisplayName("hasValidTimestampRange() always returns true (validation in constructor)")
    void alwaysReturnsTrueAfterConstruction() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 0L, Long.MAX_VALUE);
      assertTrue(data.hasValidTimestampRange());
    }
  }

  @Nested
  @DisplayName("Helper Tests — coversTimestamp()")
  class CoversTimestamp {

    @Test
    @DisplayName("coversTimestamp() returns true when timestamp equals minTimestamp")
    void returnsTrueAtMin() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 1000L, 2000L);
      assertTrue(data.coversTimestamp(1000L));
    }

    @Test
    @DisplayName("coversTimestamp() returns true when timestamp equals maxTimestamp")
    void returnsTrueAtMax() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 1000L, 2000L);
      assertTrue(data.coversTimestamp(2000L));
    }

    @Test
    @DisplayName("coversTimestamp() returns true when timestamp is between min and max")
    void returnsTrueInRange() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 1000L, 2000L);
      assertTrue(data.coversTimestamp(1500L));
    }

    @Test
    @DisplayName("coversTimestamp() returns false when timestamp is before minTimestamp")
    void returnsFalseBeforeMin() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 1000L, 2000L);
      assertFalse(data.coversTimestamp(999L));
    }

    @Test
    @DisplayName("coversTimestamp() returns false when timestamp is after maxTimestamp")
    void returnsFalseAfterMax() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 1000L, 2000L);
      assertFalse(data.coversTimestamp(2001L));
    }

    @Test
    @DisplayName("coversTimestamp() returns true for single-entry segment at exact timestamp")
    void returnsTrueForSingleEntryAtExactTimestamp() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 1000L, 1000L);
      assertTrue(data.coversTimestamp(1000L));
    }

    @Test
    @DisplayName("coversTimestamp() handles 0 and Long.MAX_VALUE boundaries")
    void handlesLongBoundaryValues() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 0L, Long.MAX_VALUE);
      assertTrue(data.coversTimestamp(0L));
      assertTrue(data.coversTimestamp(Long.MAX_VALUE / 2));
      assertTrue(data.coversTimestamp(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("coversTimestamp() returns false for values outside range")
    void returnsFalseOutsideRange() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 100L, 1000L);
      assertFalse(data.coversTimestamp(99L));
      assertFalse(data.coversTimestamp(1001L));
    }
  }

  @Nested
  @DisplayName("Edge Cases & Boundary Values")
  class EdgeCases {

    @Test
    @DisplayName("handles maximum values in all fields")
    void handlesMaximumValues() {
      SegmentFinalizationData data =
          new SegmentFinalizationData(Integer.MAX_VALUE, Long.MAX_VALUE - 1, Long.MAX_VALUE);
      assertEquals(Integer.MAX_VALUE, data.entryCount());
      assertEquals(Long.MAX_VALUE - 1, data.minTimestamp());
      assertEquals(Long.MAX_VALUE, data.maxTimestamp());
    }

    @Test
    @DisplayName("handles single entry with zero timestamps")
    void handlesSingleEntryZeroTimestamps() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 0L, 0L);
      assertEquals(1, data.entryCount());
      assertEquals(0L, data.minTimestamp());
      assertEquals(0L, data.maxTimestamp());
    }

    @Test
    @DisplayName("handles many entries with small timestamp range")
    void handlesManyEntriesSmallRange() {
      SegmentFinalizationData data = new SegmentFinalizationData(1000000, 1000L, 1001L);
      assertEquals(1000000, data.entryCount());
      assertEquals(1L, data.getTimestampRange());
    }

    @Test
    @DisplayName("handles single entry spanning large timestamp range")
    void handlesSingleEntryLargeRange() {
      SegmentFinalizationData data = new SegmentFinalizationData(1, 0L, Long.MAX_VALUE);

      assertEquals(1, data.entryCount());
      assertEquals(0L, data.minTimestamp());
      assertEquals(Long.MAX_VALUE, data.maxTimestamp());
    }
  }
}
