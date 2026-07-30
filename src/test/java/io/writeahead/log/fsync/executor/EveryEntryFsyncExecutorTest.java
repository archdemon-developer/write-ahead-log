package io.writeahead.log.fsync.executor;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.fsync.EveryEntryFsyncExecutor;
import io.writeahead.log.fsync.FsyncOperation;
import io.writeahead.log.fsync.FsyncRetryStrategy;
import io.writeahead.log.models.FileStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EveryEntryFsyncExecutorTest {

    private static final int SINGLE_FSYNC_CALL = 1;
    private static final int ZERO_FSYNC_CALLS = 0;
    private static final int THREE_FSYNC_CALLS = 3;
    private static final int TEN_FSYNC_CALLS = 10;
    private static final int FIVE_FSYNC_CALLS = 5;

    private Path tempFileSystemDirectory;
    private Path tempLogFile;
    private FileStream fileStreamForLogFile;
    private TrackingFsyncRetryStrategyForTesting testingRetryStrategy;
    private EveryEntryFsyncExecutor executorUnderTest;

    @BeforeEach
    void setUp() throws IOException {
        tempFileSystemDirectory = Files.createTempDirectory("fsync-executor-test-");
        tempLogFile = tempFileSystemDirectory.resolve("test.log");
        Files.createFile(tempLogFile);

        fileStreamForLogFile = new FileStream(
                new java.io.FileOutputStream(tempLogFile.toFile(), true),
                new java.io.DataOutputStream(new java.io.FileOutputStream(tempLogFile.toFile(), true))
        );

        testingRetryStrategy = new TrackingFsyncRetryStrategyForTesting();
        executorUnderTest = new EveryEntryFsyncExecutor(testingRetryStrategy, fileStreamForLogFile);
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
    void onEntryWrittenTriggersImmediateFsync() throws IOException {
        executorUnderTest.onEntryWritten();

        assertEquals(SINGLE_FSYNC_CALL, testingRetryStrategy.totalCallsToRetryStrategy());
    }

    @Test
    void onBatchCompleteDoesNotTriggerFsync() throws IOException {
        executorUnderTest.onBatchComplete();

        assertEquals(ZERO_FSYNC_CALLS, testingRetryStrategy.totalCallsToRetryStrategy());
    }

    @Test
    void eachEntryWriteTriggersIndependentFsync() throws IOException {
        executorUnderTest.onEntryWritten();
        executorUnderTest.onEntryWritten();
        executorUnderTest.onEntryWritten();

        assertEquals(THREE_FSYNC_CALLS, testingRetryStrategy.totalCallsToRetryStrategy());
    }

    @Test
    void tenConsecutiveEntryWritesTriggerTenFsyncs() throws IOException {
        for (int entryIndex = 0; entryIndex < 10; entryIndex++) {
            executorUnderTest.onEntryWritten();
        }

        assertEquals(TEN_FSYNC_CALLS, testingRetryStrategy.totalCallsToRetryStrategy());
    }

    @Test
    void batchCompleteAfterEntriesDoesNotAddAdditionalFsync() throws IOException {
        executorUnderTest.onEntryWritten();
        executorUnderTest.onEntryWritten();
        executorUnderTest.onEntryWritten();

        assertEquals(THREE_FSYNC_CALLS, testingRetryStrategy.totalCallsToRetryStrategy());

        executorUnderTest.onBatchComplete();

        assertEquals(THREE_FSYNC_CALLS, testingRetryStrategy.totalCallsToRetryStrategy());
    }

    @Test
    void entryWriteThenBatchCompleteThenEntryWriteSequence() throws IOException {
        executorUnderTest.onEntryWritten();
        executorUnderTest.onBatchComplete();
        executorUnderTest.onEntryWritten();

        assertEquals(2, testingRetryStrategy.totalCallsToRetryStrategy());
    }

    @Test
    void fsyncFailureFromRetryStrategyPropagatesAsIOException() throws IOException {
        testingRetryStrategy.configureToThrowOnNextCall(true);

        assertThrows(IOException.class, () -> executorUnderTest.onEntryWritten());
    }

    @Test
    void firstEntryWriteFsyncSucceedsWithoutFailures() throws IOException {
        executorUnderTest.onEntryWritten();

        assertEquals(SINGLE_FSYNC_CALL, testingRetryStrategy.totalCallsToRetryStrategy());
        assertEquals(0, testingRetryStrategy.totalFsyncFailures());
    }

    @Test
    void everyEntryStrategyWithoutBatchOptimization() throws IOException {
        executorUnderTest.onEntryWritten();
        assertEquals(SINGLE_FSYNC_CALL, testingRetryStrategy.totalCallsToRetryStrategy());

        executorUnderTest.onEntryWritten();
        assertEquals(2, testingRetryStrategy.totalCallsToRetryStrategy());

        executorUnderTest.onEntryWritten();
        assertEquals(THREE_FSYNC_CALLS, testingRetryStrategy.totalCallsToRetryStrategy());

        executorUnderTest.onBatchComplete();
        assertEquals(THREE_FSYNC_CALLS, testingRetryStrategy.totalCallsToRetryStrategy());
    }

    @Test
    void maximalDurabilityPerEntryGuarantee() throws IOException {
        for (int entryIndex = 0; entryIndex < 5; entryIndex++) {
            executorUnderTest.onEntryWritten();

            assertEquals(entryIndex + 1, testingRetryStrategy.totalCallsToRetryStrategy());
        }
    }

    @Test
    void multipleBatchCompleteCallsWithoutEntryWritesDoNotTriggerFsync() throws IOException {
        executorUnderTest.onEntryWritten();
        executorUnderTest.onBatchComplete();
        executorUnderTest.onBatchComplete();
        executorUnderTest.onBatchComplete();

        assertEquals(SINGLE_FSYNC_CALL, testingRetryStrategy.totalCallsToRetryStrategy());
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