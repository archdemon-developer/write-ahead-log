package io.writeahead.log.models.states;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SegmentState Tests — 100% Validation Coverage")
class SegmentStateTest {

  @Nested
  @DisplayName("Compact Constructor Validation — segmentSequenceNumber <= 0")
  class ConstructorValidation_SequenceNumber {

    @Test
    @DisplayName("constructor rejects segmentSequenceNumber = 0")
    void rejectsSequenceZero() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentState(0L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false));
      assertTrue(ex.getMessage().contains("Segment sequence number must be greater than zero"));
    }

    @Test
    @DisplayName("constructor rejects segmentSequenceNumber = -1")
    void rejectsSequenceNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentState(-1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false));
      assertTrue(ex.getMessage().contains("Segment sequence number must be greater than zero"));
    }

    @Test
    @DisplayName("constructor rejects segmentSequenceNumber = Long.MIN_VALUE")
    void rejectsSequenceMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new SegmentState(
                      Long.MIN_VALUE, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false));
      assertTrue(ex.getMessage().contains("Segment sequence number must be greater than zero"));
    }

    @Test
    @DisplayName("constructor accepts segmentSequenceNumber = 1")
    void acceptsSequenceOne() {
      SegmentState state =
          new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      assertEquals(1L, state.segmentSequenceNumber());
    }

    @Test
    @DisplayName("constructor accepts segmentSequenceNumber = Long.MAX_VALUE")
    void acceptsSequenceMaxValue() {
      SegmentState state =
          new SegmentState(Long.MAX_VALUE, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      assertEquals(Long.MAX_VALUE, state.segmentSequenceNumber());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — entryCount < 0")
  class ConstructorValidation_EntryCount {

    @Test
    @DisplayName("constructor rejects entryCount = -1")
    void rejectsEntryCountNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentState(1L, -1L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false));
      assertTrue(ex.getMessage().contains("Entry count must be greater than or equal to 0"));
    }

    @Test
    @DisplayName("constructor rejects entryCount = Long.MIN_VALUE")
    void rejectsEntryCountMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new SegmentState(
                      1L, Long.MIN_VALUE, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false));
      assertTrue(ex.getMessage().contains("Entry count must be greater than or equal to 0"));
    }

    @Test
    @DisplayName("constructor accepts entryCount = 0")
    void acceptsEntryCountZero() {
      SegmentState state =
          new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      assertEquals(0L, state.entryCount());
    }

    @Test
    @DisplayName("constructor accepts entryCount = Long.MAX_VALUE")
    void acceptsEntryCountMaxValue() {
      SegmentState state =
          new SegmentState(1L, Long.MAX_VALUE, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      assertEquals(Long.MAX_VALUE, state.entryCount());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — totalByteCount < 48")
  class ConstructorValidation_ByteCount {

    @Test
    @DisplayName("constructor rejects totalByteCount = 0")
    void rejectsBytesZero() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentState(1L, 0L, 0L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false));
      assertTrue(ex.getMessage().contains("Total byte count must be greater than or equal to 48"));
    }

    @Test
    @DisplayName("constructor rejects totalByteCount = 47")
    void rejectsBytesLessThan48() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentState(1L, 0L, 47L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false));
      assertTrue(ex.getMessage().contains("Total byte count must be greater than or equal to 48"));
    }

    @Test
    @DisplayName("constructor rejects totalByteCount = Long.MIN_VALUE")
    void rejectsBytesMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new SegmentState(
                      1L, 0L, Long.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false));
      assertTrue(ex.getMessage().contains("Total byte count must be greater than or equal to 48"));
    }

    @Test
    @DisplayName("constructor accepts totalByteCount = 48")
    void acceptsBytesExactly48() {
      SegmentState state =
          new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      assertEquals(48L, state.totalByteCount());
    }

    @Test
    @DisplayName("constructor accepts totalByteCount > 48")
    void acceptsBytesGreaterThan48() {
      SegmentState state =
          new SegmentState(1L, 0L, 1000L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      assertEquals(1000L, state.totalByteCount());
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
              IllegalArgumentException.class,
              () -> new SegmentState(1L, 0L, 48L, 2000L, 1000L, 1000L, false));
      assertTrue(ex.getMessage().contains("minTimestamp is greater than maxTimestamp"));
    }

    @Test
    @DisplayName("constructor rejects minTimestamp >> maxTimestamp")
    void rejectsMinMuchGreaterThanMax() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentState(1L, 0L, 48L, Long.MAX_VALUE, Long.MIN_VALUE, 1000L, false));
      assertTrue(ex.getMessage().contains("minTimestamp is greater than maxTimestamp"));
    }

    @Test
    @DisplayName("constructor accepts minTimestamp == maxTimestamp")
    void acceptsEqualTimestamps() {
      SegmentState state = new SegmentState(1L, 0L, 48L, 1000L, 1000L, 1000L, false);
      assertEquals(1000L, state.minTimestamp());
      assertEquals(1000L, state.maxTimestamp());
    }

    @Test
    @DisplayName("constructor accepts minTimestamp < maxTimestamp")
    void acceptsMinLessThanMax() {
      SegmentState state = new SegmentState(1L, 0L, 48L, 1000L, 2000L, 1000L, false);
      assertEquals(1000L, state.minTimestamp());
      assertEquals(2000L, state.maxTimestamp());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — createdAtTimestamp < 0")
  class ConstructorValidation_CreatedAt {

    @Test
    @DisplayName("constructor rejects createdAtTimestamp = -1")
    void rejectsCreatedAtNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, -1L, false));
      assertTrue(ex.getMessage().contains("createdAtTimestamp must be greater than or equal to 0"));
    }

    @Test
    @DisplayName("constructor rejects createdAtTimestamp = Long.MIN_VALUE")
    void rejectsCreatedAtMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new SegmentState(
                      1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, false));
      assertTrue(ex.getMessage().contains("createdAtTimestamp must be greater than or equal to 0"));
    }

    @Test
    @DisplayName("constructor accepts createdAtTimestamp = 0")
    void acceptsCreatedAtZero() {
      SegmentState state = new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 0L, false);
      assertEquals(0L, state.createdAtTimestamp());
    }

    @Test
    @DisplayName("constructor accepts createdAtTimestamp = Long.MAX_VALUE")
    void acceptsCreatedAtMaxValue() {
      SegmentState state =
          new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, false);
      assertEquals(Long.MAX_VALUE, state.createdAtTimestamp());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — isFinalized && entryCount <= 0")
  class ConstructorValidation_FinalizedWithoutEntries {

    @Test
    @DisplayName("constructor rejects isFinalized=true with entryCount=0")
    void rejectsFinalizedZeroEntries() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, true));
      assertTrue(ex.getMessage().contains("Cannot finalize segment with 0 entries"));
    }

    @Test
    @DisplayName("constructor rejects isFinalized=true with entryCount=-1")
    void rejectsFinalizedNegativeEntries() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentState(1L, -1L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, true));
      assertTrue(ex.getMessage().toLowerCase().contains("entry"));
    }

    @Test
    @DisplayName("constructor accepts isFinalized=true with entryCount=1")
    void acceptsFinalizedWithEntries() {
      SegmentState state =
          new SegmentState(1L, 1L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, true);
      assertTrue(state.isFinalized());
      assertEquals(1L, state.entryCount());
    }

    @Test
    @DisplayName("constructor accepts isFinalized=false with entryCount=0")
    void acceptsOpenSegmentZeroEntries() {
      SegmentState state =
          new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      assertFalse(state.isFinalized());
      assertEquals(0L, state.entryCount());
    }
  }

  @Nested
  @DisplayName("Factory Method Tests — emptyOpenSegment()")
  class FactoryMethod_EmptyOpenSegment {

    @Test
    @DisplayName("emptyOpenSegment() sets sequenceNumber")
    void setsSequenceNumber() {
      SegmentState state = SegmentState.emptyOpenSegment(42L, 1000L);
      assertEquals(42L, state.segmentSequenceNumber());
    }

    @Test
    @DisplayName("emptyOpenSegment() sets entryCount to 0")
    void setsEntryCountZero() {
      SegmentState state = SegmentState.emptyOpenSegment(1L, 1000L);
      assertEquals(0L, state.entryCount());
    }

    @Test
    @DisplayName("emptyOpenSegment() sets totalByteCount to 48")
    void setsByteCountTo48() {
      SegmentState state = SegmentState.emptyOpenSegment(1L, 1000L);
      assertEquals(48L, state.totalByteCount());
    }

    @Test
    @DisplayName("emptyOpenSegment() sets minTimestamp to Long.MIN_VALUE")
    void setsMinTimestampToMin() {
      SegmentState state = SegmentState.emptyOpenSegment(1L, 1000L);
      assertEquals(Long.MIN_VALUE, state.minTimestamp());
    }

    @Test
    @DisplayName("emptyOpenSegment() sets maxTimestamp to Long.MAX_VALUE")
    void setsMaxTimestampToMax() {
      SegmentState state = SegmentState.emptyOpenSegment(1L, 1000L);
      assertEquals(Long.MAX_VALUE, state.maxTimestamp());
    }

    @Test
    @DisplayName("emptyOpenSegment() sets createdAtTimestamp")
    void setsCreatedAtTimestamp() {
      long timestamp = 9999999L;
      SegmentState state = SegmentState.emptyOpenSegment(1L, timestamp);
      assertEquals(timestamp, state.createdAtTimestamp());
    }

    @Test
    @DisplayName("emptyOpenSegment() sets isFinalized to false")
    void setsFinalizedFalse() {
      SegmentState state = SegmentState.emptyOpenSegment(1L, 1000L);
      assertFalse(state.isFinalized());
    }
  }

  @Nested
  @DisplayName("Factory Method Tests — withEntries()")
  class FactoryMethod_WithEntries {

    @Test
    @DisplayName("withEntries() sets all parameters correctly")
    void setsAllParametersCorrectly() {
      SegmentState state = SegmentState.withEntries(5L, 100L, 5000L, 1000L, 2000L, 9999L, true);
      assertEquals(5L, state.segmentSequenceNumber());
      assertEquals(100L, state.entryCount());
      assertEquals(5000L, state.totalByteCount());
      assertEquals(1000L, state.minTimestamp());
      assertEquals(2000L, state.maxTimestamp());
      assertEquals(9999L, state.createdAtTimestamp());
      assertTrue(state.isFinalized());
    }

    @Test
    @DisplayName("withEntries() validates all parameters via constructor")
    void validatesAllParameters() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> SegmentState.withEntries(0L, 100L, 5000L, 1000L, 2000L, 9999L, false));
      assertTrue(ex.getMessage().contains("Segment sequence number"));
    }
  }

  @Nested
  @DisplayName("Helper Tests — estimatedFillPercent()")
  class EstimatedFillPercent {

    @Test
    @DisplayName("estimatedFillPercent() calculates percentage correctly")
    void calculatesPercentageCorrectly() {
      SegmentState state =
          new SegmentState(1L, 10L, 5000L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      int percent = state.estimatedFillPercent(10000L);
      assertEquals(50, percent);
    }

    @Test
    @DisplayName("estimatedFillPercent() returns 0 for empty segment")
    void returns0ForEmpty() {
      SegmentState state =
          new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      int percent = state.estimatedFillPercent(10000L);
      assertEquals(0, percent);
    }

    @Test
    @DisplayName("estimatedFillPercent() returns 100 for full segment")
    void returns100ForFull() {
      SegmentState state =
          new SegmentState(1L, 0L, 10000L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      int percent = state.estimatedFillPercent(10000L);
      assertEquals(100, percent);
    }

    @Test
    @DisplayName("estimatedFillPercent() caps at 100")
    void capsAt100() {
      SegmentState state =
          new SegmentState(1L, 0L, 15000L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      int percent = state.estimatedFillPercent(10000L);
      assertEquals(100, percent);
    }

    @Test
    @DisplayName("estimatedFillPercent() returns 0 for invalid max size")
    void returns0ForInvalidMaxSize() {
      SegmentState state =
          new SegmentState(1L, 0L, 5000L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      int percent = state.estimatedFillPercent(0L);
      assertEquals(0, percent);
    }

    @Test
    @DisplayName("estimatedFillPercent() returns 0 for negative max size")
    void returns0ForNegativeMaxSize() {
      SegmentState state =
          new SegmentState(1L, 0L, 5000L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      int percent = state.estimatedFillPercent(-1000L);
      assertEquals(0, percent);
    }
  }

  @Nested
  @DisplayName("Helper Tests — ageInMilliseconds()")
  class AgeInMilliseconds {

    @Test
    @DisplayName("ageInMilliseconds() calculates age correctly")
    void calculatesAgeCorrectly() {
      SegmentState state =
          new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      long age = state.ageInMilliseconds(2000L);
      assertEquals(1000L, age);
    }

    @Test
    @DisplayName("ageInMilliseconds() returns 0 when current time equals created time")
    void returns0WhenSameTime() {
      SegmentState state =
          new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      long age = state.ageInMilliseconds(1000L);
      assertEquals(0L, age);
    }

    @Test
    @DisplayName("ageInMilliseconds() returns 0 when current time is before created time")
    void returns0WhenClockGoesBackward() {
      SegmentState state =
          new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 2000L, false);
      long age = state.ageInMilliseconds(1000L);
      assertEquals(0L, age);
    }

    @Test
    @DisplayName("ageInMilliseconds() handles large time differences")
    void handlesLargeTimeDifferences() {
      SegmentState state = new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 0L, false);
      long age = state.ageInMilliseconds(Long.MAX_VALUE - 1);
      assertEquals(Long.MAX_VALUE - 1, age);
    }
  }

  @Nested
  @DisplayName("Helper Tests — averageBytesPerEntry()")
  class AverageBytesPerEntry {

    @Test
    @DisplayName("averageBytesPerEntry() calculates average correctly")
    void calculatesAverageCorrectly() {
      SegmentState state =
          new SegmentState(1L, 10L, 1000L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      long avg = state.averageBytesPerEntry();
      assertEquals(100L, avg);
    }

    @Test
    @DisplayName("averageBytesPerEntry() returns 0 for empty segment")
    void returns0ForEmpty() {
      SegmentState state =
          new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      long avg = state.averageBytesPerEntry();
      assertEquals(0L, avg);
    }

    @Test
    @DisplayName("averageBytesPerEntry() handles single entry")
    void handlesSingleEntry() {
      SegmentState state =
          new SegmentState(1L, 1L, 500L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      long avg = state.averageBytesPerEntry();
      assertEquals(500L, avg);
    }

    @Test
    @DisplayName("averageBytesPerEntry() returns integer division result")
    void returnsIntegerDivision() {
      SegmentState state =
          new SegmentState(1L, 3L, 100L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      long avg = state.averageBytesPerEntry();
      assertEquals(33L, avg);
    }
  }

  @Nested
  @DisplayName("Helper Tests — canAcceptMoreEntries()")
  class CanAcceptMoreEntries {

    @Test
    @DisplayName("canAcceptMoreEntries() returns true for open segment")
    void returnsTrueForOpen() {
      SegmentState state =
          new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      assertTrue(state.canAcceptMoreEntries());
    }

    @Test
    @DisplayName("canAcceptMoreEntries() returns false for finalized segment")
    void returnsFalseForFinalized() {
      SegmentState state =
          new SegmentState(1L, 1L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, true);
      assertFalse(state.canAcceptMoreEntries());
    }

    @Test
    @DisplayName("canAcceptMoreEntries() reflects isFinalized flag")
    void reflectsIsFinalized() {
      SegmentState openState =
          new SegmentState(1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, false);
      SegmentState finalizedState =
          new SegmentState(1L, 1L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, 1000L, true);

      assertEquals(!openState.isFinalized(), openState.canAcceptMoreEntries());
      assertEquals(!finalizedState.isFinalized(), finalizedState.canAcceptMoreEntries());
    }
  }

  @Nested
  @DisplayName("Edge Cases & Boundary Values")
  class EdgeCases {

    @Test
    @DisplayName("handles all MAX values")
    void handlesMaxValues() {
      SegmentState state =
          new SegmentState(
              Long.MAX_VALUE,
              Long.MAX_VALUE,
              Long.MAX_VALUE,
              Long.MAX_VALUE - 1,
              Long.MAX_VALUE,
              Long.MAX_VALUE,
              false);
      assertEquals(Long.MAX_VALUE, state.segmentSequenceNumber());
      assertEquals(Long.MAX_VALUE, state.entryCount());
    }

    @Test
    @DisplayName("handles segment with 1 entry and minimum byte count")
    void handlesMinimalFilledSegment() {
      SegmentState state = new SegmentState(1L, 1L, 48L, 0L, 0L, 1000L, true);
      assertEquals(1L, state.entryCount());
      assertEquals(48L, state.totalByteCount());
    }
  }
}
