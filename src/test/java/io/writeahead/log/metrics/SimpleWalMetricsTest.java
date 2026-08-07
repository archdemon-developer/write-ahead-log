package io.writeahead.log.metrics;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.exceptions.CorruptionType;
import io.writeahead.log.enums.exceptions.ErrorContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SimpleWalMetrics - Metrics Recording and Querying")
public class SimpleWalMetricsTest {

  private SimpleWalMetrics metrics;

  @BeforeEach
  void setUp() {
    metrics = new SimpleWalMetrics();
  }

  @Test
  @DisplayName("recordEntryAppended increments entries and updates bytes")
  void testRecordEntryAppended() {
    metrics.recordEntryAppended(100);

    assertEquals(1, metrics.getEntriesWritten());
    assertEquals(100, metrics.getBytesWritten());
  }

  @Test
  @DisplayName("recordEntryAppended handles multiple entries")
  void testRecordEntryAppendedMultiple() {
    metrics.recordEntryAppended(100);
    metrics.recordEntryAppended(50);
    metrics.recordEntryAppended(75);

    assertEquals(3, metrics.getEntriesWritten());
    assertEquals(225, metrics.getBytesWritten());
  }

  @Test
  @DisplayName("recordEntryAppended handles zero size")
  void testRecordEntryAppendedZeroSize() {
    metrics.recordEntryAppended(0);

    assertEquals(1, metrics.getEntriesWritten());
    assertEquals(0, metrics.getBytesWritten());
  }

  @Test
  @DisplayName("recordEntryAppended handles large size")
  void testRecordEntryAppendedLargeSize() {
    metrics.recordEntryAppended(1_000_000);

    assertEquals(1, metrics.getEntriesWritten());
    assertEquals(1_000_000, metrics.getBytesWritten());
  }

  @Test
  @DisplayName("recordEntryWritten delegates to recordEntryAppended")
  void testRecordEntryWritten() {
    metrics.recordEntryWritten(100);

    assertEquals(1, metrics.getEntriesWritten());
    assertEquals(100, metrics.getBytesWritten());
  }

  @Test
  @DisplayName("recordFsync increments count and updates latency")
  void testRecordFsync() {
    metrics.recordFsync(50);

    assertEquals(1, metrics.getTotalFsyncs());
    assertTrue(metrics.getLastFsyncTimeMs() > 0);
  }

  @Test
  @DisplayName("recordFsync accumulates latency")
  void testRecordFsyncMultiple() {
    metrics.recordFsync(50);
    metrics.recordFsync(100);
    metrics.recordFsync(75);

    assertEquals(3, metrics.getTotalFsyncs());
  }

  @Test
  @DisplayName("recordFsync handles zero latency")
  void testRecordFsyncZeroLatency() {
    metrics.recordFsync(0);

    assertEquals(1, metrics.getTotalFsyncs());
    assertEquals(0.0, metrics.getAverageFsyncLatencyMs());
  }

  @Test
  @DisplayName("getAverageFsyncLatencyMs calculates correctly")
  void testGetAverageFsyncLatencyMs() {
    metrics.recordFsync(100);
    metrics.recordFsync(200);
    metrics.recordFsync(300);

    assertEquals(200.0, metrics.getAverageFsyncLatencyMs());
  }

  @Test
  @DisplayName("getAverageFsyncLatencyMs returns 0 when no fsyncs")
  void testGetAverageFsyncLatencyMsNoFsyncs() {
    assertEquals(0.0, metrics.getAverageFsyncLatencyMs());
  }

  @Test
  @DisplayName("recordCorruptedEntry increments count")
  void testRecordCorruptedEntry() {
    metrics.recordCorruptedEntry();

    assertEquals(1, metrics.getCorruptedEntriesDetected());
  }

  @Test
  @DisplayName("recordCorruptedEntry handles multiple")
  void testRecordCorruptedEntryMultiple() {
    metrics.recordCorruptedEntry();
    metrics.recordCorruptedEntry();
    metrics.recordCorruptedEntry();

    assertEquals(3, metrics.getCorruptedEntriesDetected());
  }

  @Test
  @DisplayName("recordSegmentRotation updates time")
  void testRecordSegmentRotation() {
    long beforeMs = System.currentTimeMillis();
    metrics.recordSegmentRotation();
    long afterMs = System.currentTimeMillis();

    long rotationTime = metrics.getLastRotationTimeMs();
    assertTrue(rotationTime >= beforeMs && rotationTime <= afterMs);
  }

  @Test
  @DisplayName("setCurrentSegmentEntryCount stores value")
  void testSetCurrentSegmentEntryCount() {
    metrics.setCurrentSegmentEntryCount(100);

    assertEquals(100, metrics.getCurrentSegmentEntryCount());
  }

  @Test
  @DisplayName("setCurrentSegmentEntryCount overwrites previous value")
  void testSetCurrentSegmentEntryCountOverwrite() {
    metrics.setCurrentSegmentEntryCount(100);
    metrics.setCurrentSegmentEntryCount(200);

    assertEquals(200, metrics.getCurrentSegmentEntryCount());
  }

  @Test
  @DisplayName("setCurrentSegmentByteCount stores value")
  void testSetCurrentSegmentByteCount() {
    metrics.setCurrentSegmentByteCount(50000);

    assertEquals(50000, metrics.getCurrentSegmentByteCount());
  }

  @Test
  @DisplayName("setTotalSegmentCount stores value")
  void testSetTotalSegmentCount() {
    metrics.setTotalSegmentCount(5);

    assertEquals(5, metrics.getSegmentCount());
  }

  @Test
  @DisplayName("setSegmentCount delegates to setTotalSegmentCount")
  void testSetSegmentCount() {
    metrics.setSegmentCount(10);

    assertEquals(10, metrics.getSegmentCount());
  }

  @Test
  @DisplayName("recordCorruptionType tracks corruption types")
  void testRecordCorruptionType() {
    metrics.recordCorruptionType(CorruptionType.ENTRY_CRC_MISMATCH);

    Map<String, Long> counts = metrics.getCorruptionTypeCounts();
    assertEquals(1, counts.get(CorruptionType.ENTRY_CRC_MISMATCH.name()));
  }

  @Test
  @DisplayName("recordCorruptionType handles multiple types")
  void testRecordCorruptionTypeMultiple() {
    metrics.recordCorruptionType(CorruptionType.ENTRY_CRC_MISMATCH);
    metrics.recordCorruptionType(CorruptionType.INVALID_FOOTER_MARKER);
    metrics.recordCorruptionType(CorruptionType.ENTRY_CRC_MISMATCH);

    Map<String, Long> counts = metrics.getCorruptionTypeCounts();
    assertEquals(2, counts.get(CorruptionType.ENTRY_CRC_MISMATCH.name()));
    assertEquals(1, counts.get(CorruptionType.INVALID_FOOTER_MARKER.name()));
  }

  @Test
  @DisplayName("recordSegmentCorruption increments count")
  void testRecordSegmentCorruption() {
    metrics.recordSegmentCorruption();

    assertEquals(1, metrics.getSegmentCorruptionCount());
  }

  @Test
  @DisplayName("recordSegmentCorruption handles multiple")
  void testRecordSegmentCorruptionMultiple() {
    metrics.recordSegmentCorruption();
    metrics.recordSegmentCorruption();

    assertEquals(2, metrics.getSegmentCorruptionCount());
  }

  @Test
  @DisplayName("recordRecoveryCompleted records all metrics")
  void testRecordRecoveryCompleted() {
    metrics.recordRecoveryCompleted(500, 10, 8);

    assertEquals(1, metrics.getRecoveryCount());
    assertEquals(500.0, metrics.getAverageRecoveryTimeMs());
    assertEquals(10, metrics.getLastRecoverySegmentsScanned());
    assertEquals(8, metrics.getLastRecoverySegmentsRecovered());
  }

  @Test
  @DisplayName("recordRecoveryCompleted handles multiple recoveries")
  void testRecordRecoveryCompletedMultiple() {
    metrics.recordRecoveryCompleted(500, 10, 8);
    metrics.recordRecoveryCompleted(300, 5, 5);

    assertEquals(2, metrics.getRecoveryCount());
    assertEquals(400.0, metrics.getAverageRecoveryTimeMs());
    assertEquals(5, metrics.getLastRecoverySegmentsScanned());
    assertEquals(5, metrics.getLastRecoverySegmentsRecovered());
  }

  @Test
  @DisplayName("getAverageRecoveryTimeMs returns 0 when no recoveries")
  void testGetAverageRecoveryTimeMsNoRecoveries() {
    assertEquals(0.0, metrics.getAverageRecoveryTimeMs());
  }

  @Test
  @DisplayName("recordFsyncRetrySuccess increments count")
  void testRecordFsyncRetrySuccess() {
    metrics.recordFsyncRetrySuccess(3);

    assertEquals(1, metrics.getFsyncRetrySuccessCount());
  }

  @Test
  @DisplayName("recordFsyncRetrySuccess handles multiple")
  void testRecordFsyncRetrySuccessMultiple() {
    metrics.recordFsyncRetrySuccess(1);
    metrics.recordFsyncRetrySuccess(2);
    metrics.recordFsyncRetrySuccess(3);

    assertEquals(3, metrics.getFsyncRetrySuccessCount());
  }

  @Test
  @DisplayName("recordFsyncTransientFailure tracks by error context")
  void testRecordFsyncTransientFailure() {
    metrics.recordFsyncTransientFailure(ErrorContext.RESOURCE_BUSY);

    Map<String, Long> counts = metrics.getFsyncTransientErrorCounts();
    assertEquals(1, counts.get(ErrorContext.RESOURCE_BUSY.name()));
  }

  @Test
  @DisplayName("recordFsyncTransientFailure handles multiple contexts")
  void testRecordFsyncTransientFailureMultiple() {
    metrics.recordFsyncTransientFailure(ErrorContext.RESOURCE_BUSY);
    metrics.recordFsyncTransientFailure(ErrorContext.NO_MEMORY);
    metrics.recordFsyncTransientFailure(ErrorContext.RESOURCE_BUSY);

    Map<String, Long> counts = metrics.getFsyncTransientErrorCounts();
    assertEquals(2, counts.get(ErrorContext.RESOURCE_BUSY.name()));
    assertEquals(1, counts.get(ErrorContext.NO_MEMORY.name()));
  }

  @Test
  @DisplayName("recordFsyncPermanentFailure tracks by error context")
  void testRecordFsyncPermanentFailure() {
    metrics.recordFsyncPermanentFailure(ErrorContext.DISK_FULL);

    Map<String, Long> counts = metrics.getFsyncPermanentErrorCounts();
    assertEquals(1, counts.get(ErrorContext.DISK_FULL.name()));
  }

  @Test
  @DisplayName("recordFsyncPermanentFailure handles multiple contexts")
  void testRecordFsyncPermanentFailureMultiple() {
    metrics.recordFsyncPermanentFailure(ErrorContext.DISK_FULL);
    metrics.recordFsyncPermanentFailure(ErrorContext.PERMISSION_DENIED);
    metrics.recordFsyncPermanentFailure(ErrorContext.DISK_FULL);

    Map<String, Long> counts = metrics.getFsyncPermanentErrorCounts();
    assertEquals(2, counts.get(ErrorContext.DISK_FULL.name()));
    assertEquals(1, counts.get(ErrorContext.PERMISSION_DENIED.name()));
  }

  @Test
  @DisplayName("getThroughputEntriesPerSec returns 0 when no entries")
  void testGetThroughputEntriesPerSecNoEntries() {
    assertEquals(0.0, metrics.getThroughputEntriesPerSec());
  }

  @Test
  @DisplayName("getThroughputEntriesPerSec calculates correctly after delay")
  void testGetThroughputEntriesPerSecWithDelay() throws InterruptedException {
    metrics.recordEntryAppended(100);
    Thread.sleep(100);
    metrics.recordEntryAppended(100);

    double throughput = metrics.getThroughputEntriesPerSec();
    assertTrue(throughput > 0);
  }

  @Test
  @DisplayName("getThroughputMbPerSec returns 0 when no bytes")
  void testGetThroughputMbPerSecNoBytes() {
    assertEquals(0.0, metrics.getThroughputMbPerSec());
  }

  @Test
  @DisplayName("getThroughputMbPerSec calculates correctly")
  void testGetThroughputMbPerSecWithData() throws InterruptedException {
    metrics.recordEntryAppended(1_000_000);
    Thread.sleep(100);

    double throughput = metrics.getThroughputMbPerSec();
    assertTrue(throughput > 0);
  }

  @Test
  @DisplayName("getEntriesWritten returns initial 0")
  void testGetEntriesWrittenInitial() {
    assertEquals(0, metrics.getEntriesWritten());
  }

  @Test
  @DisplayName("getBytesWritten returns initial 0")
  void testGetBytesWrittenInitial() {
    assertEquals(0, metrics.getBytesWritten());
  }

  @Test
  @DisplayName("getSegmentCount returns initial 0")
  void testGetSegmentCountInitial() {
    assertEquals(0, metrics.getSegmentCount());
  }

  @Test
  @DisplayName("getCorruptedEntriesDetected returns initial 0")
  void testGetCorruptedEntriesDetectedInitial() {
    assertEquals(0, metrics.getCorruptedEntriesDetected());
  }

  @Test
  @DisplayName("getLastRotationTimeMs returns initial 0")
  void testGetLastRotationTimeMsInitial() {
    assertEquals(0, metrics.getLastRotationTimeMs());
  }

  @Test
  @DisplayName("getTotalFsyncs returns initial 0")
  void testGetTotalFsyncsInitial() {
    assertEquals(0, metrics.getTotalFsyncs());
  }

  @Test
  @DisplayName("getLastFsyncTimeMs returns initial 0")
  void testGetLastFsyncTimeMsInitial() {
    assertEquals(0, metrics.getLastFsyncTimeMs());
  }

  @Test
  @DisplayName("getFsyncTransientErrorCounts returns copy")
  void testGetFsyncTransientErrorCountsCopy() {
    metrics.recordFsyncTransientFailure(ErrorContext.RESOURCE_BUSY);
    Map<String, Long> counts1 = metrics.getFsyncTransientErrorCounts();
    Map<String, Long> counts2 = metrics.getFsyncTransientErrorCounts();

    assertNotSame(counts1, counts2);
    assertEquals(counts1, counts2);
  }

  @Test
  @DisplayName("getFsyncPermanentErrorCounts returns copy")
  void testGetFsyncPermanentErrorCountsCopy() {
    metrics.recordFsyncPermanentFailure(ErrorContext.DISK_FULL);
    Map<String, Long> counts1 = metrics.getFsyncPermanentErrorCounts();
    Map<String, Long> counts2 = metrics.getFsyncPermanentErrorCounts();

    assertNotSame(counts1, counts2);
    assertEquals(counts1, counts2);
  }

  @Test
  @DisplayName("getCorruptionTypeCounts returns copy")
  void testGetCorruptionTypeCountsCopy() {
    metrics.recordCorruptionType(CorruptionType.HEADER_CRC_MISMATCH);
    Map<String, Long> counts1 = metrics.getCorruptionTypeCounts();
    Map<String, Long> counts2 = metrics.getCorruptionTypeCounts();

    assertNotSame(counts1, counts2);
    assertEquals(counts1, counts2);
  }

  @Test
  @DisplayName("Complex scenario: combined recording")
  void testComplexScenario() {
    metrics.recordEntryAppended(100);
    metrics.recordEntryAppended(200);
    metrics.recordFsync(50);
    metrics.recordFsync(100);
    metrics.recordSegmentRotation();
    metrics.setCurrentSegmentEntryCount(2);
    metrics.setCurrentSegmentByteCount(300);
    metrics.setTotalSegmentCount(1);
    metrics.recordCorruptedEntry();
    metrics.recordCorruptionType(CorruptionType.HEADER_CRC_MISMATCH);

    assertEquals(2, metrics.getEntriesWritten());
    assertEquals(300, metrics.getBytesWritten());
    assertEquals(2, metrics.getTotalFsyncs());
    assertEquals(75.0, metrics.getAverageFsyncLatencyMs());
    assertEquals(1, metrics.getCorruptedEntriesDetected());
    assertEquals(1, metrics.getSegmentCount());
    assertEquals(2, metrics.getCurrentSegmentEntryCount());
    assertEquals(300, metrics.getCurrentSegmentByteCount());
  }

  @Test
  @DisplayName("getRecoveryCount returns initial 0")
  void testGetRecoveryCountInitial() {
    assertEquals(0, metrics.getRecoveryCount());
  }

  @Test
  @DisplayName("getLastRecoverySegmentsScanned returns initial 0")
  void testGetLastRecoverySegmentsScannedInitial() {
    assertEquals(0, metrics.getLastRecoverySegmentsScanned());
  }

  @Test
  @DisplayName("getLastRecoverySegmentsRecovered returns initial 0")
  void testGetLastRecoverySegmentsRecoveredInitial() {
    assertEquals(0, metrics.getLastRecoverySegmentsRecovered());
  }

  @Test
  @DisplayName("getFsyncRetrySuccessCount returns initial 0")
  void testGetFsyncRetrySuccessCountInitial() {
    assertEquals(0, metrics.getFsyncRetrySuccessCount());
  }

  @Test
  @DisplayName("getSegmentCorruptionCount returns initial 0")
  void testGetSegmentCorruptionCountInitial() {
    assertEquals(0, metrics.getSegmentCorruptionCount());
  }

  @Test
  @DisplayName("Concurrent access test")
  void testConcurrentRecording() throws InterruptedException {
    Thread t1 =
        new Thread(
            () -> {
              for (int i = 0; i < 100; i++) {
                metrics.recordEntryAppended(10);
              }
            });

    Thread t2 =
        new Thread(
            () -> {
              for (int i = 0; i < 100; i++) {
                metrics.recordCorruptedEntry();
              }
            });

    t1.start();
    t2.start();
    t1.join();
    t2.join();

    assertEquals(100, metrics.getEntriesWritten());
    assertEquals(1000, metrics.getBytesWritten());
    assertEquals(100, metrics.getCorruptedEntriesDetected());
  }
}
