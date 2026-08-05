package io.writeahead.log.models;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.results.TruncateResult;
import org.junit.jupiter.api.Test;

class TruncateResultTest {

  @Test
  void successfulTruncate_createsCorrectState() {
    TruncateResult result = TruncateResult.successfulTruncate(5, 10);

    assertTrue(result.success());
    assertEquals(5, result.segmentsRemoved());
    assertEquals(10, result.oldestRemainingSegmentSequence());
    assertFalse(result.corruptionDetected());
    assertNull(result.errorMessage());
  }

  @Test
  void nothingToTruncate_createsCorrectState() {
    TruncateResult result = TruncateResult.nothingToTruncate(5);

    assertTrue(result.success());
    assertEquals(0, result.segmentsRemoved());
    assertEquals(5, result.oldestRemainingSegmentSequence());
    assertFalse(result.corruptionDetected());
    assertNull(result.errorMessage());
  }

  @Test
  void truncationFailed_createsErrorState() {
    String errorMsg = "Failed to delete segment file";
    TruncateResult result = TruncateResult.truncationFailed(3, errorMsg);

    assertFalse(result.success());
    assertEquals(0, result.segmentsRemoved());
    assertEquals(3, result.oldestRemainingSegmentSequence());
    assertFalse(result.corruptionDetected());
    assertEquals(errorMsg, result.errorMessage());
  }

  @Test
  void truncationFailed_throwsOnNullErrorMessage() {
    assertThrows(IllegalArgumentException.class, () -> TruncateResult.truncationFailed(3, null));
  }

  @Test
  void truncationFailed_throwsOnEmptyErrorMessage() {
    assertThrows(IllegalArgumentException.class, () -> TruncateResult.truncationFailed(3, ""));
  }

  @Test
  void corruptionDetectedDuringTruncate_createsCorruptionState() {
    String errorMsg = "CRC mismatch on segment 5";
    TruncateResult result = TruncateResult.corruptionDetectedDuringTruncate(6, errorMsg);

    assertFalse(result.success());
    assertEquals(0, result.segmentsRemoved());
    assertEquals(6, result.oldestRemainingSegmentSequence());
    assertTrue(result.corruptionDetected());
    assertEquals(errorMsg, result.errorMessage());
  }

  @Test
  void corruptionDetectedDuringTruncate_throwsOnNullErrorMessage() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TruncateResult.corruptionDetectedDuringTruncate(3, null));
  }

  @Test
  void corruptionDetectedDuringTruncate_throwsOnEmptyErrorMessage() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TruncateResult.corruptionDetectedDuringTruncate(3, ""));
  }

  @Test
  void didRemoveSegments_returnsTrue_whenSuccessAndSegmentsRemoved() {
    TruncateResult result = TruncateResult.successfulTruncate(5, 10);
    assertTrue(result.didRemoveSegments());
  }

  @Test
  void didRemoveSegments_returnsFalse_whenSuccessButNothingRemoved() {
    TruncateResult result = TruncateResult.nothingToTruncate(5);
    assertFalse(result.didRemoveSegments());
  }

  @Test
  void didRemoveSegments_returnsFalse_whenFailed() {
    TruncateResult result = TruncateResult.truncationFailed(3, "error");
    assertFalse(result.didRemoveSegments());
  }

  @Test
  void isErrorState_returnsTrue_whenFailed() {
    TruncateResult errorResult = TruncateResult.truncationFailed(3, "error");
    assertTrue(errorResult.isErrorState());
  }

  @Test
  void isErrorState_returnsTrue_whenCorruptionDetected() {
    TruncateResult corruptionResult =
        TruncateResult.corruptionDetectedDuringTruncate(3, "corruption");
    assertTrue(corruptionResult.isErrorState());
  }

  @Test
  void isErrorState_returnsFalse_whenSuccessful() {
    TruncateResult successResult = TruncateResult.successfulTruncate(5, 10);
    assertFalse(successResult.isErrorState());
  }

  @Test
  void canContinueNormally_returnsTrue_whenSuccessfulAndNoCorruption() {
    TruncateResult result = TruncateResult.successfulTruncate(5, 10);
    assertTrue(result.canContinueNormally());
  }

  @Test
  void canContinueNormally_returnsTrue_whenNothingToTruncate() {
    TruncateResult result = TruncateResult.nothingToTruncate(5);
    assertTrue(result.canContinueNormally());
  }

  @Test
  void canContinueNormally_returnsFalse_whenFailed() {
    TruncateResult result = TruncateResult.truncationFailed(3, "error");
    assertFalse(result.canContinueNormally());
  }

  @Test
  void canContinueNormally_returnsFalse_whenCorruptionDetected() {
    TruncateResult result = TruncateResult.corruptionDetectedDuringTruncate(3, "corruption");
    assertFalse(result.canContinueNormally());
  }

  @Test
  void compactConstructor_throwsOnNegativeSegmentsRemoved() {
    assertThrows(
        IllegalArgumentException.class, () -> new TruncateResult(true, -1, 10, false, null));
  }

  @Test
  void compactConstructor_throwsOnNegativeOldestSequence() {
    assertThrows(
        IllegalArgumentException.class, () -> new TruncateResult(true, 5, -1, false, null));
  }

  @Test
  void compactConstructor_throwsOnSuccessWithCorruption() {
    assertThrows(IllegalArgumentException.class, () -> new TruncateResult(true, 5, 10, true, null));
  }

  @Test
  void compactConstructor_throwsOnSuccessWithErrorMessage() {
    assertThrows(
        IllegalArgumentException.class, () -> new TruncateResult(true, 5, 10, false, "error"));
  }

  @Test
  void compactConstructor_throwsOnFailureWithoutErrorMessage() {
    assertThrows(
        IllegalArgumentException.class, () -> new TruncateResult(false, 0, 10, false, null));
  }

  @Test
  void compactConstructor_throwsOnFailureWithEmptyErrorMessage() {
    assertThrows(IllegalArgumentException.class, () -> new TruncateResult(false, 0, 10, false, ""));
  }

  @Test
  void compactConstructor_throwsOnFailureWithSegmentsRemoved() {
    assertThrows(
        IllegalArgumentException.class, () -> new TruncateResult(false, 5, 10, false, "error"));
  }

  @Test
  void successfulTruncate_zeroSegments() {
    TruncateResult result = TruncateResult.successfulTruncate(0, 5);
    assertTrue(result.success());
    assertEquals(0, result.segmentsRemoved());
    assertFalse(result.didRemoveSegments());
  }

  @Test
  void successfulTruncate_largeNumberOfSegments() {
    TruncateResult result = TruncateResult.successfulTruncate(10000, 10001);
    assertTrue(result.success());
    assertEquals(10000, result.segmentsRemoved());
    assertTrue(result.didRemoveSegments());
  }

  @Test
  void oldestRemainingSequence_preserved() {
    long[] sequences = {0, 1, 5, 100, 1000};

    for (long seq : sequences) {
      TruncateResult result = TruncateResult.nothingToTruncate(seq);
      assertEquals(seq, result.oldestRemainingSegmentSequence());
    }
  }

  @Test
  void errorMessages_preserved() {
    String[] messages = {"File not found", "Permission denied", "Disk full", "I/O error occurred"};

    for (String msg : messages) {
      TruncateResult result = TruncateResult.truncationFailed(1, msg);
      assertEquals(msg, result.errorMessage());
    }
  }

  @Test
  void immutability_recordIsUnmodifiable() {
    TruncateResult result = TruncateResult.successfulTruncate(5, 10);

    assertEquals(5, result.segmentsRemoved());
    assertEquals(10, result.oldestRemainingSegmentSequence());
    // Records have no setters (compile-time safety)
  }

  @Test
  void allFactories_createValidObjects() {
    // All four factories should create valid objects via constructor
    TruncateResult r1 = TruncateResult.successfulTruncate(5, 10);
    TruncateResult r2 = TruncateResult.nothingToTruncate(5);
    TruncateResult r3 = TruncateResult.truncationFailed(3, "error");
    TruncateResult r4 = TruncateResult.corruptionDetectedDuringTruncate(3, "corruption");

    assertTrue(r1.success());
    assertTrue(r2.success());
    assertFalse(r3.success());
    assertFalse(r4.success());
  }

  @Test
  void successVsFailure_stateComparison() {
    TruncateResult success = TruncateResult.successfulTruncate(10, 20);
    TruncateResult failure = TruncateResult.truncationFailed(20, "failed");

    assertTrue(success.success());
    assertFalse(failure.success());

    assertFalse(success.isErrorState());
    assertTrue(failure.isErrorState());

    assertTrue(success.canContinueNormally());
    assertFalse(failure.canContinueNormally());
  }

  @Test
  void normalVsCorruption_stateComparison() {
    TruncateResult normal = TruncateResult.successfulTruncate(10, 20);
    TruncateResult corrupted = TruncateResult.corruptionDetectedDuringTruncate(20, "corruption");

    assertFalse(normal.corruptionDetected());
    assertTrue(corrupted.corruptionDetected());

    assertTrue(normal.canContinueNormally());
    assertFalse(corrupted.canContinueNormally());
  }

  @Test
  void segmentsRemovedProgression() {
    long[] removedCounts = {0, 1, 5, 10, 100, 1000};

    for (long count : removedCounts) {
      TruncateResult result = TruncateResult.successfulTruncate(count, count + 1);

      if (count == 0) {
        assertFalse(result.didRemoveSegments());
      } else {
        assertTrue(result.didRemoveSegments());
      }

      assertEquals(count, result.segmentsRemoved());
    }
  }

  @Test
  void queryMethodConsistency_successful() {
    TruncateResult result = TruncateResult.successfulTruncate(5, 10);

    assertTrue(result.success());
    assertFalse(result.isErrorState());
    assertTrue(result.canContinueNormally());
    assertTrue(result.didRemoveSegments());
    assertFalse(result.corruptionDetected());
    assertNull(result.errorMessage());
  }

  @Test
  void queryMethodConsistency_nothingToTruncate() {
    TruncateResult result = TruncateResult.nothingToTruncate(5);

    assertTrue(result.success());
    assertFalse(result.isErrorState());
    assertTrue(result.canContinueNormally());
    assertFalse(result.didRemoveSegments());
    assertFalse(result.corruptionDetected());
    assertNull(result.errorMessage());
  }

  @Test
  void queryMethodConsistency_failed() {
    TruncateResult result = TruncateResult.truncationFailed(3, "error");

    assertFalse(result.success());
    assertTrue(result.isErrorState());
    assertFalse(result.canContinueNormally());
    assertFalse(result.didRemoveSegments());
    assertFalse(result.corruptionDetected());
    assertNotNull(result.errorMessage());
  }

  @Test
  void queryMethodConsistency_corrupted() {
    TruncateResult result = TruncateResult.corruptionDetectedDuringTruncate(3, "corruption");

    assertFalse(result.success());
    assertTrue(result.isErrorState());
    assertFalse(result.canContinueNormally());
    assertFalse(result.didRemoveSegments());
    assertTrue(result.corruptionDetected());
    assertNotNull(result.errorMessage());
  }
}
