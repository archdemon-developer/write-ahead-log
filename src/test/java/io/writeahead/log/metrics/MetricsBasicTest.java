package io.writeahead.log.metrics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MetricsBasicTest {

    private SimpleWalMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new SimpleWalMetrics();
    }

    @Test
    void testRecordEntryWritten() {
        metrics.recordEntryWritten(100);
        metrics.recordEntryWritten(200);

        assertEquals(2, metrics.getEntriesWritten(), "Should have 2 entries");
        assertEquals(300, metrics.getBytesWritten(), "Should have 300 bytes");
    }

    @Test
    void testSetSegmentCount() {
        metrics.setSegmentCount(3);
        assertEquals(3, metrics.getSegmentCount(), "Should have 3 segments");

        metrics.setSegmentCount(5);
        assertEquals(5, metrics.getSegmentCount(), "Should have 5 segments");
    }

    @Test
    void testRecordCorruptedEntry() {
        metrics.recordCorruptedEntry();
        metrics.recordCorruptedEntry();

        assertEquals(2, metrics.getCorruptedEntriesDetected(), "Should have 2 corrupted entries");
    }

    @Test
    void testRecordSegmentRotation() {
        long before = System.currentTimeMillis();
        metrics.recordSegmentRotation();
        long after = System.currentTimeMillis();

        long rotationTime = metrics.getLastRotationTimeMs();
        assertTrue(
                rotationTime >= before && rotationTime <= after,
                "Rotation timestamp should be within recorded time window");
    }

    @Test
    void testRecordFsync() {
        metrics.recordFsync(5);
        metrics.recordFsync(10);
        metrics.recordFsync(15);

        assertEquals(3, metrics.getTotalFsyncs(), "Should have 3 fsyncs");
        assertEquals(10.0, metrics.getAverageFsyncLatencyMs(), "Average should be 10ms");
    }

    @Test
    void testFsyncTimestamp() {
        long before = System.currentTimeMillis();
        metrics.recordFsync(5);
        long after = System.currentTimeMillis();

        long fsyncTime = metrics.getLastFsyncTimeMs();
        assertTrue(
                fsyncTime >= before && fsyncTime <= after,
                "Fsync timestamp should be within recorded time window");
    }

    @Test
    void testThroughputCalculations() throws InterruptedException {
        metrics.recordEntryWritten(1024);  // 1KB
        metrics.recordEntryWritten(1024);  // 1KB

        Thread.sleep(100);  // 100ms

        double entriesPerSec = metrics.getThroughputEntriesPerSec();
        double mbPerSec = metrics.getThroughputMbPerSec();

        assertTrue(entriesPerSec > 0, "Throughput should be positive");
        assertTrue(mbPerSec > 0, "MB/sec should be positive");
    }

    @Test
    void testAverageFsyncLatencyWithZeroFsyncs() {
        assertEquals(0.0, metrics.getAverageFsyncLatencyMs(), "Average with 0 fsyncs should be 0");
    }

    @Test
    void testThroughputWithZeroElapsedTime() {
        metrics.recordEntryWritten(100);

        // Immediately check throughput (almost 0 elapsed time)
        double throughput = metrics.getThroughputEntriesPerSec();

        // Should be very high or handle gracefully
        assertTrue(throughput >= 0, "Throughput should not be negative");
    }
}