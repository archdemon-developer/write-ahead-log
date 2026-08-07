package io.writeahead.log.fsync.executors.tests;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.fsync.FsyncOperation;
import io.writeahead.log.fsync.executors.EveryBatchFsyncExecutor;
import io.writeahead.log.fsync.executors.EveryEntryFsyncExecutor;
import io.writeahead.log.fsync.executors.FsyncExecutor;
import io.writeahead.log.fsync.executors.FsyncExecutorFactory;
import io.writeahead.log.fsync.retryers.FsyncRetryStrategy;
import io.writeahead.log.models.FileStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FsyncExecutor - Fsync Strategy Implementations")
public class FsyncExecutorTest {

  static class MockFsyncRetryStrategy implements FsyncRetryStrategy {
    int executeWithRetryCallCount = 0;
    IOException exceptionToThrow = null;

    @Override
    public void executeWithRetry(FsyncOperation operation) throws IOException {
      if (exceptionToThrow != null) {
        throw exceptionToThrow;
      }
      executeWithRetryCallCount++;
    }

    void throwIOException(IOException ex) {
      this.exceptionToThrow = ex;
    }

    void reset() {
      executeWithRetryCallCount = 0;
      exceptionToThrow = null;
    }
  }

  private MockFsyncRetryStrategy mockRetryStrategy;
  private FileStream mockFileStream;

  @BeforeEach
  void setUp() {
    mockRetryStrategy = new MockFsyncRetryStrategy();
    mockFileStream = null;
  }

  @Test
  @DisplayName("EveryEntryFsyncExecutor.onEntryWritten() delegates to retry strategy")
  void testEveryEntryFsyncExecutorOnEntryWritten() throws IOException {
    EveryEntryFsyncExecutor executor =
        new EveryEntryFsyncExecutor(mockRetryStrategy, mockFileStream);

    executor.onEntryWritten();

    assertEquals(1, mockRetryStrategy.executeWithRetryCallCount);
  }

  @Test
  @DisplayName("EveryEntryFsyncExecutor.onEntryWritten() multiple calls")
  void testEveryEntryFsyncExecutorMultipleCalls() throws IOException {
    EveryEntryFsyncExecutor executor =
        new EveryEntryFsyncExecutor(mockRetryStrategy, mockFileStream);

    executor.onEntryWritten();
    executor.onEntryWritten();
    executor.onEntryWritten();

    assertEquals(3, mockRetryStrategy.executeWithRetryCallCount);
  }

  @Test
  @DisplayName("EveryEntryFsyncExecutor.onBatchComplete() does nothing (uses default)")
  void testEveryEntryFsyncExecutorOnBatchCompleteDoesNothing() throws IOException {
    EveryEntryFsyncExecutor executor =
        new EveryEntryFsyncExecutor(mockRetryStrategy, mockFileStream);

    executor.onBatchComplete();

    assertEquals(0, mockRetryStrategy.executeWithRetryCallCount);
  }

  @Test
  @DisplayName(
      "EveryEntryFsyncExecutor.onEntryWritten() propagates IOException from retry strategy")
  void testEveryEntryFsyncExecutorPropagatesIOException() throws IOException {
    EveryEntryFsyncExecutor executor =
        new EveryEntryFsyncExecutor(mockRetryStrategy, mockFileStream);
    IOException testException = new IOException("Fsync failed");
    mockRetryStrategy.throwIOException(testException);

    assertThrows(IOException.class, () -> executor.onEntryWritten());
  }

  @Test
  @DisplayName("EveryBatchFsyncExecutor.onBatchComplete() delegates to retry strategy")
  void testEveryBatchFsyncExecutorOnBatchComplete() throws IOException {
    EveryBatchFsyncExecutor executor =
        new EveryBatchFsyncExecutor(mockRetryStrategy, mockFileStream);

    executor.onBatchComplete();

    assertEquals(1, mockRetryStrategy.executeWithRetryCallCount);
  }

  @Test
  @DisplayName("EveryBatchFsyncExecutor.onBatchComplete() multiple calls")
  void testEveryBatchFsyncExecutorMultipleCalls() throws IOException {
    EveryBatchFsyncExecutor executor =
        new EveryBatchFsyncExecutor(mockRetryStrategy, mockFileStream);

    executor.onBatchComplete();
    executor.onBatchComplete();
    executor.onBatchComplete();

    assertEquals(3, mockRetryStrategy.executeWithRetryCallCount);
  }

  @Test
  @DisplayName("EveryBatchFsyncExecutor.onEntryWritten() does nothing (uses default)")
  void testEveryBatchFsyncExecutorOnEntryWrittenDoesNothing() throws IOException {
    EveryBatchFsyncExecutor executor =
        new EveryBatchFsyncExecutor(mockRetryStrategy, mockFileStream);

    executor.onEntryWritten();

    assertEquals(0, mockRetryStrategy.executeWithRetryCallCount);
  }

  @Test
  @DisplayName(
      "EveryBatchFsyncExecutor.onBatchComplete() propagates IOException from retry strategy")
  void testEveryBatchFsyncExecutorPropagatesIOException() throws IOException {
    EveryBatchFsyncExecutor executor =
        new EveryBatchFsyncExecutor(mockRetryStrategy, mockFileStream);
    IOException testException = new IOException("Fsync failed");
    mockRetryStrategy.throwIOException(testException);

    assertThrows(IOException.class, () -> executor.onBatchComplete());
  }

  @Test
  @DisplayName(
      "FsyncExecutorFactory.create() with FSYNC_EVERY_ENTRY creates EveryEntryFsyncExecutor")
  void testFactoryCreatesEveryEntryFsyncExecutor() {
    FsyncExecutor executor =
        FsyncExecutorFactory.create(
            FsyncStrategy.FSYNC_EVERY_ENTRY, mockRetryStrategy, mockFileStream);

    assertInstanceOf(EveryEntryFsyncExecutor.class, executor);
  }

  @Test
  @DisplayName(
      "FsyncExecutorFactory.create() with FSYNC_EVERY_BATCH creates EveryBatchFsyncExecutor")
  void testFactoryCreatesEveryBatchFsyncExecutor() {
    FsyncExecutor executor =
        FsyncExecutorFactory.create(
            FsyncStrategy.FSYNC_EVERY_BATCH, mockRetryStrategy, mockFileStream);

    assertInstanceOf(EveryBatchFsyncExecutor.class, executor);
  }

  @Test
  @DisplayName("FsyncExecutorFactory.create() returns distinct instances for different strategies")
  void testFactoryCreatesDifferentInstances() {
    FsyncExecutor executor1 =
        FsyncExecutorFactory.create(
            FsyncStrategy.FSYNC_EVERY_ENTRY, mockRetryStrategy, mockFileStream);
    FsyncExecutor executor2 =
        FsyncExecutorFactory.create(
            FsyncStrategy.FSYNC_EVERY_BATCH, mockRetryStrategy, mockFileStream);

    assertNotSame(executor1, executor2);
  }

  @Test
  @DisplayName("FsyncExecutorFactory created executor delegates correctly")
  void testFactoryCreatedExecutorDelegates() throws IOException {
    FsyncExecutor executor =
        FsyncExecutorFactory.create(
            FsyncStrategy.FSYNC_EVERY_ENTRY, mockRetryStrategy, mockFileStream);

    executor.onEntryWritten();

    assertEquals(1, mockRetryStrategy.executeWithRetryCallCount);
  }

  @Test
  @DisplayName("FsyncExecutor default implementations are no-ops")
  void testFsyncExecutorDefaultMethods() throws IOException {
    FsyncExecutor defaultExecutor = new FsyncExecutor() {};

    defaultExecutor.onEntryWritten();
    defaultExecutor.onBatchComplete();
  }

  @Test
  @DisplayName("EveryEntryFsyncExecutor and EveryBatchFsyncExecutor use same retry strategy")
  void testBothExecutorTypesUseRetryStrategy() throws IOException {
    EveryEntryFsyncExecutor entryExecutor =
        new EveryEntryFsyncExecutor(mockRetryStrategy, mockFileStream);
    EveryBatchFsyncExecutor batchExecutor =
        new EveryBatchFsyncExecutor(mockRetryStrategy, mockFileStream);

    entryExecutor.onEntryWritten();
    batchExecutor.onBatchComplete();

    assertEquals(2, mockRetryStrategy.executeWithRetryCallCount);
  }

  @Test
  @DisplayName("EveryEntryFsyncExecutor sequential entries all call retry strategy")
  void testEveryEntryFsyncExecutorSequentialEntries() throws IOException {
    EveryEntryFsyncExecutor executor =
        new EveryEntryFsyncExecutor(mockRetryStrategy, mockFileStream);

    for (int i = 0; i < 100; i++) {
      executor.onEntryWritten();
    }

    assertEquals(100, mockRetryStrategy.executeWithRetryCallCount);
  }

  @Test
  @DisplayName("EveryBatchFsyncExecutor sequential batches all call retry strategy")
  void testEveryBatchFsyncExecutorSequentialBatches() throws IOException {
    EveryBatchFsyncExecutor executor =
        new EveryBatchFsyncExecutor(mockRetryStrategy, mockFileStream);

    for (int i = 0; i < 50; i++) {
      executor.onBatchComplete();
    }

    assertEquals(50, mockRetryStrategy.executeWithRetryCallCount);
  }

  @Test
  @DisplayName("EveryEntryFsyncExecutor recovers after exception")
  void testEveryEntryFsyncExecutorRecoveryAfterException() throws IOException {
    EveryEntryFsyncExecutor executor =
        new EveryEntryFsyncExecutor(mockRetryStrategy, mockFileStream);

    executor.onEntryWritten();
    assertEquals(1, mockRetryStrategy.executeWithRetryCallCount);

    IOException failureException = new IOException("Fsync failed");
    mockRetryStrategy.throwIOException(failureException);
    assertThrows(IOException.class, () -> executor.onEntryWritten());
    assertEquals(1, mockRetryStrategy.executeWithRetryCallCount);

    mockRetryStrategy.reset();
    executor.onEntryWritten();
    assertEquals(1, mockRetryStrategy.executeWithRetryCallCount);
  }

  @Test
  @DisplayName("EveryBatchFsyncExecutor recovers after exception")
  void testEveryBatchFsyncExecutorRecoveryAfterException() throws IOException {
    EveryBatchFsyncExecutor executor =
        new EveryBatchFsyncExecutor(mockRetryStrategy, mockFileStream);

    executor.onBatchComplete();
    assertEquals(1, mockRetryStrategy.executeWithRetryCallCount);

    IOException failureException = new IOException("Fsync failed");
    mockRetryStrategy.throwIOException(failureException);
    assertThrows(IOException.class, () -> executor.onBatchComplete());
    assertEquals(1, mockRetryStrategy.executeWithRetryCallCount);

    mockRetryStrategy.reset();
    executor.onBatchComplete();
    assertEquals(1, mockRetryStrategy.executeWithRetryCallCount);
  }

  @Test
  @DisplayName("FsyncExecutorFactory.create() with null strategy throws NullPointerException")
  void testFactoryNullStrategyThrows() {
    assertThrows(
        NullPointerException.class,
        () -> FsyncExecutorFactory.create(null, mockRetryStrategy, mockFileStream));
  }
}
