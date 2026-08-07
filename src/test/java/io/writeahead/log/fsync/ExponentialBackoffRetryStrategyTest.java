package io.writeahead.log.fsync;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.enums.exceptions.CorruptionType;
import io.writeahead.log.enums.exceptions.ErrorContext;
import io.writeahead.log.enums.strategies.RotationPolicyType;
import io.writeahead.log.fsync.retryers.ExponentialBackoffRetryStrategy;
import io.writeahead.log.fsync.retryers.FsyncRetryStrategy;
import io.writeahead.log.fsync.retryers.FsyncRetryStrategyFactory;
import io.writeahead.log.metrics.WalMetricsRecorder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExponentialBackoffRetryStrategy - Fsync Retry Logic")
public class ExponentialBackoffRetryStrategyTest {

  static class MockMetricsRecorder implements WalMetricsRecorder {
    List<Long> fsyncLatencies = new ArrayList<>();
    int fsyncRetrySuccessCount = 0;
    List<ErrorContext> transientFailureContexts = new ArrayList<>();
    List<ErrorContext> permanentFailureContexts = new ArrayList<>();

    @Override
    public void recordFsync(long latencyMs) {
      fsyncLatencies.add(latencyMs);
    }

    @Override
    public void recordFsyncRetrySuccess(int attempts) {
      fsyncRetrySuccessCount++;
    }

    @Override
    public void recordFsyncTransientFailure(ErrorContext context) {
      transientFailureContexts.add(context);
    }

    @Override
    public void recordFsyncPermanentFailure(ErrorContext context) {
      permanentFailureContexts.add(context);
    }

    @Override
    public void recordEntryAppended(int entrySize) {}

    @Override
    public void recordCorruptedEntry() {}

    @Override
    public void recordSegmentRotation() {}

    @Override
    public void setCurrentSegmentEntryCount(long count) {}

    @Override
    public void setCurrentSegmentByteCount(long count) {}

    @Override
    public void setTotalSegmentCount(long count) {}

    @Override
    public void recordCorruptionType(CorruptionType type) {}

    @Override
    public void recordSegmentCorruption() {}

    @Override
    public void recordRecoveryCompleted(
        long durationMs, long segmentsScanned, long segmentsRecovered) {}
  }

  static class CountingFsyncOperation implements FsyncOperation {
    int fsyncCallCount = 0;
    IOException exceptionToThrow = null;

    @Override
    public void fsync() throws IOException {
      fsyncCallCount++;
      if (exceptionToThrow != null) {
        throw exceptionToThrow;
      }
    }

    void throwException(IOException ex) {
      this.exceptionToThrow = ex;
    }

    void reset() {
      fsyncCallCount = 0;
      exceptionToThrow = null;
    }
  }

  static class MultiAttemptFsyncOperation implements FsyncOperation {
    int attemptCount = 0;
    IOException exceptionToThrow = null;
    int failUntilAttempt = -1;

    @Override
    public void fsync() throws IOException {
      attemptCount++;
      if (attemptCount <= failUntilAttempt && exceptionToThrow != null) {
        throw exceptionToThrow;
      }
    }

    void throwExceptionUntilAttempt(int attempt, IOException ex) {
      this.failUntilAttempt = attempt;
      this.exceptionToThrow = ex;
    }

    void reset() {
      attemptCount = 0;
      exceptionToThrow = null;
      failUntilAttempt = -1;
    }
  }

  private MockMetricsRecorder mockMetrics;
  private CountingFsyncOperation fsyncOperation;

  @BeforeEach
  void setUp() {
    mockMetrics = new MockMetricsRecorder();
    fsyncOperation = new CountingFsyncOperation();
  }

  @Test
  @DisplayName("executeWithRetry succeeds on first attempt")
  void testSuccessOnFirstAttempt() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 2.0, mockMetrics);

    strategy.executeWithRetry(fsyncOperation);

    assertEquals(1, fsyncOperation.fsyncCallCount);
    assertEquals(1, mockMetrics.fsyncLatencies.size());
    assertEquals(0, mockMetrics.fsyncRetrySuccessCount);
  }

  @Test
  @DisplayName("executeWithRetry records fsync latency")
  void testRecordsFsyncLatency() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 2.0, mockMetrics);

    strategy.executeWithRetry(fsyncOperation);

    assertEquals(1, mockMetrics.fsyncLatencies.size());
    assertTrue(mockMetrics.fsyncLatencies.get(0) >= 0);
  }

  @Test
  @DisplayName("executeWithRetry succeeds after transient failure (RESOURCE_BUSY)")
  void testSuccessAfterTransientResourceBusy() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(1, new IOException("Resource temporarily unavailable"));

    strategy.executeWithRetry(operation);

    assertEquals(2, operation.attemptCount);
    assertEquals(1, mockMetrics.fsyncRetrySuccessCount);
    assertEquals(1, mockMetrics.transientFailureContexts.size());
  }

  @Test
  @DisplayName("executeWithRetry succeeds after transient failure (NO_MEMORY)")
  void testSuccessAfterTransientNoMemory() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(1, new IOException("Cannot allocate memory"));

    strategy.executeWithRetry(operation);

    assertEquals(2, operation.attemptCount);
    assertEquals(1, mockMetrics.fsyncRetrySuccessCount);
    assertEquals(1, mockMetrics.transientFailureContexts.size());
    assertEquals(ErrorContext.NO_MEMORY, mockMetrics.transientFailureContexts.get(0));
  }

  @Test
  @DisplayName("executeWithRetry throws on permanent failure (Permission denied)")
  void testThrowsOnPermanentPermissionDenied() {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 1.0, mockMetrics);
    fsyncOperation.throwException(new IOException("Permission denied"));

    assertThrows(Exception.class, () -> strategy.executeWithRetry(fsyncOperation));

    assertEquals(1, fsyncOperation.fsyncCallCount);
    assertEquals(1, mockMetrics.permanentFailureContexts.size());
  }

  @Test
  @DisplayName("executeWithRetry throws on permanent failure (DISK_FULL)")
  void testThrowsOnPermanentDiskFull() {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 1.0, mockMetrics);
    fsyncOperation.throwException(new IOException("No space left on device"));

    assertThrows(Exception.class, () -> strategy.executeWithRetry(fsyncOperation));

    assertEquals(1, fsyncOperation.fsyncCallCount);
    assertEquals(1, mockMetrics.permanentFailureContexts.size());
    assertEquals(ErrorContext.DISK_FULL, mockMetrics.permanentFailureContexts.get(0));
  }

  @Test
  @DisplayName(
      "executeWithRetry respects maxRetries boundary (0..maxRetries = maxRetries+1 attempts)")
  void testRespectsMaxRetries() {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(2, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(100, new IOException("Resource temporarily unavailable"));

    assertThrows(Exception.class, () -> strategy.executeWithRetry(operation));

    assertEquals(3, operation.attemptCount);
  }

  @Test
  @DisplayName("executeWithRetry with maxRetries=0 makes 1 attempt total")
  void testMaxRetriesZero() {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(0, 10, 1.0, mockMetrics);
    fsyncOperation.throwException(new IOException("Resource temporarily unavailable"));

    assertThrows(Exception.class, () -> strategy.executeWithRetry(fsyncOperation));

    assertEquals(1, fsyncOperation.fsyncCallCount);
  }

  @Test
  @DisplayName("executeWithRetry with larger backoff values avoids jitter edge case")
  void testLargerBackoffValues() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(1, 20, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(1, new IOException("Resource temporarily unavailable"));

    long startTime = System.currentTimeMillis();
    strategy.executeWithRetry(operation);
    long endTime = System.currentTimeMillis();

    assertEquals(2, operation.attemptCount);
    long elapsed = endTime - startTime;
    assertTrue(elapsed >= 20, "Should wait at least retryBackoffMs");
  }

  @Test
  @DisplayName("executeWithRetry with multiplier=1 uses constant backoff")
  void testConstantBackoff() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(1, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(1, new IOException("Resource temporarily unavailable"));

    strategy.executeWithRetry(operation);

    assertEquals(2, operation.attemptCount);
  }

  @Test
  @DisplayName("executeWithRetry multiple transient failures before success")
  void testMultipleTransientFailures() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(5, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(3, new IOException("Resource temporarily unavailable"));

    strategy.executeWithRetry(operation);

    assertEquals(4, operation.attemptCount);
    assertEquals(1, mockMetrics.fsyncRetrySuccessCount);
    assertEquals(3, mockMetrics.transientFailureContexts.size());
  }

  @Test
  @DisplayName("executeWithRetry permanent failure after transient failure stops retry")
  void testPermanentAfterTransient() {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation =
        new MultiAttemptFsyncOperation() {
          @Override
          public void fsync() throws IOException {
            attemptCount++;
            if (attemptCount == 1) {
              throw new IOException("Resource temporarily unavailable");
            } else {
              throw new IOException("Permission denied");
            }
          }
        };

    assertThrows(Exception.class, () -> strategy.executeWithRetry(operation));

    assertEquals(2, operation.attemptCount);
    assertEquals(1, mockMetrics.transientFailureContexts.size());
    assertEquals(1, mockMetrics.permanentFailureContexts.size());
  }

  @Test
  @DisplayName("executeWithRetry with null operation throws NullPointerException")
  void testNullOperationThrows() {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 2.0, mockMetrics);

    assertThrows(NullPointerException.class, () -> strategy.executeWithRetry(null));
  }

  @Test
  @DisplayName("FsyncRetryStrategyFactory creates ExponentialBackoffRetryStrategy")
  void testFactoryCreatesCorrectType() {
    WalConfiguration config =
        new WalConfiguration(
            10,
            1024 * 1024,
            "/tmp",
            io.writeahead.log.enums.strategies.FsyncStrategy.FSYNC_EVERY_ENTRY,
            "yyyy-MM-dd",
            3,
            10,
            2.0,
            RotationPolicyType.SIZE_BASED);
    MockMetricsRecorder testMetrics = new MockMetricsRecorder();

    FsyncRetryStrategy strategy = FsyncRetryStrategyFactory.create(config, testMetrics);

    assertInstanceOf(ExponentialBackoffRetryStrategy.class, strategy);
  }

  @Test
  @DisplayName("Thread.sleep() throws InterruptedException converted to IOException")
  void testSleepInterruptedThrowsIOException() {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(1, new IOException("EAGAIN"));

    Thread.currentThread().interrupt();

    assertThrows(IOException.class, () -> strategy.executeWithRetry(operation));

    assertTrue(Thread.interrupted(), "Thread interrupt flag should be set");
  }

  @Test
  @DisplayName("FsyncRetryStrategyFactory uses configuration values")
  void testFactoryUsesConfigurationValues() throws IOException {
    WalConfiguration config =
        new WalConfiguration(
            10,
            1024 * 1024,
            "/tmp",
            io.writeahead.log.enums.strategies.FsyncStrategy.FSYNC_EVERY_ENTRY,
            "yyyy-MM-dd",
            1,
            10,
            1.0,
            RotationPolicyType.SIZE_BASED);
    MockMetricsRecorder testMetrics = new MockMetricsRecorder();
    FsyncRetryStrategy strategy = FsyncRetryStrategyFactory.create(config, testMetrics);

    strategy.executeWithRetry(() -> {});

    assertEquals(1, testMetrics.fsyncLatencies.size());
  }

  @Test
  @DisplayName("FsyncRetryStrategyFactory with null config throws NullPointerException")
  void testFactoryNullConfigThrows() {
    MockMetricsRecorder testMetrics = new MockMetricsRecorder();

    assertThrows(
        NullPointerException.class, () -> FsyncRetryStrategyFactory.create(null, testMetrics));
  }

  @Test
  @DisplayName("executeWithRetry success metrics not recorded on first attempt")
  void testRetrySuccessNotRecordedOnFirstAttempt() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 2.0, mockMetrics);

    strategy.executeWithRetry(fsyncOperation);

    assertEquals(0, mockMetrics.fsyncRetrySuccessCount);
  }

  @Test
  @DisplayName("executeWithRetry success metrics recorded after retry")
  void testRetrySuccessRecordedAfterRetry() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(1, new IOException("Resource temporarily unavailable"));

    strategy.executeWithRetry(operation);

    assertEquals(1, mockMetrics.fsyncRetrySuccessCount);
  }

  @Test
  @DisplayName("executeWithRetry no transient failures when immediate success")
  void testNoTransientFailuresOnSuccess() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 2.0, mockMetrics);

    strategy.executeWithRetry(fsyncOperation);

    assertEquals(0, mockMetrics.transientFailureContexts.size());
  }

  @Test
  @DisplayName("executeWithRetry no permanent failures when transient succeeds")
  void testNoPermanentFailuresOnTransientSuccess() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(1, new IOException("Resource temporarily unavailable"));

    strategy.executeWithRetry(operation);

    assertEquals(0, mockMetrics.permanentFailureContexts.size());
  }

  @Test
  @DisplayName("executeWithRetry records all transient contexts")
  void testRecordsTransientContexts() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation =
        new MultiAttemptFsyncOperation() {
          @Override
          public void fsync() throws IOException {
            attemptCount++;
            if (attemptCount == 1) {
              throw new IOException("EAGAIN");
            } else if (attemptCount == 2) {
              throw new IOException("ENOMEM");
            }
          }
        };

    strategy.executeWithRetry(operation);

    assertEquals(2, mockMetrics.transientFailureContexts.size());
  }

  @Test
  @DisplayName("executeWithRetry different exception types handled correctly")
  void testDifferentExceptionTypes() {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 1.0, mockMetrics);

    CountingFsyncOperation enospcOperation = new CountingFsyncOperation();
    enospcOperation.throwException(new IOException("ENOSPC"));

    assertThrows(Exception.class, () -> strategy.executeWithRetry(enospcOperation));
    assertEquals(1, mockMetrics.permanentFailureContexts.size());
    assertEquals(ErrorContext.DISK_FULL, mockMetrics.permanentFailureContexts.get(0));
  }

  @Test
  @DisplayName("executeWithRetry exponential backoff with larger multiplier")
  void testExponentialBackoffLargerMultiplier() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(1, 10, 2.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(1, new IOException("Resource temporarily unavailable"));

    long startTime = System.currentTimeMillis();
    strategy.executeWithRetry(operation);
    long endTime = System.currentTimeMillis();

    assertEquals(2, operation.attemptCount);
    long elapsed = endTime - startTime;
    assertTrue(elapsed >= 10, "Should wait at least retryBackoffMs");
  }

  @Test
  @DisplayName("executeWithRetry records latency for each successful attempt")
  void testRecordsLatencyPerAttempt() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 1.0, mockMetrics);

    strategy.executeWithRetry(fsyncOperation);

    assertEquals(1, mockMetrics.fsyncLatencies.size());
  }

  @Test
  @DisplayName("executeWithRetry with maxRetries=1 allows 2 attempts (0 and 1)")
  void testMaxRetriesOne() {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(1, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(100, new IOException("Resource temporarily unavailable"));

    assertThrows(Exception.class, () -> strategy.executeWithRetry(operation));

    assertEquals(2, operation.attemptCount);
  }

  @Test
  @DisplayName("executeWithRetry EAGAIN error is transient")
  void testEAGAINIsTransient() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(1, new IOException("EAGAIN"));

    strategy.executeWithRetry(operation);

    assertEquals(1, mockMetrics.transientFailureContexts.size());
    assertEquals(ErrorContext.RESOURCE_BUSY, mockMetrics.transientFailureContexts.get(0));
  }

  @Test
  @DisplayName("executeWithRetry ENOMEM error is transient")
  void testENOMEMIsTransient() throws IOException {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(3, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(1, new IOException("ENOMEM"));

    strategy.executeWithRetry(operation);

    assertEquals(1, mockMetrics.transientFailureContexts.size());
    assertEquals(ErrorContext.NO_MEMORY, mockMetrics.transientFailureContexts.get(0));
  }

  @Test
  @DisplayName("Loop boundary: maxRetries=1 allows 2 total attempts (0 and 1)")
  void testMaxRetriesBoundaryOne() {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(1, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(100, new IOException("Resource temporarily unavailable"));

    assertThrows(Exception.class, () -> strategy.executeWithRetry(operation));

    assertEquals(2, operation.attemptCount);
    assertEquals(1, mockMetrics.permanentFailureContexts.size());
  }

  @Test
  @DisplayName(
      "Transient error on final attempt (maxRetries=1, fails on attempt 1) is recorded as permanent")
  void testTransientAtFinalAttemptTreatedAsPermanent() {
    ExponentialBackoffRetryStrategy strategy =
        new ExponentialBackoffRetryStrategy(1, 10, 1.0, mockMetrics);
    MultiAttemptFsyncOperation operation = new MultiAttemptFsyncOperation();
    operation.throwExceptionUntilAttempt(100, new IOException("EAGAIN"));

    assertThrows(Exception.class, () -> strategy.executeWithRetry(operation));

    assertEquals(2, operation.attemptCount);
    assertEquals(1, mockMetrics.transientFailureContexts.size());
    assertEquals(1, mockMetrics.permanentFailureContexts.size());
  }
}
