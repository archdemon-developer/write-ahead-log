package io.writeahead.log.fsync.executor;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.fsync.FsyncOperation;
import io.writeahead.log.fsync.executors.EveryBatchFsyncExecutor;
import io.writeahead.log.fsync.retryers.FsyncRetryStrategy;
import io.writeahead.log.models.FileStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EveryBatchFsyncExecutorTest {

  private static final int NO_FSYNC_CALLS = 0;
  private static final int SINGLE_BATCH_FSYNC = 1;
  private static final int TWO_BATCH_FSYNCS = 2;
  private static final int THREE_BATCH_FSYNCS = 3;
  private static final int TEN_BATCH_FSYNCS = 10;
  private static final int FIVE_BATCH_FSYNCS = 5;

  private Path tempFileSystemDirectory;
  private Path tempLogFile;
  private FileStream fileStreamForLogFile;
  private TrackingFsyncRetryStrategyForTesting testingRetryStrategy;
  private EveryBatchFsyncExecutor executorUnderTest;

  @BeforeEach
  void setUp() throws IOException {
    tempFileSystemDirectory = Files.createTempDirectory("fsync-executor-test-");
    tempLogFile = tempFileSystemDirectory.resolve("test.log");
    Files.createFile(tempLogFile);

    fileStreamForLogFile =
        new FileStream(
            new java.io.FileOutputStream(tempLogFile.toFile(), true),
            new java.io.DataOutputStream(new java.io.FileOutputStream(tempLogFile.toFile(), true)));

    testingRetryStrategy = new TrackingFsyncRetryStrategyForTesting();
    executorUnderTest = new EveryBatchFsyncExecutor(testingRetryStrategy, fileStreamForLogFile);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (fileStreamForLogFile != null) {
      try {
        fileStreamForLogFile.dataOutputStream().close();
      } catch (Exception ignored) {
      }
    }

    Files.walk(tempFileSystemDirectory)
        .sorted((a, b) -> b.compareTo(a))
        .forEach(
            path -> {
              try {
                Files.delete(path);
              } catch (Exception ignored) {
              }
            });
  }

  @Test
  void onEntryWrittenDoesNotTriggerFsync() throws IOException {
    executorUnderTest.onEntryWritten();

    assertEquals(NO_FSYNC_CALLS, testingRetryStrategy.totalCallsToRetryStrategy());
  }

  @Test
  void onBatchCompleteTriggersExactlyOneFsync() throws IOException {
    executorUnderTest.onBatchComplete();

    assertEquals(SINGLE_BATCH_FSYNC, testingRetryStrategy.totalCallsToRetryStrategy());
  }

  @Test
  void multipleEntriesWithoutBatchCompleteDoNotTriggerFsync() throws IOException {
    executorUnderTest.onEntryWritten();
    executorUnderTest.onEntryWritten();
    executorUnderTest.onEntryWritten();

    assertEquals(NO_FSYNC_CALLS, testingRetryStrategy.totalCallsToRetryStrategy());

    executorUnderTest.onBatchComplete();

    assertEquals(SINGLE_BATCH_FSYNC, testingRetryStrategy.totalCallsToRetryStrategy());
  }

  @Test
  void multipleEntriesFollowedByBatchCompleteTriggersSingleFsync() throws IOException {
    for (int entryIndex = 0; entryIndex < 10; entryIndex++) {
      executorUnderTest.onEntryWritten();
    }

    assertEquals(NO_FSYNC_CALLS, testingRetryStrategy.totalCallsToRetryStrategy());

    executorUnderTest.onBatchComplete();

    assertEquals(SINGLE_BATCH_FSYNC, testingRetryStrategy.totalCallsToRetryStrategy());
  }

  @Test
  void multipleBatchCompleteCallsEachTriggerFsync() throws IOException {
    executorUnderTest.onBatchComplete();
    executorUnderTest.onBatchComplete();
    executorUnderTest.onBatchComplete();

    assertEquals(THREE_BATCH_FSYNCS, testingRetryStrategy.totalCallsToRetryStrategy());
  }

  @Test
  void alternatingEntriesAndBatchesCompleteCorrectly() throws IOException {
    executorUnderTest.onEntryWritten();
    executorUnderTest.onBatchComplete();

    assertEquals(SINGLE_BATCH_FSYNC, testingRetryStrategy.totalCallsToRetryStrategy());

    executorUnderTest.onEntryWritten();
    executorUnderTest.onEntryWritten();
    executorUnderTest.onBatchComplete();

    assertEquals(TWO_BATCH_FSYNCS, testingRetryStrategy.totalCallsToRetryStrategy());
  }

  @Test
  void batchCompleteWithoutEntriesStillFsyncs() throws IOException {
    executorUnderTest.onBatchComplete();

    assertEquals(SINGLE_BATCH_FSYNC, testingRetryStrategy.totalCallsToRetryStrategy());
  }

  @Test
  void fsyncFailureFromRetryStrategyPropagatesAsIOException() throws IOException {
    testingRetryStrategy.configureToThrowOnNextCall(true);

    assertThrows(IOException.class, () -> executorUnderTest.onBatchComplete());
  }

  @Test
  void tenConsecutiveEntriesInFiveBatchesTriggerFiveFsyncs() throws IOException {
    for (int batchIndex = 0; batchIndex < 5; batchIndex++) {
      for (int entryIndex = 0; entryIndex < 2; entryIndex++) {
        executorUnderTest.onEntryWritten();
      }
      executorUnderTest.onBatchComplete();
    }

    assertEquals(FIVE_BATCH_FSYNCS, testingRetryStrategy.totalCallsToRetryStrategy());
  }

  @Test
  void batchFsyncOptimizationReducesFsyncCallsComparedToEveryEntry() throws IOException {
    for (int entryIndex = 0; entryIndex < 100; entryIndex++) {
      executorUnderTest.onEntryWritten();
    }

    assertEquals(NO_FSYNC_CALLS, testingRetryStrategy.totalCallsToRetryStrategy());

    executorUnderTest.onBatchComplete();

    assertEquals(SINGLE_BATCH_FSYNC, testingRetryStrategy.totalCallsToRetryStrategy());
  }

  @Test
  void entryWriteThenMultipleBatchCompletesCalls() throws IOException {
    executorUnderTest.onEntryWritten();
    executorUnderTest.onBatchComplete();
    executorUnderTest.onBatchComplete();

    assertEquals(TWO_BATCH_FSYNCS, testingRetryStrategy.totalCallsToRetryStrategy());
  }

  static class TrackingFsyncRetryStrategyForTesting implements FsyncRetryStrategy {
    private int totalRetryStrategyCalls = 0;
    private int totalFsyncFailureCount = 0;
    private boolean shouldThrowOnNextCall = false;

    @Override
    public void executeWithRetry(FsyncOperation operation) throws IOException {
      totalRetryStrategyCalls++;

      if (shouldThrowOnNextCall) {
        totalFsyncFailureCount++;
        throw new IOException("Test fsync failure");
      }

      operation.fsync();
    }

    public int totalCallsToRetryStrategy() {
      return totalRetryStrategyCalls;
    }

    public int totalFsyncFailures() {
      return totalFsyncFailureCount;
    }

    public void configureToThrowOnNextCall(boolean shouldThrow) {
      this.shouldThrowOnNextCall = shouldThrow;
    }
  }
}
