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
 * MILITARY-GRADE TEST SUITE FOR EVERYENTRYFSYNCEXECUTOR
 *
 * <p>Tests that fsync is called exactly when entries are written.
 * - onEntryWritten() SHOULD fsync immediately
 * - onBatchComplete() should NOT fsync
 * - Retry strategy is invoked per entry
 */
public class EveryEntryFsyncExecutorTest {

    private Path tempDir;
    private Path tempFile;
    private FileStream fileStream;
    private TrackingFsyncRetryStrategy retryStrategy;
    private EveryEntryFsyncExecutor executor;

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
        executor = new EveryEntryFsyncExecutor(retryStrategy, fileStream);
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
    void testOnEntryWrittenFsyncsImmediately() throws IOException {
        executor.onEntryWritten();

        assertEquals(1, retryStrategy.callCount(),
                "onEntryWritten() should trigger fsync immediately");
    }

    @Test
    void testOnBatchCompleteDoesNotFsync() throws IOException {
        executor.onBatchComplete();

        assertEquals(0, retryStrategy.callCount(),
                "onBatchComplete() should NOT trigger fsync");
    }

    @Test
    void testFsyncCalledPerEntry() throws IOException {
        executor.onEntryWritten();
        executor.onEntryWritten();
        executor.onEntryWritten();

        assertEquals(3, retryStrategy.callCount(),
                "Each onEntryWritten() should trigger one fsync");
    }

    @Test
    void testMultipleEntriesEachFsyncs() throws IOException {
        for (int i = 0; i < 10; i++) {
            executor.onEntryWritten();
        }

        assertEquals(10, retryStrategy.callCount(),
                "10 entries should trigger 10 fsyncs");
    }

    @Test
    void testBatchCompleteDoesNotAddFsync() throws IOException {
        executor.onEntryWritten();
        executor.onEntryWritten();
        executor.onEntryWritten();

        assertEquals(3, retryStrategy.callCount());

        // Batch complete should NOT add another fsync
        executor.onBatchComplete();

        assertEquals(3, retryStrategy.callCount(),
                "onBatchComplete() should not trigger additional fsync");
    }

    @Test
    void testEntryWrittenThenBatchCompleteThenEntryWritten() throws IOException {
        executor.onEntryWritten();  // fsync #1
        executor.onBatchComplete(); // no fsync
        executor.onEntryWritten();  // fsync #2

        assertEquals(2, retryStrategy.callCount(),
                "Only entry writes should trigger fsyncs");
    }

    @Test
    void testFsyncThrowsOnFailure() throws IOException {
        retryStrategy.setThrowException(true);

        assertThrows(IOException.class, () -> executor.onEntryWritten(),
                "Should propagate IOException from retry strategy");
    }

    @Test
    void testFirstEntryFsyncSucceeds() throws IOException {
        executor.onEntryWritten();

        assertEquals(1, retryStrategy.callCount());
        assertEquals(0, retryStrategy.failureCount());
    }

    @Test
    void testFsyncPerEntryWithoutBatchOptimization() throws IOException {
        // Simulate every-entry fsync strategy: no batching benefits
        // Entry 1, fsync
        executor.onEntryWritten();
        assertEquals(1, retryStrategy.callCount());

        // Entry 2, fsync
        executor.onEntryWritten();
        assertEquals(2, retryStrategy.callCount());

        // Entry 3, fsync
        executor.onEntryWritten();
        assertEquals(3, retryStrategy.callCount());

        // Batch complete, NO fsync (strategy already fsynced each entry)
        executor.onBatchComplete();
        assertEquals(3, retryStrategy.callCount());
    }

    @Test
    void testMaxDurabilityPerEntry() throws IOException {
        // Every entry write is immediately durable
        for (int i = 0; i < 5; i++) {
            executor.onEntryWritten();

            // After each entry write, it should be fsynced
            assertEquals(i + 1, retryStrategy.callCount(),
                    "Each entry should be fsynced immediately");
        }
    }

    @Test
    void testBatchCompleteMultipleTimes() throws IOException {
        executor.onEntryWritten();
        executor.onBatchComplete();
        executor.onBatchComplete();
        executor.onBatchComplete();

        // Only the entry write triggers fsync
        assertEquals(1, retryStrategy.callCount(),
                "Multiple batch completes should not trigger fsyncs");
    }

    // ============================================================================
    // HELPER: Tracking FsyncRetryStrategy (replaces mocks)
    // ============================================================================

    static class TrackingFsyncRetryStrategy implements FsyncRetryStrategy {
        private int callCount = 0;
        private int failureCount = 0;
        private boolean throwException = false;

        @Override
        public void executeWithRetry(FsyncOperation operation) throws IOException {
            callCount++;

            if (throwException) {
                failureCount++;
                throw new IOException("Test fsync failure");
            }

            // Actually execute the operation to make it realistic
            operation.fsync();
        }

        public int callCount() {
            return callCount;
        }

        public int failureCount() {
            return failureCount;
        }

        public void setThrowException(boolean throwException) {
            this.throwException = throwException;
        }
    }
}