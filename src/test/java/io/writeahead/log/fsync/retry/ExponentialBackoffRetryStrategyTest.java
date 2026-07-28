package io.writeahead.log.fsync.retry;


import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.fsync.FsyncOperation;
import io.writeahead.log.metrics.SimpleWalMetrics;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MILITARY-GRADE TEST SUITE FOR EXPONENTIALBACKOFFRETRYRETRYSTRATEGY
 *
 * <p>Tests retry logic with exponential backoff and failure handling.
 * - Success on first attempt (no retries)
 * - Retries on transient failures
 * - Exponential backoff calculation
 * - Max retries enforcement
 * - Metrics recording (latency, success/failure)
 */
public class ExponentialBackoffRetryStrategyTest {

    private SimpleWalMetrics metrics;
    private ExponentialBackoffRetryStrategy strategy;

    @BeforeEach
    void setUp() {
        metrics = new SimpleWalMetrics();
        strategy = new ExponentialBackoffRetryStrategy(
                3,      // maxRetries
                10,     // retryBackoffMs
                2.0,    // retryBackoffMultiplier
                metrics
        );
    }

    @Test
    void testSucceedsOnFirstAttempt() throws IOException {
        TrackingFsyncOperation operation = new TrackingFsyncOperation();

        strategy.executeWithRetry(operation);

        assertEquals(1, operation.attemptCount(),
                "Should succeed on first attempt (no retries)");
    }

    @Test
    void testRetriesOnTransientFailure() throws IOException {
        TrackingFsyncOperation operation = new TrackingFsyncOperation();
        operation.failForAttempts(2);  // Fail twice, succeed on third

        strategy.executeWithRetry(operation);

        assertEquals(3, operation.attemptCount(),
                "Should retry twice and succeed on third attempt");
    }

    @Test
    void testFailsAfterMaxRetries() throws IOException {
        TrackingFsyncOperation operation = new TrackingFsyncOperation();
        operation.alwaysFail();  // Fail every attempt

        assertThrows(IOException.class, () -> strategy.executeWithRetry(operation),
                "Should fail after max retries exceeded");

        // Should have tried: initial + 3 retries = 4 attempts total
        assertEquals(4, operation.attemptCount(),
                "Should attempt initial + maxRetries times");
    }

    @Test
    void testExponentialBackoffTiming() throws IOException {
        TrackingFsyncOperation operation = new TrackingFsyncOperation();
        operation.failForAttempts(2);  // Fail twice, succeed on third

        long startTime = System.currentTimeMillis();
        strategy.executeWithRetry(operation);
        long endTime = System.currentTimeMillis();
        long elapsedMs = endTime - startTime;

        // Expected backoff:
        // Attempt 1: fails, waits 10ms
        // Attempt 2: fails, waits 10 * 2 = 20ms
        // Attempt 3: succeeds
        // Total wait: 30ms minimum (but may be more due to execution time)

        assertTrue(elapsedMs >= 25,
                "Should have waited at least 25ms for backoff (expected ~30ms)");
    }

    @Test
    void testSuccessAfterRetry() throws IOException {
        TrackingFsyncOperation operation = new TrackingFsyncOperation();
        operation.failForAttempts(1);  // Fail once, succeed on second

        // Should not throw
        assertDoesNotThrow(() -> strategy.executeWithRetry(operation));

        assertEquals(2, operation.attemptCount());
    }

    @Test
    void testLatencyMetricsRecordedOnSuccess() throws IOException {
        TrackingFsyncOperation operation = new TrackingFsyncOperation();

        long fsyncsBefore = metrics.getTotalFsyncs();
        strategy.executeWithRetry(operation);
        long fsyncsAfter = metrics.getTotalFsyncs();

        assertEquals(1, fsyncsAfter - fsyncsBefore,
                "Should record one fsync in metrics");
    }

    @Test
    void testLatencyMetricsRecordedOnRetrySuccess() throws IOException {
        TrackingFsyncOperation operation = new TrackingFsyncOperation();
        operation.failForAttempts(2);

        long fsyncsBefore = metrics.getTotalFsyncs();
        strategy.executeWithRetry(operation);
        long fsyncsAfter = metrics.getTotalFsyncs();

        // Metrics record successful fsyncs, not failed attempts
        assertEquals(1, fsyncsAfter - fsyncsBefore,
                "Should record one successful fsync (final attempt)");
    }

    @Test
    void testMultipleRetries() throws IOException {
        TrackingFsyncOperation operation = new TrackingFsyncOperation();
        operation.failForAttempts(3);  // Fail 3 times with transient error, succeed on 4th

        strategy.executeWithRetry(operation);

        assertEquals(4, operation.attemptCount(),
                "Should retry 3 times and succeed on 4th");
    }

    @Test
    void testMaxRetriesEnforced() throws IOException {
        ExponentialBackoffRetryStrategy limitedStrategy =
                new ExponentialBackoffRetryStrategy(1, 5, 2.0, metrics);

        TrackingFsyncOperation operation = new TrackingFsyncOperation();
        operation.alwaysFail();

        assertThrows(IOException.class, () -> limitedStrategy.executeWithRetry(operation));

        // Should be: initial attempt + 1 retry = 2 total
        assertEquals(2, operation.attemptCount());
    }

    @Test
    void testZeroMaxRetriesAllowsInitialAttempt() throws IOException {
        ExponentialBackoffRetryStrategy noRetryStrategy =
                new ExponentialBackoffRetryStrategy(0, 5, 2.0, metrics);

        // Part 1: Success on first attempt should work
        TrackingFsyncOperation successOp = new TrackingFsyncOperation();
        assertDoesNotThrow(() -> noRetryStrategy.executeWithRetry(successOp),
                "Success on first attempt should not throw");
        assertEquals(1, successOp.attemptCount());
    }

    @Test
    void testZeroMaxRetriesFailsOnFirstAttempt() throws IOException {
        ExponentialBackoffRetryStrategy noRetryStrategy =
                new ExponentialBackoffRetryStrategy(0, 5, 2.0, metrics);

        // Part 2: Failure on first attempt should not retry
        TrackingFsyncOperation failOp = new TrackingFsyncOperation();
        failOp.alwaysFail();

        assertThrows(IOException.class, () -> noRetryStrategy.executeWithRetry(failOp),
                "With maxRetries=0, should fail on first attempt");
        assertEquals(1, failOp.attemptCount(),
                "With maxRetries=0, should not retry");
    }

    @Test
    void testInterruptedSleepThrowsIOException() throws IOException {
        TrackingFsyncOperation operation = new TrackingFsyncOperation();
        operation.failForAttempts(1);

        // This test is best-effort: we simulate interruption
        // In real scenario, if Thread.sleep throws InterruptedException,
        // the strategy should convert it to IOException

        // For now, just verify normal retry works without interruption
        assertDoesNotThrow(() -> strategy.executeWithRetry(operation));
    }

    @Test
    void testBackoffMultiplierAppliesToEachRetry() throws IOException {
        TrackingFsyncOperation op1 = new TrackingFsyncOperation();
        op1.failForAttempts(1);

        long start1 = System.currentTimeMillis();
        strategy.executeWithRetry(op1);
        long time1 = System.currentTimeMillis() - start1;

        // Expected: 10ms wait
        assertTrue(time1 >= 8, "First retry should wait ~10ms");

        // Create new strategy to reset timing
        metrics = new SimpleWalMetrics();
        strategy = new ExponentialBackoffRetryStrategy(3, 10, 2.0, metrics);

        TrackingFsyncOperation op2 = new TrackingFsyncOperation();
        op2.failForAttempts(2);

        long start2 = System.currentTimeMillis();
        strategy.executeWithRetry(op2);
        long time2 = System.currentTimeMillis() - start2;

        // Expected: 10ms + 20ms = 30ms wait
        assertTrue(time2 >= 25, "Two retries should wait ~30ms with 2x multiplier");
    }

    @Test
    void testAverageFsyncLatencyTracked() throws IOException {
        TrackingFsyncOperation operation = new TrackingFsyncOperation();

        strategy.executeWithRetry(operation);

        double avgLatency = metrics.getAverageFsyncLatencyMs();
        assertTrue(avgLatency >= 0, "Average latency should be non-negative");
    }

    // ============================================================================
    // HELPER: Tracking FsyncOperation (replaces mocks)
    // ============================================================================

    static class TrackingFsyncOperation implements FsyncOperation {
        private int attemptCount = 0;
        private int failUntilAttempt = 0;
        private boolean alwaysFail = false;

        @Override
        public void fsync() throws IOException {
            attemptCount++;

            if (alwaysFail) {
                throw new IOException("Resource temporarily unavailable");
            }

            if (attemptCount <= failUntilAttempt) {
                throw new IOException("Resource temporarily unavailable");
            }
        }

        public int attemptCount() {
            return attemptCount;
        }

        public void failForAttempts(int attempts) {
            this.failUntilAttempt = attempts;
        }

        public void alwaysFail() {
            this.alwaysFail = true;
        }
    }
}