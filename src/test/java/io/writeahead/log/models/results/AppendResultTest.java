package io.writeahead.log.models.results;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AppendResult Tests — 100% Validation Coverage")
class AppendResultTest {

  @Nested
  @DisplayName("Compact Constructor Validation")
  class ConstructorValidation {

    @Test
    void rejectsEntriesPendingNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new AppendResult(false, -1, 0L, 0L, 0L, 0L, 48L, false, null));
      assertTrue(ex.getMessage().contains("entriesPendingInBatch cannot be negative"));
    }

    @Test
    void rejectsCurrentSegmentEntryCountNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new AppendResult(false, 0, -1L, 0L, 0L, 0L, 48L, false, null));
      assertTrue(ex.getMessage().contains("currentSegmentEntryCount cannot be negative"));
    }

    @Test
    void rejectsCurrentSegmentSequenceNumberNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new AppendResult(false, 0, 0L, -1L, 0L, 0L, 48L, false, null));
      assertTrue(ex.getMessage().contains("currentSegmentSequenceNumber cannot be negative"));
    }

    @Test
    void rejectsCurrentSegmentByteCountLessThan48() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new AppendResult(false, 0, 0L, 0L, 0L, 0L, 47L, false, null));
      assertTrue(ex.getMessage().contains("currentSegmentByteCount must be >= 48"));
    }

    @Test
    void rejectsCorruptionWithNullErrorMessage() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new AppendResult(false, 0, 0L, 0L, 0L, 0L, 48L, true, null));
      assertTrue(
          ex.getMessage().contains("corruptionDetected=true")
              && ex.getMessage().contains("errorMessage must be non-null"));
    }

    @Test
    void rejectsNonCorruptionWithErrorMessage() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new AppendResult(false, 0, 0L, 0L, 0L, 0L, 48L, false, "Error"));
      assertTrue(
          ex.getMessage().contains("corruptionDetected=false")
              && ex.getMessage().contains("errorMessage must be null"));
    }

    @Test
    void rejectsFlushedWithPendingEntries() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new AppendResult(true, 5, 0L, 0L, 0L, 0L, 48L, false, null));
      assertTrue(
          ex.getMessage().contains("flushed=true")
              && ex.getMessage().contains("entriesPendingInBatch must be 0"));
    }

    @Test
    void rejectsMinTimestampGreaterThanMaxTimestamp() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new AppendResult(false, 0, 0L, 0L, 2000L, 1000L, 48L, false, null));
      assertTrue(
          ex.getMessage().contains("currentSegmentMinTimestamp")
              && ex.getMessage().contains("cannot be > currentSegmentMaxTimestamp"));
    }

    @Test
    void acceptsValidAppendResult() {
      AppendResult result = new AppendResult(false, 0, 0L, 0L, 0L, 0L, 48L, false, null);
      assertFalse(result.isErrorState());
    }
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    void successfulAppendNoFlushCreatesCorrectResult() {
      AppendResult result = AppendResult.successfulAppendNoFlush(5, 0L, 10L, 500L, 0L, 100L);
      assertFalse(result.flushed());
      assertEquals(5, result.entriesPendingInBatch());
      assertTrue(result.canAcceptMoreWrites());
    }

    @Test
    void successfulAppendWithFlushCreatesCorrectResult() {
      AppendResult result = AppendResult.successfulAppendWithFlush(0L, 10L, 500L, 0L, 100L);
      assertTrue(result.flushed());
      assertEquals(0, result.entriesPendingInBatch());
      assertTrue(result.isDurable());
    }

    @Test
    void corruptionDetectedResultRejectsNullMessage() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> AppendResult.corruptionDetectedResult(0L, 0L, 48L, 0L, 0L, null));
      assertTrue(ex.getMessage().contains("errorMessage"));
    }

    @Test
    void corruptionDetectedResultCreatesCorrectResult() {
      AppendResult result = AppendResult.corruptionDetectedResult(0L, 0L, 48L, 0L, 0L, "Corrupted");
      assertTrue(result.isErrorState());
      assertFalse(result.canAcceptMoreWrites());
    }
  }

  @Nested
  @DisplayName("Helper Methods")
  class HelperMethods {

    @Test
    void isErrorStateReturnsTrueForCorruption() {
      AppendResult result = AppendResult.corruptionDetectedResult(0L, 0L, 48L, 0L, 0L, "Error");
      assertTrue(result.isErrorState());
    }

    @Test
    void isErrorStateReturnsFalseForSuccess() {
      AppendResult result = AppendResult.successfulAppendNoFlush(0, 0L, 0L, 48L, 0L, 0L);
      assertFalse(result.isErrorState());
    }

    @Test
    void isDurableReturnsTrueWhenFlushed() {
      AppendResult result = AppendResult.successfulAppendWithFlush(0L, 0L, 48L, 0L, 0L);
      assertTrue(result.isDurable());
    }

    @Test
    void isDurableReturnsFalseWhenNotFlushed() {
      AppendResult result = AppendResult.successfulAppendNoFlush(0, 0L, 0L, 48L, 0L, 0L);
      assertFalse(result.isDurable());
    }

    @Test
    void canAcceptMoreWritesReturnsFalseForError() {
      AppendResult result = AppendResult.corruptionDetectedResult(0L, 0L, 48L, 0L, 0L, "Error");
      assertFalse(result.canAcceptMoreWrites());
    }

    @Test
    void canAcceptMoreWritesReturnsTrueForSuccess() {
      AppendResult result = AppendResult.successfulAppendNoFlush(0, 0L, 0L, 48L, 0L, 0L);
      assertTrue(result.canAcceptMoreWrites());
    }

    @Test
    void estimateRotationRiskPercentCalculatesCorrectly() {
      AppendResult result = AppendResult.successfulAppendNoFlush(0, 0L, 10L, 500L, 0L, 0L);
      int risk = result.estimateRotationRiskPercent(1000L);
      assertEquals(50, risk);
    }

    @Test
    void estimateRotationRiskPercentCapsAt100() {
      AppendResult result = AppendResult.successfulAppendNoFlush(0, 0L, 10L, 1500L, 0L, 0L);
      int risk = result.estimateRotationRiskPercent(1000L);
      assertEquals(100, risk);
    }
  }
}
