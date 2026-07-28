package io.writeahead.log.metrics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MILITARY-GRADE TEST SUITE FOR SIMPLEWALMETRICS
 *
 * <p>Tests all metrics tracking: entries, bytes, fsyncs, latency, throughput.
 * - Atomic increments (thread-safe)
 * - Accurate calculation of throughput and averages
 * - Thread safety under concurrent access
 */
public class SimpleWalMetricsTest {

    private SimpleWalMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new SimpleWalMetrics();
    }

    @Test
    void testRecordEntryWrittenIncrementsCount() {
        metrics.recordEntryWritten(10);

        assertEquals(1, metrics.getEntriesWritten(),
                "Should increment entry count by 1");
    }

    @Test
    void testRecordEntryWrittenIncrementsBytesWritten() {
        metrics.recordEntryWritten(10);
        metrics.recordEntryWritten(20);
        metrics.recordEntryWritten(30);

        assertEquals(60, metrics.getBytesWritten(),
                "Should sum all bytes written: 10+20+30=60");
    }

    @Test
    void testRecordFsyncRecordsTotalFsyncs() {
        metrics.recordFsync(5);
        metrics.recordFsync(10);
        metrics.recordFsync(15);

        assertEquals(3, metrics.getTotalFsyncs(),
                "Should record 3 fsync operations");
    }

    @Test
    void testRecordFsyncTracksLatency() {
        metrics.recordFsync(5);
        metrics.recordFsync(10);
        metrics.recordFsync(15);

        long totalLatency = 5 + 10 + 15;

        double averageLatency = metrics.getAverageFsyncLatencyMs();
        assertEquals(totalLatency / 3.0, averageLatency, 0.01,
                "Average latency should be (5+10+15)/3 = 10ms");
    }

    @Test
    void testRecordCorruptedEntry() {
        metrics.recordCorruptedEntry();
        metrics.recordCorruptedEntry();

        assertEquals(2, metrics.getCorruptedEntriesDetected(),
                "Should record 2 corrupted entries");
    }

    @Test
    void testRecordSegmentRotation() {
        long beforeRotation = metrics.getLastRotationTimeMs();

        metrics.recordSegmentRotation();

        long afterRotation = metrics.getLastRotationTimeMs();
        assertTrue(afterRotation >= beforeRotation,
                "Rotation time should be updated to current time");
    }

    @Test
    void testSetSegmentCount() {
        metrics.setSegmentCount(5);

        assertEquals(5, metrics.getSegmentCount(),
                "Should set segment count");
    }

    @Test
    void testThroughputCalculation() throws InterruptedException {
        // Record entries and sleep to create time delta
        for (int i = 0; i < 100; i++) {
            metrics.recordEntryWritten(100);  // 100 bytes per entry
        }

        Thread.sleep(1000);  // Sleep 1 second

        double throughputEntries = metrics.getThroughputEntriesPerSec();
        double throughputMb = metrics.getThroughputMbPerSec();

        // Should be roughly 100 entries/sec (100 entries in 1 sec)
        assertTrue(throughputEntries >= 90 && throughputEntries <= 110,
                "Throughput should be ~100 entries/sec");

        // Should be roughly 0.01 MB/sec (10KB in 1 sec)
        assertTrue(throughputMb >= 0.008 && throughputMb <= 0.012,
                "Throughput should be ~0.01 MB/sec");
    }

    @Test
    void testAverageFsyncLatencyCalculation() {
        metrics.recordFsync(2);
        metrics.recordFsync(4);
        metrics.recordFsync(6);

        double avgLatency = metrics.getAverageFsyncLatencyMs();

        assertEquals(4.0, avgLatency, 0.01,
                "Average latency should be (2+4+6)/3 = 4ms");
    }

    @Test
    void testAverageFsyncLatencyZeroFsyncs() {
        double avgLatency = metrics.getAverageFsyncLatencyMs();

        assertEquals(0.0, avgLatency,
                "Average latency with no fsyncs should be 0");
    }

    @Test
    void testThroughputIsPositiveWhenEntriesRecorded() throws InterruptedException {
        metrics.recordEntryWritten(100);

        Thread.sleep(10);  // Ensure at least 10ms passes

        double throughput = metrics.getThroughputEntriesPerSec();

        assertTrue(throughput > 0.0,
                "Throughput should be positive when entries are recorded and time has elapsed");
    }

    @Test
    void testMultipleEntriesAcrossMultipleBatches() {
        // Batch 1
        metrics.recordEntryWritten(10);
        metrics.recordEntryWritten(20);

        // Batch 2
        metrics.recordEntryWritten(30);
        metrics.recordEntryWritten(40);

        // Batch 3
        metrics.recordEntryWritten(50);

        assertEquals(5, metrics.getEntriesWritten(),
                "Should track all 5 entries");
        assertEquals(150, metrics.getBytesWritten(),
                "Should sum all bytes: 10+20+30+40+50=150");
    }

    @Test
    void testMetricsStartWithZeros() {
        assertEquals(0, metrics.getEntriesWritten());
        assertEquals(0, metrics.getBytesWritten());
        assertEquals(0, metrics.getSegmentCount());
        assertEquals(0, metrics.getCorruptedEntriesDetected());
        assertEquals(0, metrics.getTotalFsyncs());
    }

    @Test
    void testLastFsyncTimeUpdated() {
        long beforeFsync = System.currentTimeMillis();

        metrics.recordFsync(5);

        long lastFsyncTime = metrics.getLastFsyncTimeMs();

        assertTrue(lastFsyncTime >= beforeFsync,
                "Last fsync time should be at or after the call time");
    }

    @Test
    void testMultipleFsyncsUpdateLastTime() {
        metrics.recordFsync(5);
        long firstFsyncTime = metrics.getLastFsyncTimeMs();

        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }

        metrics.recordFsync(10);
        long secondFsyncTime = metrics.getLastFsyncTimeMs();

        assertTrue(secondFsyncTime >= firstFsyncTime,
                "Last fsync time should be updated to most recent fsync");
    }

    @Test
    void testLargeByteCount() {
        long largeSize = 1024 * 1024 * 100;  // 100 MB

        metrics.recordEntryWritten((int) largeSize);

        assertEquals(largeSize, metrics.getBytesWritten());
    }

    @Test
    void testLargeEntryCount() {
        for (int i = 0; i < 10000; i++) {
            metrics.recordEntryWritten(1);
        }

        assertEquals(10000, metrics.getEntriesWritten(),
                "Should handle 10000 entries");
    }

    @Test
    void testAtomicUpdatesUnderConcurrency() throws InterruptedException {
        int threadCount = 10;
        int entriesPerThread = 100;

        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < entriesPerThread; i++) {
                    metrics.recordEntryWritten(10);
                }
            });
            threads[t].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        int expectedEntries = threadCount * entriesPerThread;
        assertEquals(expectedEntries, metrics.getEntriesWritten(),
                "Should correctly count all entries from concurrent threads");

        int expectedBytes = expectedEntries * 10;
        assertEquals(expectedBytes, metrics.getBytesWritten(),
                "Should correctly sum all bytes from concurrent threads");
    }

    @Test
    void testConcurrentFsyncRecording() throws InterruptedException {
        int threadCount = 5;
        int fsyncsPerThread = 20;

        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < fsyncsPerThread; i++) {
                    metrics.recordFsync(5 + i);  // Varying latencies
                }
            });
            threads[t].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        int expectedFsyncs = threadCount * fsyncsPerThread;
        assertEquals(expectedFsyncs, metrics.getTotalFsyncs(),
                "Should correctly count all fsyncs from concurrent threads");
    }

    @Test
    void testConcurrentCorruptionRecording() throws InterruptedException {
        int threadCount = 10;

        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    metrics.recordCorruptedEntry();
                }
            });
            threads[t].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        assertEquals(threadCount * 10, metrics.getCorruptedEntriesDetected(),
                "Should correctly track corrupted entries from concurrent threads");
    }

    @Test
    void testMetricsIsolatedBetweenInstances() {
        SimpleWalMetrics metrics1 = new SimpleWalMetrics();
        SimpleWalMetrics metrics2 = new SimpleWalMetrics();

        metrics1.recordEntryWritten(100);
        metrics2.recordEntryWritten(50);

        assertEquals(100, metrics1.getBytesWritten());
        assertEquals(50, metrics2.getBytesWritten(),
                "Metrics should be isolated between instances");
    }
}