package io.writeahead.log.fsync.factory;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.fsync.*;
import io.writeahead.log.enums.FsyncStrategy;
import io.writeahead.log.fsync.ExponentialBackoffRetryStrategy;
import io.writeahead.log.metrics.SimpleWalMetrics;
import io.writeahead.log.models.FileStream;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FsyncExecutorFactoryTest {

    private Path tempDirectory;
    private File tempFile;
    private FileStream fileStream;

    @BeforeEach
    void setUp() throws Exception {
        tempDirectory = Files.createTempDirectory("fsync-factory-test-");
        tempFile = new File(tempDirectory.toFile(), "test.log");
        tempFile.createNewFile();

        FileOutputStream fileOutputStream = new FileOutputStream(tempFile, true);
        DataOutputStream dataOutputStream = new DataOutputStream(fileOutputStream);
        fileStream = new FileStream(fileOutputStream, dataOutputStream);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (fileStream != null) {
            try {
                fileStream.dataOutputStream().close();
            } catch (Exception ignored) {
            }
        }

        Files.walk(tempDirectory)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (Exception ignored) {
                    }
                });
    }

    @Test
    void createWithEveryBatchStrategyReturnsEveryBatchFsyncExecutor() {
        FsyncRetryStrategy fsyncRetryStrategy = new ExponentialBackoffRetryStrategy(
                3, 10, 1.0, new SimpleWalMetrics());

        FsyncExecutor createdFsyncExecutor = FsyncExecutorFactory.create(
                FsyncStrategy.FSYNC_EVERY_BATCH,
                fsyncRetryStrategy,
                fileStream);

        assertNotNull(createdFsyncExecutor);
        assertInstanceOf(EveryBatchFsyncExecutor.class, createdFsyncExecutor);
    }

    @Test
    void createWithEveryEntryStrategyReturnsEveryEntryFsyncExecutor() {
        FsyncRetryStrategy fsyncRetryStrategy = new ExponentialBackoffRetryStrategy(
                3, 10, 1.0, new SimpleWalMetrics());

        FsyncExecutor createdFsyncExecutor = FsyncExecutorFactory.create(
                FsyncStrategy.FSYNC_EVERY_ENTRY,
                fsyncRetryStrategy,
                fileStream);

        assertNotNull(createdFsyncExecutor);
        assertInstanceOf(EveryEntryFsyncExecutor.class, createdFsyncExecutor);
    }

    @Test
    void createReturnsFsyncExecutorInterface() {
        FsyncRetryStrategy fsyncRetryStrategy = new ExponentialBackoffRetryStrategy(
                3, 10, 1.0, new SimpleWalMetrics());

        FsyncExecutor createdFsyncExecutor = FsyncExecutorFactory.create(
                FsyncStrategy.FSYNC_EVERY_BATCH,
                fsyncRetryStrategy,
                fileStream);

        assertInstanceOf(FsyncExecutor.class, createdFsyncExecutor);
    }

    @Test
    void createEveryBatchReturnsNotNull() {
        FsyncRetryStrategy fsyncRetryStrategy = new ExponentialBackoffRetryStrategy(
                3, 10, 1.0, new SimpleWalMetrics());

        FsyncExecutor createdFsyncExecutor = FsyncExecutorFactory.create(
                FsyncStrategy.FSYNC_EVERY_BATCH,
                fsyncRetryStrategy,
                fileStream);

        assertNotNull(createdFsyncExecutor);
    }

    @Test
    void createEveryEntryReturnsNotNull() {
        FsyncRetryStrategy fsyncRetryStrategy = new ExponentialBackoffRetryStrategy(
                3, 10, 1.0, new SimpleWalMetrics());

        FsyncExecutor createdFsyncExecutor = FsyncExecutorFactory.create(
                FsyncStrategy.FSYNC_EVERY_ENTRY,
                fsyncRetryStrategy,
                fileStream);

        assertNotNull(createdFsyncExecutor);
    }

    @Test
    void createMultipleCallsSameStrategyReturnsDifferentInstances() {
        FsyncRetryStrategy fsyncRetryStrategy = new ExponentialBackoffRetryStrategy(
                3, 10, 1.0, new SimpleWalMetrics());

        FsyncExecutor firstCreatedFsyncExecutor = FsyncExecutorFactory.create(
                FsyncStrategy.FSYNC_EVERY_BATCH,
                fsyncRetryStrategy,
                fileStream);

        FsyncExecutor secondCreatedFsyncExecutor = FsyncExecutorFactory.create(
                FsyncStrategy.FSYNC_EVERY_BATCH,
                fsyncRetryStrategy,
                fileStream);

        assertNotSame(firstCreatedFsyncExecutor, secondCreatedFsyncExecutor);
    }

    @Test
    void createAllStrategiesProduceSomeExecutor() {
        FsyncRetryStrategy fsyncRetryStrategy = new ExponentialBackoffRetryStrategy(
                3, 10, 1.0, new SimpleWalMetrics());

        for (FsyncStrategy fsyncStrategy : FsyncStrategy.values()) {
            FsyncExecutor createdFsyncExecutor = FsyncExecutorFactory.create(
                    fsyncStrategy,
                    fsyncRetryStrategy,
                    fileStream);

            assertNotNull(createdFsyncExecutor);
            assertInstanceOf(FsyncExecutor.class, createdFsyncExecutor);
        }
    }
}