package io.writeahead.log.fsync.executor;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.fsync.FsyncOperation;
import io.writeahead.log.fsync.FsyncRetryStrategy;
import io.writeahead.log.models.file.FileStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MILITARY-GRADE TEST SUITE FOR EVERYBATCHFSYNCEXECUTOR
 *
 * <p>Tests that fsync is called exactly when batches complete.
 * - onEntryWritten() should NOT fsync
 * - onBatchComplete() SHOULD fsync
 * - Retry strategy is invoked correctly
 */
public class EveryBatchFsyncExecutorTest {

    private Path tempDir;
    private Path tempFile;
    private FileStream fileStream;
    private TrackingFsyncRetryStrategy retryStrategy;
    private EveryBatchFsyncExecutor executor;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("fsync-executor-test-");
        tempFile = tempDir.resolve("test.log");
        Files.createFile(tempFile);

        fileStream = new FileStream(
                new java.io.FileOutputStream(tempFile.toFile(), true),
                new java.io.DataOutputStream(new java.io.FileOutputStream(tempFile.toFile(), true))
        );

        retryStrategy = new TrackingFsyncRetryStrategy();
        executor = new EveryBatchFsyncExecutor(retryStrategy, fileStream);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (fileStream != null) {
            try {
                fileStream.dataOutputStream().close();
            } catch (Exception ignored) {
            }
        }

        Files.walk(tempDir)
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
    void testOnEntryWrittenDoesNotCallRetry() throws IOException {
        executor.onEntryWritten();

        assertEquals(0, retryStrategy.callCount(),
                "onEntryWritten() should not trigger retry strategy");
    }

    @Test
    void testOnBatchCompleteCallsRetry() throws IOException {
        executor.onBatchComplete();

        assertEquals(1, retryStrategy.callCount(),
                "onBatchComplete() should call retry strategy once");
    }

    @Test
    void testMultipleEntriesFollowedByBatchCompleteOnly() throws IOException {
        executor.onEntryWritten();
        executor.onEntryWritten();
        executor.onEntryWritten();

        assertEquals(0, retryStrategy.callCount(),
                "Three onEntryWritten() calls should not trigger fsync");

        executor.onBatchComplete();

        assertEquals(1, retryStrategy.callCount(),
                "onBatchComplete() should trigger fsync exactly once");
    }

    @Test
    void testMultipleBatches() throws IOException {
        // First batch
        executor.onEntryWritten();
        executor.onEntryWritten();
        executor.onBatchComplete();

        assertEquals(1, retryStrategy.callCount());

        // Second batch
        executor.onEntryWritten();
        executor.onBatchComplete();

        assertEquals(2, retryStrategy.callCount(),
                "Two onBatchComplete() calls should trigger fsync twice");
    }

    @Test
    void testOnBatchCompleteMultipleTimes() throws IOException {
        executor.onBatchComplete();
        executor.onBatchComplete();
        executor.onBatchComplete();

        assertEquals(3, retryStrategy.callCount(),
                "Three onBatchComplete() calls should trigger fsync three times");
    }

    @Test
    void testEmptyBatchCompleteFsyncs() throws IOException {
        // Calling batch complete without any entries should still fsync
        executor.onBatchComplete();

        assertEquals(1, retryStrategy.callCount(),
                "onBatchComplete() should fsync even with empty batch");
    }

    @Test
    void testOnEntryWrittenNeverTriggersRetry() throws IOException {
        for (int i = 0; i < 100; i++) {
            executor.onEntryWritten();
        }

        assertEquals(0, retryStrategy.callCount(),
                "100 onEntryWritten() calls should never trigger fsync");
    }

    @Test
    void testBatchCompleteThrowsOnRetryFailure() throws IOException {
        retryStrategy.setThrowException(true);

        assertThrows(IOException.class, () -> executor.onBatchComplete(),
                "Should propagate IOException from retry strategy");
    }

    // ============================================================================
    // HELPER: Tracking FsyncRetryStrategy (replaces mocks)
    // ============================================================================

    static class TrackingFsyncRetryStrategy implements FsyncRetryStrategy {
        private int callCount = 0;
        private boolean throwException = false;

        @Override
        public void executeWithRetry(FsyncOperation operation) throws IOException {
            callCount++;

            if (throwException) {
                throw new IOException("Test fsync failure");
            }

            // Actually execute the operation to make it realistic
            operation.fsync();
        }

        public int callCount() {
            return callCount;
        }

        public void setThrowException(boolean throwException) {
            this.throwException = throwException;
        }
    }
}