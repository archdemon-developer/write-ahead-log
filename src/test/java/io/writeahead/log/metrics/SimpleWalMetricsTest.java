package io.writeahead.log.metrics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SimpleWalMetricsTest {

  private static final long SINGLE_ENTRY_RECORDED = 1L;
  private static final int SMALL_BYTE_SIZE = 10;
  private static final int MEDIUM_BYTE_SIZE = 20;
  private static final int LARGE_BYTE_SIZE = 30;
  private static final int TOTAL_BYTES_FOR_THREE_ENTRIES = 60;
  private static final long THREE_FSYNCS = 3L;
  private static final long FIRST_FSYNC_LATENCY_MS = 5L;
  private static final long SECOND_FSYNC_LATENCY_MS = 10L;
  private static final long THIRD_FSYNC_LATENCY_MS = 15L;
  private static final double EXPECTED_AVERAGE_LATENCY_MS = 10.0;
  private static final long TWO_CORRUPTED_ENTRIES = 2L;
  private static final int HUNDRED_ENTRIES = 100;
  private static final int HUNDRED_BYTES_PER_ENTRY = 100;
  private static final int HUNDRED_TOTAL_BYTES = 10000;
  private static final int TEN_THOUSAND_ENTRIES = 10000;
  private static final int ONE_BYTE_PER_ENTRY = 1;
  private static final int CONCURRENT_THREAD_COUNT = 10;
  private static final int ENTRIES_PER_THREAD = 100;
  private static final int TEN_BYTES_PER_ENTRY = 10;
  private static final long EXPECTED_TOTAL_CONCURRENT_ENTRIES = 1000L;
  private static final long EXPECTED_TOTAL_CONCURRENT_BYTES = 10000L;
  private static final int FSYNC_THREAD_COUNT = 5;
  private static final int FSYNCS_PER_THREAD = 20;
  private static final long BASE_LATENCY_MS = 5L;
  private static final long EXPECTED_TOTAL_FSYNCS = 100L;
  private static final int CORRUPTION_THREAD_COUNT = 10;
  private static final int CORRUPTIONS_PER_THREAD = 10;
  private static final long EXPECTED_TOTAL_CORRUPTIONS = 100L;
  private static final int FIRST_INSTANCE_BYTES = 100;
  private static final int SECOND_INSTANCE_BYTES = 50;
  private static final long EXPECTED_SEGMENT_COUNT = 5L;
  private static final int ZERO_ENTRIES = 0;
  private static final int ZERO_BYTES = 0;

  private SimpleWalMetrics metricsCollector;

  @BeforeEach
  void setUp() {
    metricsCollector = new SimpleWalMetrics();
  }

  @Test
  void recordEntryWrittenIncrementsEntryCount() {
    metricsCollector.recordEntryWritten(SMALL_BYTE_SIZE);

    assertEquals(SINGLE_ENTRY_RECORDED, metricsCollector.getEntriesWritten());
  }

  @Test
  void recordEntryWrittenAccumulatesBytesWritten() {
    metricsCollector.recordEntryWritten(SMALL_BYTE_SIZE);
    metricsCollector.recordEntryWritten(MEDIUM_BYTE_SIZE);
    metricsCollector.recordEntryWritten(LARGE_BYTE_SIZE);

    assertEquals(TOTAL_BYTES_FOR_THREE_ENTRIES, metricsCollector.getBytesWritten());
  }

  @Test
  void recordFsyncIncrementsTotalFsyncCount() {
    metricsCollector.recordFsync(FIRST_FSYNC_LATENCY_MS);
    metricsCollector.recordFsync(SECOND_FSYNC_LATENCY_MS);
    metricsCollector.recordFsync(THIRD_FSYNC_LATENCY_MS);

    assertEquals(THREE_FSYNCS, metricsCollector.getTotalFsyncs());
  }

  @Test
  void recordFsyncTracksAverageLatency() {
    metricsCollector.recordFsync(FIRST_FSYNC_LATENCY_MS);
    metricsCollector.recordFsync(SECOND_FSYNC_LATENCY_MS);
    metricsCollector.recordFsync(THIRD_FSYNC_LATENCY_MS);

    double averageLatencyMs = metricsCollector.getAverageFsyncLatencyMs();
    assertEquals(EXPECTED_AVERAGE_LATENCY_MS, averageLatencyMs, 0.01);
  }

  @Test
  void recordCorruptedEntryIncrementsCorruptionCount() {
    metricsCollector.recordCorruptedEntry();
    metricsCollector.recordCorruptedEntry();

    assertEquals(TWO_CORRUPTED_ENTRIES, metricsCollector.getCorruptedEntriesDetected());
  }

  @Test
  void recordSegmentRotationUpdatesRotationTimestamp() {
    long rotationTimestampBeforeRecording = metricsCollector.getLastRotationTimeMs();

    metricsCollector.recordSegmentRotation();

    long rotationTimestampAfterRecording = metricsCollector.getLastRotationTimeMs();
    assertTrue(rotationTimestampAfterRecording >= rotationTimestampBeforeRecording);
  }

  @Test
  void setSegmentCountUpdatesSegmentCount() {
    metricsCollector.setSegmentCount(EXPECTED_SEGMENT_COUNT);

    assertEquals(EXPECTED_SEGMENT_COUNT, metricsCollector.getSegmentCount());
  }

  @Test
  void recordMultipleEntriesCalculatesCorrectThroughput() {
    for (int entryIndex = 0; entryIndex < HUNDRED_ENTRIES; entryIndex++) {
      metricsCollector.recordEntryWritten(HUNDRED_BYTES_PER_ENTRY);
    }

    assertEquals(HUNDRED_ENTRIES, metricsCollector.getEntriesWritten());
    assertEquals(HUNDRED_TOTAL_BYTES, metricsCollector.getBytesWritten());
  }

  @Test
  void recordZeroByteEntriesTrackedCorrectly() {
    metricsCollector.recordEntryWritten(ZERO_BYTES);
    metricsCollector.recordEntryWritten(ZERO_BYTES);
    metricsCollector.recordEntryWritten(ZERO_BYTES);

    assertEquals(3, metricsCollector.getEntriesWritten());
    assertEquals(ZERO_BYTES, metricsCollector.getBytesWritten());
  }

  @Test
  void recordLargeEntryCountHandledCorrectly() {
    for (int entryIndex = 0; entryIndex < TEN_THOUSAND_ENTRIES; entryIndex++) {
      metricsCollector.recordEntryWritten(ONE_BYTE_PER_ENTRY);
    }

    assertEquals(TEN_THOUSAND_ENTRIES, metricsCollector.getEntriesWritten());
  }

  @Test
  void atomicUpdatesUnderConcurrentThreadAccess() throws InterruptedException {
    Thread[] workerThreads = new Thread[CONCURRENT_THREAD_COUNT];

    for (int threadIndex = 0; threadIndex < CONCURRENT_THREAD_COUNT; threadIndex++) {
      workerThreads[threadIndex] =
          new Thread(
              () -> {
                for (int entryIndex = 0; entryIndex < ENTRIES_PER_THREAD; entryIndex++) {
                  metricsCollector.recordEntryWritten(TEN_BYTES_PER_ENTRY);
                }
              });
      workerThreads[threadIndex].start();
    }

    for (Thread workerThread : workerThreads) {
      workerThread.join();
    }

    assertEquals(EXPECTED_TOTAL_CONCURRENT_ENTRIES, metricsCollector.getEntriesWritten());
    assertEquals(EXPECTED_TOTAL_CONCURRENT_BYTES, metricsCollector.getBytesWritten());
  }

  @Test
  void concurrentFsyncRecordingHandledCorrectly() throws InterruptedException {
    Thread[] workerThreads = new Thread[FSYNC_THREAD_COUNT];

    for (int threadIndex = 0; threadIndex < FSYNC_THREAD_COUNT; threadIndex++) {
      workerThreads[threadIndex] =
          new Thread(
              () -> {
                for (int fsyncIndex = 0; fsyncIndex < FSYNCS_PER_THREAD; fsyncIndex++) {
                  long fsyncLatencyMs = BASE_LATENCY_MS + fsyncIndex;
                  metricsCollector.recordFsync(fsyncLatencyMs);
                }
              });
      workerThreads[threadIndex].start();
    }

    for (Thread workerThread : workerThreads) {
      workerThread.join();
    }

    assertEquals(EXPECTED_TOTAL_FSYNCS, metricsCollector.getTotalFsyncs());
  }

  @Test
  void concurrentCorruptionRecordingHandledCorrectly() throws InterruptedException {
    Thread[] workerThreads = new Thread[CORRUPTION_THREAD_COUNT];

    for (int threadIndex = 0; threadIndex < CORRUPTION_THREAD_COUNT; threadIndex++) {
      workerThreads[threadIndex] =
          new Thread(
              () -> {
                for (int corruptionIndex = 0;
                    corruptionIndex < CORRUPTIONS_PER_THREAD;
                    corruptionIndex++) {
                  metricsCollector.recordCorruptedEntry();
                }
              });
      workerThreads[threadIndex].start();
    }

    for (Thread workerThread : workerThreads) {
      workerThread.join();
    }

    assertEquals(EXPECTED_TOTAL_CORRUPTIONS, metricsCollector.getCorruptedEntriesDetected());
  }

  @Test
  void metricsIsolatedBetweenIndependentInstances() {
    SimpleWalMetrics firstMetricsInstance = new SimpleWalMetrics();
    SimpleWalMetrics secondMetricsInstance = new SimpleWalMetrics();

    firstMetricsInstance.recordEntryWritten(FIRST_INSTANCE_BYTES);
    secondMetricsInstance.recordEntryWritten(SECOND_INSTANCE_BYTES);

    assertEquals(FIRST_INSTANCE_BYTES, firstMetricsInstance.getBytesWritten());
    assertEquals(SECOND_INSTANCE_BYTES, secondMetricsInstance.getBytesWritten());
  }
}
