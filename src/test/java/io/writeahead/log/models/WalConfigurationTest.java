package io.writeahead.log.models;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.fsync.FsyncStrategy;
import io.writeahead.log.models.wal.WalConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WalConfigurationTest {

    private static final int INVALID_BATCH_SIZE_ZERO = 0;
    private static final int INVALID_BATCH_SIZE_NEGATIVE = -5;
    private static final int VALID_BATCH_SIZE_MINIMUM = 1;
    private static final int VALID_BATCH_SIZE_MEDIUM = 10;
    private static final long INVALID_SEGMENT_SIZE_ZERO = 0;
    private static final long INVALID_SEGMENT_SIZE_NEGATIVE = -1024;
    private static final long VALID_SEGMENT_SIZE_MINIMUM = 1;
    private static final long VALID_SEGMENT_SIZE_LARGE = 1024L * 1024L * 1024L;
    private static final int INVALID_MAX_RETRIES_NEGATIVE = -1;
    private static final int VALID_MAX_RETRIES_ZERO = 0;
    private static final int VALID_MAX_RETRIES_MEDIUM = 100;
    private static final long INVALID_RETRY_BACKOFF_MS_ZERO = 0;
    private static final long INVALID_RETRY_BACKOFF_MS_NEGATIVE = -10;
    private static final long VALID_RETRY_BACKOFF_MS_MINIMUM = 1;
    private static final long VALID_RETRY_BACKOFF_MS_MEDIUM = 10;
    private static final double INVALID_RETRY_BACKOFF_MULTIPLIER_ZERO = 0.0;
    private static final double INVALID_RETRY_BACKOFF_MULTIPLIER_NEGATIVE = -1.0;
    private static final double VALID_RETRY_BACKOFF_MULTIPLIER_MINIMUM = 0.1;
    private static final double VALID_RETRY_BACKOFF_MULTIPLIER_NO_EXPONENTIAL = 1.0;
    private static final double VALID_RETRY_BACKOFF_MULTIPLIER_LARGE = 10.0;
    private static final String DEFAULT_TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    private static final String CUSTOM_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private Path tempFileSystemDirectory;

    @BeforeEach
    void setUp() throws Exception {
        tempFileSystemDirectory = Files.createTempDirectory("wal-config-test-");
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.walk(tempFileSystemDirectory)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (Exception ignored) {
                    }
                });
    }

    @Test
    void batchSizeZeroThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempFileSystemDirectory.toString())
                                .batchSize(INVALID_BATCH_SIZE_ZERO)
                                .build());
    }

    @Test
    void batchSizeNegativeThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempFileSystemDirectory.toString())
                                .batchSize(INVALID_BATCH_SIZE_NEGATIVE)
                                .build());
    }

    @Test
    void batchSizeOneIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempFileSystemDirectory.toString())
                        .batchSize(VALID_BATCH_SIZE_MINIMUM)
                        .build();

        assertEquals(VALID_BATCH_SIZE_MINIMUM, config.batchSize());
    }

    @Test
    void batchSizeDefaultIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempFileSystemDirectory.toString()).build();

        assertTrue(config.batchSize() > 0);
    }

    @Test
    void maxSegmentSizeZeroThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempFileSystemDirectory.toString())
                                .maxSegmentSize(INVALID_SEGMENT_SIZE_ZERO)
                                .build());
    }

    @Test
    void maxSegmentSizeNegativeThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempFileSystemDirectory.toString())
                                .maxSegmentSize(INVALID_SEGMENT_SIZE_NEGATIVE)
                                .build());
    }

    @Test
    void maxSegmentSizeOneIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempFileSystemDirectory.toString())
                        .maxSegmentSize(VALID_SEGMENT_SIZE_MINIMUM)
                        .build();

        assertEquals(VALID_SEGMENT_SIZE_MINIMUM, config.maxSegmentSize());
    }

    @Test
    void maxSegmentSizeLargeIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempFileSystemDirectory.toString())
                        .maxSegmentSize(VALID_SEGMENT_SIZE_LARGE)
                        .build();

        assertEquals(VALID_SEGMENT_SIZE_LARGE, config.maxSegmentSize());
    }

    @Test
    void maxSegmentSizeDefaultIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempFileSystemDirectory.toString()).build();

        assertTrue(config.maxSegmentSize() > 0);
    }

    @Test
    void maxRetriesNegativeThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempFileSystemDirectory.toString())
                                .maxRetries(INVALID_MAX_RETRIES_NEGATIVE)
                                .build());
    }

    @Test
    void maxRetriesZeroIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempFileSystemDirectory.toString())
                        .maxRetries(VALID_MAX_RETRIES_ZERO)
                        .build();

        assertEquals(VALID_MAX_RETRIES_ZERO, config.maxRetries());
    }

    @Test
    void maxRetriesLargeIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempFileSystemDirectory.toString())
                        .maxRetries(VALID_MAX_RETRIES_MEDIUM)
                        .build();

        assertEquals(VALID_MAX_RETRIES_MEDIUM, config.maxRetries());
    }

    @Test
    void maxRetriesDefaultIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempFileSystemDirectory.toString()).build();

        assertTrue(config.maxRetries() >= 0);
    }

    @Test
    void retryBackoffMsZeroThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempFileSystemDirectory.toString())
                                .retryBackoffMs(INVALID_RETRY_BACKOFF_MS_ZERO)
                                .build());
    }

    @Test
    void retryBackoffMsNegativeThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempFileSystemDirectory.toString())
                                .retryBackoffMs(INVALID_RETRY_BACKOFF_MS_NEGATIVE)
                                .build());
    }

    @Test
    void retryBackoffMsOneIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempFileSystemDirectory.toString())
                        .retryBackoffMs(VALID_RETRY_BACKOFF_MS_MINIMUM)
                        .build();

        assertEquals(VALID_RETRY_BACKOFF_MS_MINIMUM, config.retryBackoffMs());
    }

    @Test
    void retryBackoffMsDefaultIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempFileSystemDirectory.toString()).build();

        assertTrue(config.retryBackoffMs() > 0);
    }

    @Test
    void retryBackoffMultiplierZeroThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempFileSystemDirectory.toString())
                                .retryBackoffMultiplier(INVALID_RETRY_BACKOFF_MULTIPLIER_ZERO)
                                .build());
    }

    @Test
    void retryBackoffMultiplierNegativeThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempFileSystemDirectory.toString())
                                .retryBackoffMultiplier(INVALID_RETRY_BACKOFF_MULTIPLIER_NEGATIVE)
                                .build());
    }

    @Test
    void retryBackoffMultiplierSmallPositiveIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempFileSystemDirectory.toString())
                        .retryBackoffMultiplier(VALID_RETRY_BACKOFF_MULTIPLIER_MINIMUM)
                        .build();

        assertEquals(VALID_RETRY_BACKOFF_MULTIPLIER_MINIMUM, config.retryBackoffMultiplier(), 0.01);
    }

    @Test
    void retryBackoffMultiplierOneIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempFileSystemDirectory.toString())
                        .retryBackoffMultiplier(VALID_RETRY_BACKOFF_MULTIPLIER_NO_EXPONENTIAL)
                        .build();

        assertEquals(VALID_RETRY_BACKOFF_MULTIPLIER_NO_EXPONENTIAL, config.retryBackoffMultiplier(), 0.01);
    }

    @Test
    void retryBackoffMultiplierLargeIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempFileSystemDirectory.toString())
                        .retryBackoffMultiplier(VALID_RETRY_BACKOFF_MULTIPLIER_LARGE)
                        .build();

        assertEquals(VALID_RETRY_BACKOFF_MULTIPLIER_LARGE, config.retryBackoffMultiplier(), 0.01);
    }

    @Test
    void retryBackoffMultiplierDefaultIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempFileSystemDirectory.toString()).build();

        assertTrue(config.retryBackoffMultiplier() > 0);
    }

    @Test
    void timestampFormatCanBeCustomized() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempFileSystemDirectory.toString())
                        .timestampFormat(CUSTOM_TIMESTAMP_FORMAT)
                        .build();

        assertEquals(CUSTOM_TIMESTAMP_FORMAT, config.timestampFormat());
    }

    @Test
    void timestampFormatDefaultIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempFileSystemDirectory.toString()).build();

        assertEquals(DEFAULT_TIMESTAMP_FORMAT, config.timestampFormat());
    }

    @Test
    void logDirNullThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WalConfiguration.Builder().logDir(null).build());
    }

    @Test
    void logDirValidIsAccepted() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempFileSystemDirectory.toString()).build();

        assertEquals(tempFileSystemDirectory.toString(), config.logDir());
    }

    @Test
    void multipleInvalidParametersThrowsOnFirstCheck() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempFileSystemDirectory.toString())
                                .batchSize(INVALID_BATCH_SIZE_ZERO)
                                .maxSegmentSize(INVALID_SEGMENT_SIZE_NEGATIVE)
                                .build());
    }

    @Test
    void fsyncStrategyDefaultIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempFileSystemDirectory.toString()).build();

        assertNotNull(config.fsyncStrategy());
    }

    @Test
    void fsyncStrategyEveryBatchIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempFileSystemDirectory.toString())
                        .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
                        .build();

        assertEquals(FsyncStrategy.FSYNC_EVERY_BATCH, config.fsyncStrategy());
    }

    @Test
    void completeValidConfigurationBuilds() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempFileSystemDirectory.toString())
                        .batchSize(VALID_BATCH_SIZE_MEDIUM)
                        .maxSegmentSize(10 * 1024 * 1024L)
                        .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
                        .timestampFormat(CUSTOM_TIMESTAMP_FORMAT)
                        .maxRetries(3)
                        .retryBackoffMs(VALID_RETRY_BACKOFF_MS_MEDIUM)
                        .retryBackoffMultiplier(2.0)
                        .build();

        assertNotNull(config);
        assertEquals(VALID_BATCH_SIZE_MEDIUM, config.batchSize());
        assertEquals(10 * 1024 * 1024L, config.maxSegmentSize());
        assertEquals(FsyncStrategy.FSYNC_EVERY_BATCH, config.fsyncStrategy());
        assertEquals(CUSTOM_TIMESTAMP_FORMAT, config.timestampFormat());
        assertEquals(3, config.maxRetries());
        assertEquals(VALID_RETRY_BACKOFF_MS_MEDIUM, config.retryBackoffMs());
        assertEquals(2.0, config.retryBackoffMultiplier(), 0.01);
    }

    @Test
    void defaultValuesAreAllValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempFileSystemDirectory.toString()).build();

        assertTrue(config.batchSize() > 0);
        assertTrue(config.maxSegmentSize() > 0);
        assertTrue(config.maxRetries() >= 0);
        assertTrue(config.retryBackoffMs() > 0);
        assertTrue(config.retryBackoffMultiplier() > 0);
        assertNotNull(config.fsyncStrategy());
        assertEquals(DEFAULT_TIMESTAMP_FORMAT, config.timestampFormat());
    }

    @Test
    void boundaryValuesAreValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempFileSystemDirectory.toString())
                        .batchSize(VALID_BATCH_SIZE_MINIMUM)
                        .maxSegmentSize(VALID_SEGMENT_SIZE_MINIMUM)
                        .maxRetries(VALID_MAX_RETRIES_ZERO)
                        .retryBackoffMs(VALID_RETRY_BACKOFF_MS_MINIMUM)
                        .retryBackoffMultiplier(VALID_RETRY_BACKOFF_MULTIPLIER_MINIMUM)
                        .build();

        assertEquals(VALID_BATCH_SIZE_MINIMUM, config.batchSize());
        assertEquals(VALID_SEGMENT_SIZE_MINIMUM, config.maxSegmentSize());
        assertEquals(VALID_MAX_RETRIES_ZERO, config.maxRetries());
        assertEquals(VALID_RETRY_BACKOFF_MS_MINIMUM, config.retryBackoffMs());
        assertEquals(VALID_RETRY_BACKOFF_MULTIPLIER_MINIMUM, config.retryBackoffMultiplier(), 0.01);
    }
}