package io.writeahead.log.models;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.results.CloseResult;
import org.junit.jupiter.api.Test;

class CloseResultTest {

  @Test
  void successfulClose_createsCorrectState() {
    CloseResult result = CloseResult.successfulClose(5, 1, 5, 500, 51200);

    assertTrue(result.success());
    assertEquals(5, result.totalSegmentsAtClose());
    assertEquals(1, result.oldestSegmentSequence());
    assertEquals(5, result.newestSegmentSequence());
    assertEquals(500, result.totalEntriesPersisted());
    assertEquals(51200, result.totalBytesPersisted());
    assertFalse(result.hasUnflushedEntries());
    assertFalse(result.corruptionDetected());
    assertNull(result.errorMessage());
  }

  @Test
  void closeWithUnflushedEntries_createsCorrectState() {
    String errorMsg = "Unflushed entries in batch";
    CloseResult result = CloseResult.closeWithUnflushedEntries(5, 1, 5, 400, 40960, errorMsg);

    assertFalse(result.success());
    assertEquals(5, result.totalSegmentsAtClose());
    assertEquals(400, result.totalEntriesPersisted());
    assertTrue(result.hasUnflushedEntries());
    assertFalse(result.corruptionDetected());
    assertEquals(errorMsg, result.errorMessage());
  }

  @Test
  void closeWithUnflushedEntries_throwsOnNullErrorMessage() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CloseResult.closeWithUnflushedEntries(5, 1, 5, 400, 40960, null));
  }

  @Test
  void closeWithUnflushedEntries_throwsOnEmptyErrorMessage() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CloseResult.closeWithUnflushedEntries(5, 1, 5, 400, 40960, ""));
  }

  @Test
  void closeFailed_createsCorrectState() {
    String errorMsg = "Failed to finalize segment";
    CloseResult result = CloseResult.closeFailed(5, 1, 5, errorMsg);

    assertFalse(result.success());
    assertEquals(5, result.totalSegmentsAtClose());
    assertEquals(1, result.oldestSegmentSequence());
    assertEquals(5, result.newestSegmentSequence());
    assertEquals(0, result.totalEntriesPersisted());
    assertEquals(0, result.totalBytesPersisted());
    assertFalse(result.hasUnflushedEntries());
    assertFalse(result.corruptionDetected());
    assertEquals(errorMsg, result.errorMessage());
  }

  @Test
  void closeFailed_throwsOnNullErrorMessage() {
    assertThrows(IllegalArgumentException.class, () -> CloseResult.closeFailed(5, 1, 5, null));
  }

  @Test
  void closeFailed_throwsOnEmptyErrorMessage() {
    assertThrows(IllegalArgumentException.class, () -> CloseResult.closeFailed(5, 1, 5, ""));
  }

  @Test
  void corruptionDetectedAtClose_createsCorrectState() {
    String errorMsg = "CRC mismatch at close";
    CloseResult result = CloseResult.corruptionDetectedAtClose(5, errorMsg);

    assertFalse(result.success());
    assertEquals(5, result.totalSegmentsAtClose());
    assertEquals(0, result.oldestSegmentSequence());
    assertEquals(0, result.newestSegmentSequence());
    assertEquals(0, result.totalEntriesPersisted());
    assertEquals(0, result.totalBytesPersisted());
    assertFalse(result.hasUnflushedEntries());
    assertTrue(result.corruptionDetected());
    assertEquals(errorMsg, result.errorMessage());
  }

  @Test
  void corruptionDetectedAtClose_throwsOnNullErrorMessage() {
    assertThrows(
        IllegalArgumentException.class, () -> CloseResult.corruptionDetectedAtClose(5, null));
  }

  @Test
  void corruptionDetectedAtClose_throwsOnEmptyErrorMessage() {
    assertThrows(
        IllegalArgumentException.class, () -> CloseResult.corruptionDetectedAtClose(5, ""));
  }

  @Test
  void isClean_returnsTrue_whenSuccessfulAndNoIssues() {
    CloseResult result = CloseResult.successfulClose(5, 1, 5, 500, 51200);
    assertTrue(result.isClean());
  }

  @Test
  void isClean_returnsFalse_whenHasUnflushedEntries() {
    CloseResult result = CloseResult.closeWithUnflushedEntries(5, 1, 5, 400, 40960, "unflushed");
    assertFalse(result.isClean());
  }

  @Test
  void isClean_returnsFalse_whenCorruptionDetected() {
    CloseResult result = CloseResult.corruptionDetectedAtClose(5, "corruption");
    assertFalse(result.isClean());
  }

  @Test
  void isClean_returnsFalse_whenFailed() {
    CloseResult result = CloseResult.closeFailed(5, 1, 5, "error");
    assertFalse(result.isClean());
  }

  @Test
  void hadErrors_returnsTrue_whenFailed() {
    CloseResult result = CloseResult.closeFailed(5, 1, 5, "error");
    assertTrue(result.hadErrors());
  }

  @Test
  void hadErrors_returnsTrue_whenHasUnflushedEntries() {
    CloseResult result = CloseResult.closeWithUnflushedEntries(5, 1, 5, 400, 40960, "unflushed");
    assertTrue(result.hadErrors());
  }

  @Test
  void hadErrors_returnsTrue_whenCorruptionDetected() {
    CloseResult result = CloseResult.corruptionDetectedAtClose(5, "corruption");
    assertTrue(result.hadErrors());
  }

  @Test
  void hadErrors_returnsFalse_whenSuccessful() {
    CloseResult result = CloseResult.successfulClose(5, 1, 5, 500, 51200);
    assertFalse(result.hadErrors());
  }

  @Test
  void canSafelyReopen_returnsTrue_whenSuccessfulWithSegments() {
    CloseResult result = CloseResult.successfulClose(5, 1, 5, 500, 51200);
    assertTrue(result.canSafelyReopen());
  }

  @Test
  void canSafelyReopen_returnsFalse_whenNoSegments() {
    CloseResult result = CloseResult.successfulClose(0, 0, 0, 0, 0);
    assertFalse(result.canSafelyReopen());
  }

  @Test
  void canSafelyReopen_returnsFalse_whenHasUnflushedEntries() {
    CloseResult result = CloseResult.closeWithUnflushedEntries(5, 1, 5, 400, 40960, "unflushed");
    assertFalse(result.canSafelyReopen());
  }

  @Test
  void canSafelyReopen_returnsFalse_whenFailed() {
    CloseResult result = CloseResult.closeFailed(5, 1, 5, "error");
    assertFalse(result.canSafelyReopen());
  }

  @Test
  void canSafelyReopen_returnsFalse_whenCorruptionDetected() {
    CloseResult result = CloseResult.corruptionDetectedAtClose(5, "corruption");
    assertFalse(result.canSafelyReopen());
  }

  @Test
  void compactConstructor_throwsOnNegativeTotalSegments() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CloseResult(true, -1, 0, 0, 0, 0, false, false, null));
  }

  @Test
  void compactConstructor_throwsOnNegativeOldestSequence() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CloseResult(true, 5, -1, 5, 0, 0, false, false, null));
  }

  @Test
  void compactConstructor_throwsOnNegativeNewestSequence() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CloseResult(true, 5, 1, -1, 0, 0, false, false, null));
  }

  @Test
  void compactConstructor_throwsOnNegativeEntriesPersisted() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CloseResult(true, 5, 1, 5, -1, 0, false, false, null));
  }

  @Test
  void compactConstructor_throwsOnNegativeBytesPersisted() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CloseResult(true, 5, 1, 5, 0, -1, false, false, null));
  }

  @Test
  void compactConstructor_throwsOnZeroSegmentsButNonZeroOldest() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CloseResult(true, 0, 1, 0, 0, 0, false, false, null));
  }

  @Test
  void compactConstructor_throwsOnZeroSegmentsButNonZeroNewest() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CloseResult(true, 0, 0, 1, 0, 0, false, false, null));
  }

  @Test
  void compactConstructor_throwsOnOldestGreaterThanNewest() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CloseResult(true, 5, 5, 1, 0, 0, false, false, null));
  }

  @Test
  void compactConstructor_throwsOnSuccessWithUnflushedEntries() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CloseResult(true, 5, 1, 5, 0, 0, true, false, null));
  }

  @Test
  void compactConstructor_throwsOnSuccessWithErrorMessage() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CloseResult(true, 5, 1, 5, 0, 0, false, false, "error"));
  }

  @Test
  void compactConstructor_throwsOnSuccessWithCorruption() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CloseResult(true, 5, 1, 5, 0, 0, false, true, null));
  }

  @Test
  void compactConstructor_throwsOnFailureWithoutErrorMessage() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CloseResult(false, 5, 1, 5, 0, 0, false, false, null));
  }

  @Test
  void compactConstructor_throwsOnFailureWithEmptyErrorMessage() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CloseResult(false, 5, 1, 5, 0, 0, false, false, ""));
  }

  @Test
  void successfulClose_zeroSegments() {
    CloseResult result = CloseResult.successfulClose(0, 0, 0, 0, 0);
    assertTrue(result.success());
    assertEquals(0, result.totalSegmentsAtClose());
    assertFalse(result.canSafelyReopen());
  }

  @Test
  void successfulClose_largeNumbers() {
    CloseResult result = CloseResult.successfulClose(10000, 1, 10000, 1000000, 1024000000);
    assertTrue(result.success());
    assertEquals(10000, result.totalSegmentsAtClose());
    assertEquals(1000000, result.totalEntriesPersisted());
    assertEquals(1024000000, result.totalBytesPersisted());
    assertTrue(result.canSafelyReopen());
  }

  @Test
  void errorMessages_preserved() {
    String[] messages = {
      "Failed to close segment",
      "I/O error during close",
      "Corrupted segment detected",
      "Permission denied"
    };

    for (String msg : messages) {
      CloseResult result = CloseResult.closeFailed(5, 1, 5, msg);
      assertEquals(msg, result.errorMessage());
    }
  }

  @Test
  void immutability_recordIsUnmodifiable() {
    CloseResult result = CloseResult.successfulClose(5, 1, 5, 500, 51200);

    assertEquals(5, result.totalSegmentsAtClose());
    assertEquals(500, result.totalEntriesPersisted());
    // Records have no setters (compile-time safety)
  }

  @Test
  void allFactories_createValidObjects() {
    CloseResult r1 = CloseResult.successfulClose(5, 1, 5, 500, 51200);
    CloseResult r2 = CloseResult.closeWithUnflushedEntries(5, 1, 5, 400, 40960, "unflushed");
    CloseResult r3 = CloseResult.closeFailed(5, 1, 5, "error");
    CloseResult r4 = CloseResult.corruptionDetectedAtClose(5, "corruption");

    assertTrue(r1.success());
    assertFalse(r2.success());
    assertFalse(r3.success());
    assertFalse(r4.success());
  }

  @Test
  void queryMethodConsistency_successful() {
    CloseResult result = CloseResult.successfulClose(5, 1, 5, 500, 51200);

    assertTrue(result.success());
    assertFalse(result.hadErrors());
    assertTrue(result.isClean());
    assertTrue(result.canSafelyReopen());
    assertFalse(result.hasUnflushedEntries());
    assertFalse(result.corruptionDetected());
  }

  @Test
  void queryMethodConsistency_unflushed() {
    CloseResult result = CloseResult.closeWithUnflushedEntries(5, 1, 5, 400, 40960, "unflushed");

    assertFalse(result.success());
    assertTrue(result.hadErrors());
    assertFalse(result.isClean());
    assertFalse(result.canSafelyReopen());
    assertTrue(result.hasUnflushedEntries());
    assertFalse(result.corruptionDetected());
  }

  @Test
  void queryMethodConsistency_failed() {
    CloseResult result = CloseResult.closeFailed(5, 1, 5, "error");

    assertFalse(result.success());
    assertTrue(result.hadErrors());
    assertFalse(result.isClean());
    assertFalse(result.canSafelyReopen());
    assertFalse(result.hasUnflushedEntries());
    assertFalse(result.corruptionDetected());
  }

  @Test
  void queryMethodConsistency_corrupted() {
    CloseResult result = CloseResult.corruptionDetectedAtClose(5, "corruption");

    assertFalse(result.success());
    assertTrue(result.hadErrors());
    assertFalse(result.isClean());
    assertFalse(result.canSafelyReopen());
    assertFalse(result.hasUnflushedEntries());
    assertTrue(result.corruptionDetected());
  }

  @Test
  void segmentSequenceRange_validProgression() {
    for (int oldest = 1; oldest <= 5; oldest++) {
      for (int newest = oldest; newest <= 10; newest++) {
        CloseResult result =
            CloseResult.successfulClose(newest - oldest + 1, oldest, newest, 100, 10240);
        assertEquals(oldest, result.oldestSegmentSequence());
        assertEquals(newest, result.newestSegmentSequence());
      }
    }
  }

  @Test
  void entriesAndBytes_progression() {
    long[] entryCounts = {0, 100, 500, 1000, 10000};
    long[] byteCounts = {0, 10240, 51200, 102400, 1024000};

    for (int i = 0; i < entryCounts.length; i++) {
      CloseResult result = CloseResult.successfulClose(5, 1, 5, entryCounts[i], byteCounts[i]);
      assertEquals(entryCounts[i], result.totalEntriesPersisted());
      assertEquals(byteCounts[i], result.totalBytesPersisted());
    }
  }
}
