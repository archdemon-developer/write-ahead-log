package io.writeahead.log.models.results;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TruncateResult Tests — 100% Validation Coverage")
class TruncateResultTest {

  @Nested
  @DisplayName("Compact Constructor Validation")
  class ConstructorValidation {

    @Test
    void rejectsSegmentsRemovedNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new TruncateResult(true, -1L, 0L, false, null));
      assertTrue(ex.getMessage().contains("segmentsRemoved cannot be negative"));
    }

    @Test
    void rejectsOldestRemainingSequenceNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new TruncateResult(true, 0L, -1L, false, null));
      assertTrue(ex.getMessage().contains("oldestRemainingSegmentSequence cannot be negative"));
    }

    @Test
    void rejectsSuccessWithCorruptionDetected() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new TruncateResult(true, 0L, 0L, true, null));
      assertTrue(ex.getMessage().contains("corruptionDetected must be false"));
    }

    @Test
    void rejectsSuccessWithErrorMessage() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new TruncateResult(true, 0L, 0L, false, "Error"));
      assertTrue(ex.getMessage().contains("errorMessage must be null"));
    }

    @Test
    void rejectsFailureWithNullErrorMessage() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new TruncateResult(false, 0L, 0L, false, null));
      assertTrue(ex.getMessage().contains("errorMessage must be non-null"));
    }

    @Test
    void rejectsFailureWithSegmentsRemoved() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new TruncateResult(false, 5L, 0L, false, "Error"));
      assertTrue(ex.getMessage().contains("segmentsRemoved must be 0"));
    }

    @Test
    void acceptsValidSuccessfulTruncate() {
      TruncateResult result = new TruncateResult(true, 5L, 10L, false, null);
      assertTrue(result.success());
    }
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    void successfulTruncateCreatesCorrectResult() {
      TruncateResult result = TruncateResult.successfulTruncate(5L, 10L);
      assertTrue(result.success());
      assertEquals(5L, result.segmentsRemoved());
    }

    @Test
    void nothingToTruncateCreatesCorrectResult() {
      TruncateResult result = TruncateResult.nothingToTruncate(0L);
      assertTrue(result.success());
      assertEquals(0L, result.segmentsRemoved());
    }

    @Test
    void truncationFailedRejectsNullMessage() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> TruncateResult.truncationFailed(0L, null));
      assertTrue(ex.getMessage().contains("errorMessage"));
    }

    @Test
    void truncationFailedCreatesCorrectResult() {
      TruncateResult result = TruncateResult.truncationFailed(0L, "Failed");
      assertFalse(result.success());
    }

    @Test
    void corruptionDetectedDuringTruncateRejectsNullMessage() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> TruncateResult.corruptionDetectedDuringTruncate(0L, null));
      assertTrue(ex.getMessage().contains("errorMessage"));
    }

    @Test
    void corruptionDetectedDuringTruncateCreatesCorrectResult() {
      TruncateResult result = TruncateResult.corruptionDetectedDuringTruncate(0L, "Corrupted");
      assertFalse(result.success());
      assertTrue(result.corruptionDetected());
    }
  }

  @Nested
  @DisplayName("Helper Methods")
  class HelperMethods {

    @Test
    void didRemoveSegmentsReturnsTrueForSuccessWithRemovals() {
      TruncateResult result = TruncateResult.successfulTruncate(5L, 10L);
      assertTrue(result.didRemoveSegments());
    }

    @Test
    void didRemoveSegmentsReturnsFalseForNoRemovals() {
      TruncateResult result = TruncateResult.nothingToTruncate(0L);
      assertFalse(result.didRemoveSegments());
    }

    @Test
    void isErrorStateReturnsFalseForSuccess() {
      TruncateResult result = TruncateResult.successfulTruncate(0L, 0L);
      assertFalse(result.isErrorState());
    }

    @Test
    void isErrorStateReturnsTrueForFailure() {
      TruncateResult result = TruncateResult.truncationFailed(0L, "Failed");
      assertTrue(result.isErrorState());
    }

    @Test
    void canContinueNormallyReturnsTrueForSuccess() {
      TruncateResult result = TruncateResult.successfulTruncate(0L, 0L);
      assertTrue(result.canContinueNormally());
    }

    @Test
    void canContinueNormallyReturnsFalseForCorruption() {
      TruncateResult result = TruncateResult.corruptionDetectedDuringTruncate(0L, "Error");
      assertFalse(result.canContinueNormally());
    }
  }
}
