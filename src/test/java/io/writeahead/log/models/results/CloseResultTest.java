package io.writeahead.log.models.results;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CloseResult Tests — 100% Validation Coverage")
class CloseResultTest {

  @Nested
  @DisplayName("Compact Constructor Validation")
  class ConstructorValidation {

    @Test
    void rejectsTotalSegmentsAtCloseNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new CloseResult(true, -1L, 0L, 0L, 0L, 0L, false, false, null));
      assertTrue(ex.getMessage().contains("totalSegmentsAtClose cannot be negative"));
    }

    @Test
    void rejectsOldestSegmentSequenceNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new CloseResult(true, 0L, -1L, 0L, 0L, 0L, false, false, null));
      assertTrue(ex.getMessage().contains("oldestSegmentSequence cannot be negative"));
    }

    @Test
    void rejectsNewestSegmentSequenceNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new CloseResult(true, 0L, 0L, -1L, 0L, 0L, false, false, null));
      assertTrue(ex.getMessage().contains("newestSegmentSequence cannot be negative"));
    }

    @Test
    void rejectsTotalEntriesPersistedNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new CloseResult(true, 0L, 0L, 0L, -1L, 0L, false, false, null));
      assertTrue(ex.getMessage().contains("totalEntriesPersisted cannot be negative"));
    }

    @Test
    void rejectsTotalBytesPersistedNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new CloseResult(true, 0L, 0L, 0L, 0L, -1L, false, false, null));
      assertTrue(ex.getMessage().contains("totalBytesPersisted cannot be negative"));
    }

    @Test
    void rejectsTotalSegmentsZeroWithOldestNonZero() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new CloseResult(true, 0L, 1L, 0L, 0L, 0L, false, false, null));
      assertTrue(ex.getMessage().contains("oldestSegmentSequence must be 0"));
    }

    @Test
    void rejectsTotalSegmentsZeroWithNewestNonZero() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new CloseResult(true, 0L, 0L, 1L, 0L, 0L, false, false, null));
      assertTrue(ex.getMessage().contains("newestSegmentSequence must be 0"));
    }

    @Test
    void rejectsOldestGreaterThanNewest() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new CloseResult(true, 2L, 5L, 3L, 0L, 0L, false, false, null));
      assertTrue(
          ex.getMessage().contains("oldestSegmentSequence")
              && ex.getMessage().contains("newestSegmentSequence"));
    }

    @Test
    void rejectsHasUnflushedWithSuccess() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new CloseResult(true, 0L, 0L, 0L, 0L, 0L, true, false, null));
      assertTrue(ex.getMessage().contains("hasUnflushedEntries"));
    }

    @Test
    void rejectsSuccessWithErrorMessage() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new CloseResult(true, 0L, 0L, 0L, 0L, 0L, false, false, "Error"));
      assertTrue(
          ex.getMessage().contains("success=true")
              && ex.getMessage().contains("errorMessage must be null"));
    }

    @Test
    void rejectsSuccessWithCorruptionDetected() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new CloseResult(true, 0L, 0L, 0L, 0L, 0L, false, true, null));
      assertTrue(ex.getMessage().contains("corruptionDetected must be false"));
    }

    @Test
    void rejectsFailureWithNullErrorMessage() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new CloseResult(false, 0L, 0L, 0L, 0L, 0L, false, false, null));
      assertTrue(ex.getMessage().contains("errorMessage must be non-null"));
    }

    @Test
    void acceptsValidSuccessfulClose() {
      CloseResult result = new CloseResult(true, 1L, 0L, 0L, 100L, 5000L, false, false, null);
      assertTrue(result.success());
    }
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    void successfulCloseCreatesCorrectResult() {
      CloseResult result = CloseResult.successfulClose(2L, 1L, 2L, 100L, 5000L);
      assertTrue(result.success());
      assertFalse(result.hasUnflushedEntries());
      assertFalse(result.corruptionDetected());
      assertNull(result.errorMessage());
    }

    @Test
    void closeWithUnflushedEntriesRejectsNullMessage() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> CloseResult.closeWithUnflushedEntries(1L, 0L, 0L, 50L, 2000L, null));
      assertTrue(ex.getMessage().contains("errorMessage"));
    }

    @Test
    void closeWithUnflushedEntriesCreatesCorrectResult() {
      CloseResult result =
          CloseResult.closeWithUnflushedEntries(1L, 0L, 0L, 50L, 2000L, "Unflushed");
      assertFalse(result.success());
      assertTrue(result.hasUnflushedEntries());
    }

    @Test
    void closeFailedRejectsNullMessage() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> CloseResult.closeFailed(1L, 0L, 0L, null));
      assertTrue(ex.getMessage().contains("errorMessage"));
    }

    @Test
    void closeFailedCreatesCorrectResult() {
      CloseResult result = CloseResult.closeFailed(1L, 0L, 0L, "Failed");
      assertFalse(result.success());
      assertEquals(0L, result.totalEntriesPersisted());
    }

    @Test
    void corruptionDetectedAtCloseRejectsNullMessage() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> CloseResult.corruptionDetectedAtClose(1L, null));
      assertTrue(ex.getMessage().contains("errorMessage"));
    }

    @Test
    void corruptionDetectedAtCloseCreatesCorrectResult() {
      CloseResult result = CloseResult.corruptionDetectedAtClose(1L, "Corrupted");
      assertFalse(result.success());
      assertTrue(result.corruptionDetected());
    }
  }

  @Nested
  @DisplayName("Helper Methods")
  class HelperMethods {

    @Test
    void isCleanReturnsTrueForSuccessfulClean() {
      CloseResult result = CloseResult.successfulClose(1L, 0L, 0L, 0L, 0L);
      assertTrue(result.isClean());
    }

    @Test
    void isCleanReturnsFalseForFailure() {
      CloseResult result = CloseResult.closeFailed(1L, 0L, 0L, "Failed");
      assertFalse(result.isClean());
    }

    @Test
    void hadErrorsReturnsFalseForSuccess() {
      CloseResult result = CloseResult.successfulClose(1L, 0L, 0L, 0L, 0L);
      assertFalse(result.hadErrors());
    }

    @Test
    void hadErrorsReturnsTrueForFailure() {
      CloseResult result = CloseResult.closeFailed(1L, 0L, 0L, "Failed");
      assertTrue(result.hadErrors());
    }

    @Test
    void canSafelyReopenReturnsTrueForSuccess() {
      CloseResult result = CloseResult.successfulClose(1L, 0L, 0L, 0L, 0L);
      assertTrue(result.canSafelyReopen());
    }

    @Test
    void canSafelyReopenReturnsFalseForNoSegments() {
      CloseResult result = CloseResult.successfulClose(0L, 0L, 0L, 0L, 0L);
      assertFalse(result.canSafelyReopen());
    }
  }
}
