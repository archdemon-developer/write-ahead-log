package io.writeahead.log.fsync.retry;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.fsync.FsyncOperation;
import io.writeahead.log.fsync.retryers.ExponentialBackoffRetryStrategy;
import io.writeahead.log.metrics.SimpleWalMetrics;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExponentialBackoffRetryStrategyTest {

  private static final int DEFAULT_MAX_RETRIES = 3;
  private static final int DEFAULT_INITIAL_BACKOFF_MS = 10;
  private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
  private static final int SINGLE_RETRY_ALLOWED = 1;
  private static final int NO_RETRIES_ALLOWED = 0;
  private static final int FIVE_MS_BACKOFF = 5;
  private static final int MINIMUM_TIMING_TOLERANCE_MS = 8;
  private static final int EXPECTED_SINGLE_BACKOFF_WAIT_MS = 25;
  private static final int EXPECTED_DOUBLE_BACKOFF_WAIT_MS = 25;

  private SimpleWalMetrics metricsCollector;
  private ExponentialBackoffRetryStrategy retryStrategyWithDefaults;

  @BeforeEach
  void setUp() {
    metricsCollector = new SimpleWalMetrics();
    retryStrategyWithDefaults =
        new ExponentialBackoffRetryStrategy(
            DEFAULT_MAX_RETRIES,
            DEFAULT_INITIAL_BACKOFF_MS,
            DEFAULT_BACKOFF_MULTIPLIER,
            metricsCollector);
  }

  @Test
  void successOnFirstAttemptRequiresNoRetries() throws IOException {
    TestableFsyncOperation operation = new TestableFsyncOperation();

    retryStrategyWithDefaults.executeWithRetry(operation);

    assertEquals(1, operation.totalAttempts());
  }

  @Test
  void retriesOnTransientFailureThenSucceeds() throws IOException {
    TestableFsyncOperation operation = new TestableFsyncOperation();
    operation.failForNumberOfAttempts(2);

    retryStrategyWithDefaults.executeWithRetry(operation);

    assertEquals(3, operation.totalAttempts());
  }

  @Test
  void failsAfterExhaustingMaxRetries() throws IOException {
    TestableFsyncOperation operation = new TestableFsyncOperation();
    operation.failAllAttempts();

    assertThrows(IOException.class, () -> retryStrategyWithDefaults.executeWithRetry(operation));

    assertEquals(4, operation.totalAttempts());
  }

  @Test
  void exponentialBackoffDelayGrowsWithEachRetry() throws IOException {
    TestableFsyncOperation operation = new TestableFsyncOperation();
    operation.failForNumberOfAttempts(2);

    long executionStartTime = System.currentTimeMillis();
    retryStrategyWithDefaults.executeWithRetry(operation);
    long executionDurationMs = System.currentTimeMillis() - executionStartTime;

    assertTrue(executionDurationMs >= EXPECTED_SINGLE_BACKOFF_WAIT_MS);
  }

  @Test
  void successAfterSingleRetryDoesNotThrow() throws IOException {
    TestableFsyncOperation operation = new TestableFsyncOperation();
    operation.failForNumberOfAttempts(1);

    assertDoesNotThrow(() -> retryStrategyWithDefaults.executeWithRetry(operation));

    assertEquals(2, operation.totalAttempts());
  }

  @Test
  void metricsRecordedOnFirstAttemptSuccess() throws IOException {
    TestableFsyncOperation operation = new TestableFsyncOperation();

    long metricsBeforeFsync = metricsCollector.getTotalFsyncs();
    retryStrategyWithDefaults.executeWithRetry(operation);
    long metricsAfterFsync = metricsCollector.getTotalFsyncs();

    assertEquals(1, metricsAfterFsync - metricsBeforeFsync);
  }

  @Test
  void metricsRecordSuccessfulCompletionAfterRetries() throws IOException {
    TestableFsyncOperation operation = new TestableFsyncOperation();
    operation.failForNumberOfAttempts(2);

    long metricsBeforeFsync = metricsCollector.getTotalFsyncs();
    retryStrategyWithDefaults.executeWithRetry(operation);
    long metricsAfterFsync = metricsCollector.getTotalFsyncs();

    assertEquals(1, metricsAfterFsync - metricsBeforeFsync);
  }

  @Test
  void multipleRetriesUntilSuccess() throws IOException {
    TestableFsyncOperation operation = new TestableFsyncOperation();
    operation.failForNumberOfAttempts(3);

    retryStrategyWithDefaults.executeWithRetry(operation);

    assertEquals(4, operation.totalAttempts());
  }

  @Test
  void maxRetriesLimitIsEnforced() throws IOException {
    ExponentialBackoffRetryStrategy limitedRetryStrategy =
        new ExponentialBackoffRetryStrategy(
            SINGLE_RETRY_ALLOWED,
            DEFAULT_INITIAL_BACKOFF_MS,
            DEFAULT_BACKOFF_MULTIPLIER,
            metricsCollector);

    TestableFsyncOperation operation = new TestableFsyncOperation();
    operation.failAllAttempts();

    assertThrows(IOException.class, () -> limitedRetryStrategy.executeWithRetry(operation));

    assertEquals(2, operation.totalAttempts());
  }

  @Test
  void zeroRetriesAllowedSucceedsOnFirstAttempt() throws IOException {
    ExponentialBackoffRetryStrategy noRetryStrategy =
        new ExponentialBackoffRetryStrategy(
            NO_RETRIES_ALLOWED,
            DEFAULT_INITIAL_BACKOFF_MS,
            DEFAULT_BACKOFF_MULTIPLIER,
            metricsCollector);

    TestableFsyncOperation successOperation = new TestableFsyncOperation();

    assertDoesNotThrow(() -> noRetryStrategy.executeWithRetry(successOperation));
    assertEquals(1, successOperation.totalAttempts());
  }

  @Test
  void zeroRetriesAllowedFailsOnFirstAttempt() throws IOException {
    ExponentialBackoffRetryStrategy noRetryStrategy =
        new ExponentialBackoffRetryStrategy(
            NO_RETRIES_ALLOWED,
            DEFAULT_INITIAL_BACKOFF_MS,
            DEFAULT_BACKOFF_MULTIPLIER,
            metricsCollector);

    TestableFsyncOperation failingOperation = new TestableFsyncOperation();
    failingOperation.failAllAttempts();

    assertThrows(IOException.class, () -> noRetryStrategy.executeWithRetry(failingOperation));
    assertEquals(1, failingOperation.totalAttempts());
  }

  @Test
  void interruptedSleepDuringBackoffHandledGracefully() throws IOException {
    TestableFsyncOperation operation = new TestableFsyncOperation();
    operation.failForNumberOfAttempts(1);

    assertDoesNotThrow(() -> retryStrategyWithDefaults.executeWithRetry(operation));
  }

  @Test
  void backoffMultiplierAppliesToConsecutiveRetries() throws IOException {
    TestableFsyncOperation singleRetryOperation = new TestableFsyncOperation();
    singleRetryOperation.failForNumberOfAttempts(1);

    long singleRetryStartTime = System.currentTimeMillis();
    retryStrategyWithDefaults.executeWithRetry(singleRetryOperation);
    long singleRetryDurationMs = System.currentTimeMillis() - singleRetryStartTime;

    assertTrue(singleRetryDurationMs >= MINIMUM_TIMING_TOLERANCE_MS);

    metricsCollector = new SimpleWalMetrics();
    retryStrategyWithDefaults =
        new ExponentialBackoffRetryStrategy(
            DEFAULT_MAX_RETRIES,
            DEFAULT_INITIAL_BACKOFF_MS,
            DEFAULT_BACKOFF_MULTIPLIER,
            metricsCollector);

    TestableFsyncOperation doubleRetryOperation = new TestableFsyncOperation();
    doubleRetryOperation.failForNumberOfAttempts(2);

    long doubleRetryStartTime = System.currentTimeMillis();
    retryStrategyWithDefaults.executeWithRetry(doubleRetryOperation);
    long doubleRetryDurationMs = System.currentTimeMillis() - doubleRetryStartTime;

    assertTrue(doubleRetryDurationMs >= EXPECTED_DOUBLE_BACKOFF_WAIT_MS);
  }

  @Test
  void averageFsyncLatencyIsTrackedNonNegative() throws IOException {
    TestableFsyncOperation operation = new TestableFsyncOperation();

    retryStrategyWithDefaults.executeWithRetry(operation);

    double averageLatencyMs = metricsCollector.getAverageFsyncLatencyMs();
    assertTrue(averageLatencyMs >= 0);
  }

  static class TestableFsyncOperation implements FsyncOperation {
    private int totalAttemptsMade = 0;
    private int failUntilAttemptNumber = 0;
    private boolean shouldAlwaysFail = false;

    @Override
    public void fsync() throws IOException {
      totalAttemptsMade++;

      if (shouldAlwaysFail) {
        throw new IOException("Resource temporarily unavailable");
      }

      if (totalAttemptsMade <= failUntilAttemptNumber) {
        throw new IOException("Resource temporarily unavailable");
      }
    }

    public int totalAttempts() {
      return totalAttemptsMade;
    }

    public void failForNumberOfAttempts(int numberOfAttempts) {
      this.failUntilAttemptNumber = numberOfAttempts;
    }

    public void failAllAttempts() {
      this.shouldAlwaysFail = true;
    }
  }
}
