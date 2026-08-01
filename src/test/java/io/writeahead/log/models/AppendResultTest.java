package io.writeahead.log.models;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AppendResultTest {

  private static final long maxSegmentSize = 1048576; // 1 MB

  @Test
  void successfulAppendNoFlush_createsCorrectResult() {
    AppendResult result = AppendResult.successfulAppendNoFlush(3, 5, 100, 10240, 1000L, 5000L);

    assertFalse(result.flushed());
    assertEquals(3, result.entriesPendingInBatch());
    assertEquals(5, result.currentSegmentSequenceNumber());
    assertEquals(100, result.currentSegmentEntryCount());
    assertEquals(10240, result.currentSegmentByteCount());
    assertEquals(1000L, result.currentSegmentMinTimestamp());
    assertEquals(5000L, result.currentSegmentMaxTimestamp());
    assertFalse(result.corruptionDetected());
    assertNull(result.errorMessage());
  }

  @Test
  void successfulAppendWithFlush_createsCorrectResult() {
    AppendResult result = AppendResult.successfulAppendWithFlush(7, 200, 20480, 2000L, 8000L);

    assertTrue(result.flushed());
    assertEquals(0, result.entriesPendingInBatch());
    assertEquals(7, result.currentSegmentSequenceNumber());
    assertEquals(200, result.currentSegmentEntryCount());
    assertEquals(20480, result.currentSegmentByteCount());
    assertEquals(2000L, result.currentSegmentMinTimestamp());
    assertEquals(8000L, result.currentSegmentMaxTimestamp());
    assertFalse(result.corruptionDetected());
    assertNull(result.errorMessage());
  }

  @Test
  void corruptionDetectedResult_createsErrorState() {
    String errorMsg = "CRC mismatch on entry at offset 512";
    AppendResult result = AppendResult.corruptionDetectedResult(3, 50, 5120, 500L, 3000L, errorMsg);

    assertTrue(result.corruptionDetected());
    assertEquals(errorMsg, result.errorMessage());
    assertFalse(result.flushed());
    assertEquals(0, result.entriesPendingInBatch());
    assertTrue(result.isErrorState());
    assertFalse(result.canAcceptMoreWrites());
  }

  @Test
  void corruptionDetectedResult_throwsOnNullErrorMessage() {
    assertThrows(
        IllegalArgumentException.class,
        () -> AppendResult.corruptionDetectedResult(3, 50, 5120, 500L, 3000L, null));
  }

  @Test
  void corruptionDetectedResult_throwsOnEmptyErrorMessage() {
    assertThrows(
        IllegalArgumentException.class,
        () -> AppendResult.corruptionDetectedResult(3, 50, 5120, 500L, 3000L, ""));
  }

  @Test
  void isDurable_returnsTrue_whenFlushed() {
    AppendResult flushedResult = AppendResult.successfulAppendWithFlush(1, 10, 1024, 100, 200);
    AppendResult unflushedResult = AppendResult.successfulAppendNoFlush(5, 1, 10, 512, 100, 200);

    assertTrue(flushedResult.isDurable());
    assertFalse(unflushedResult.isDurable());
  }

  @Test
  void isErrorState_returnsTrue_whenCorrupted() {
    AppendResult errorResult =
        AppendResult.corruptionDetectedResult(1, 10, 1024, 100, 200, "corruption detected");
    AppendResult successResult = AppendResult.successfulAppendWithFlush(1, 10, 1024, 100, 200);

    assertTrue(errorResult.isErrorState());
    assertFalse(successResult.isErrorState());
  }

  @Test
  void canAcceptMoreWrites_returnsFalse_whenError() {
    AppendResult errorResult =
        AppendResult.corruptionDetectedResult(1, 10, 1024, 100, 200, "error");
    AppendResult successResult = AppendResult.successfulAppendWithFlush(1, 10, 1024, 100, 200);

    assertFalse(errorResult.canAcceptMoreWrites());
    assertTrue(successResult.canAcceptMoreWrites());
  }

  @Test
  void canAcceptMoreWrites_returnsTrue_whenNoError() {
    AppendResult result1 = AppendResult.successfulAppendNoFlush(3, 1, 10, 512, 100, 200);
    AppendResult result2 = AppendResult.successfulAppendWithFlush(1, 10, 1024, 100, 200);

    assertTrue(result1.canAcceptMoreWrites());
    assertTrue(result2.canAcceptMoreWrites());
  }

  @Test
  void estimateRotationRiskPercent_returnsZero_whenZeroByteCount() {
    AppendResult result = AppendResult.successfulAppendNoFlush(0, 1, 10, 48, 100, 200);
    assertEquals(0, result.estimateRotationRiskPercent(maxSegmentSize));
  }

  @Test
  void estimateRotationRiskPercent_returnsAccuratePercentage() {
    // 25% utilization: 262144 / 1048576 = 0.25
    AppendResult result1 = AppendResult.successfulAppendWithFlush(1, 10, 262144, 100, 200);
    int percent1 = result1.estimateRotationRiskPercent(maxSegmentSize);
    assertTrue(percent1 >= 20 && percent1 <= 30, "Expected ~25%, got " + percent1);

    AppendResult result2 = AppendResult.successfulAppendWithFlush(1, 10, 524288, 100, 200);
    int percent2 = result2.estimateRotationRiskPercent(maxSegmentSize);
    assertTrue(percent2 >= 45 && percent2 <= 55, "Expected ~50%, got " + percent2);

    AppendResult result3 = AppendResult.successfulAppendWithFlush(1, 10, 943718, 100, 200);
    int percent3 = result3.estimateRotationRiskPercent(maxSegmentSize);
    assertTrue(percent3 >= 85 && percent3 <= 95, "Expected ~90%, got " + percent3);
  }

  @Test
  void estimateRotationRiskPercent_capsCapped_at100Percent() {
    AppendResult result = AppendResult.successfulAppendWithFlush(1, 10, 2000000, 100, 200);
    int percent = result.estimateRotationRiskPercent(maxSegmentSize);
    assertEquals(100, percent);
  }

  @Test
  void estimateRotationRiskPercent_handlesSmallSegmentSizes() {
    long smallMaxSize = 1024;
    AppendResult result = AppendResult.successfulAppendWithFlush(1, 10, 512, 100, 200);
    int percent = result.estimateRotationRiskPercent(smallMaxSize);
    assertTrue(percent >= 45 && percent <= 55, "Expected ~50%, got " + percent);
  }

  @Test
  void immutability_recordIsUnmodifiable() {
    AppendResult result = AppendResult.successfulAppendWithFlush(1, 10, 1024, 100, 200);

    assertTrue(result.flushed());
    assertEquals(1, result.currentSegmentSequenceNumber());
    assertEquals(10, result.currentSegmentEntryCount());
  }

  @Test
  void flushAndNoFlush_bothValidStates() {
    AppendResult noFlush = AppendResult.successfulAppendNoFlush(5, 1, 100, 10240, 500, 5000);
    AppendResult withFlush = AppendResult.successfulAppendWithFlush(1, 100, 10240, 500, 5000);

    assertEquals(5, noFlush.entriesPendingInBatch());
    assertFalse(noFlush.isDurable());

    assertEquals(0, withFlush.entriesPendingInBatch());
    assertTrue(withFlush.isDurable());
  }

  @Test
  void errorResult_neverDurable_neverAcceptsWrites() {
    AppendResult errorResult =
        AppendResult.corruptionDetectedResult(1, 50, 5120, 100, 1000, "CRC mismatch");

    assertFalse(errorResult.isDurable());
    assertFalse(errorResult.canAcceptMoreWrites());
    assertTrue(errorResult.isErrorState());
    assertEquals(0, errorResult.entriesPendingInBatch());
  }

  @Test
  void segmentState_trackedAcrossResults() {
    // Simulate a write sequence and verify state tracking
    AppendResult result1 = AppendResult.successfulAppendNoFlush(2, 1, 10, 1024, 100, 500);
    assertEquals(1, result1.currentSegmentSequenceNumber());
    assertEquals(10, result1.currentSegmentEntryCount());

    // Segment fills up, need to rotate
    AppendResult result2 = AppendResult.successfulAppendWithFlush(2, 400, 1000000, 50, 5000);
    assertEquals(2, result2.currentSegmentSequenceNumber());
    assertEquals(400, result2.currentSegmentEntryCount());

    // Verify rotation risk
    int riskPercent = result2.estimateRotationRiskPercent(1048576);
    assertTrue(riskPercent >= 90 && riskPercent <= 100);
  }

  @Test
  void errorMessage_preservedInCorruptionResult() {
    String[] errorMessages = {
      "Header CRC mismatch",
      "Footer validation failed",
      "Entry payload corrupted",
      "Magic byte invalid at offset 0"
    };

    for (String errorMsg : errorMessages) {
      AppendResult result = AppendResult.corruptionDetectedResult(1, 50, 5120, 100, 1000, errorMsg);
      assertEquals(errorMsg, result.errorMessage());
      assertTrue(result.isErrorState());
    }
  }

  @Test
  void successfulAppendNoFlush_withLargeBatchSize() {
    int largeBatch = 10000;
    AppendResult result =
        AppendResult.successfulAppendNoFlush(largeBatch, 1, 500, 50000, 100, 5000);

    assertEquals(largeBatch, result.entriesPendingInBatch());
    assertFalse(result.flushed());
    assertTrue(result.canAcceptMoreWrites());
  }

  @Test
  void successfulAppendWithFlush_clearsAllPendingState() {
    AppendResult result = AppendResult.successfulAppendWithFlush(5, 250, 30000, 1000, 9000);

    // Flush implies all writes hit disk and batch is clear
    assertTrue(result.isDurable());
    assertEquals(0, result.entriesPendingInBatch());
    assertFalse(result.corruptionDetected());
    assertNull(result.errorMessage());
  }

  @Test
  void timestampRange_preserved_inAllResults() {
    long minTime = 1000L;
    long maxTime = 9999L;

    AppendResult result1 = AppendResult.successfulAppendNoFlush(2, 1, 100, 5120, minTime, maxTime);
    AppendResult result2 = AppendResult.successfulAppendWithFlush(1, 100, 5120, minTime, maxTime);
    AppendResult result3 =
        AppendResult.corruptionDetectedResult(1, 100, 5120, minTime, maxTime, "error");

    assertEquals(minTime, result1.currentSegmentMinTimestamp());
    assertEquals(maxTime, result1.currentSegmentMaxTimestamp());

    assertEquals(minTime, result2.currentSegmentMinTimestamp());
    assertEquals(maxTime, result2.currentSegmentMaxTimestamp());

    assertEquals(minTime, result3.currentSegmentMinTimestamp());
    assertEquals(maxTime, result3.currentSegmentMaxTimestamp());
  }

  @Test
  void rotationRiskPercent_incremental_asSegmentFills() {
    long maxSize = 1048576;
    long[] byteProgression = {104857, 262144, 524288, 786432, 943718, 1048576};

    for (long bytes : byteProgression) {
      AppendResult result = AppendResult.successfulAppendWithFlush(1, 100, bytes, 100, 5000);
      int riskPercent = result.estimateRotationRiskPercent(maxSize);
      double expectedPercent = ((double) bytes / maxSize) * 100;
      assertTrue(
          riskPercent >= (expectedPercent - 5) && riskPercent <= (expectedPercent + 5),
          "Byte count " + bytes + " should be ~" + expectedPercent + "%, got " + riskPercent);
    }
  }
}
