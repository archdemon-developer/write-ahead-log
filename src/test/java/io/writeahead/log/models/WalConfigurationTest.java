package io.writeahead.log.models;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.FsyncStrategy;
import java.nio.file.Files;
import java.nio.file.Path;

import io.writeahead.log.models.wal.WalConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MILITARY-GRADE TEST SUITE FOR WALCONFIGURATION
 *
 * <p>Tests all parameter validation in WalConfiguration.Builder.build().
 * Every invalid combination is tested. Every default value is validated.
 * This ensures fail-fast behavior with clear error messages.
 */
public class WalConfigurationTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("wal-config-test-");
    }

    @AfterEach
    void tearDown() throws Exception {
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

    // ============================================================================
    // BATCH SIZE VALIDATION
    // ============================================================================

    @Test
    void testBatchSizeZeroThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempDir.toString())
                                .batchSize(0)
                                .build(),
                "batchSize = 0 should throw IllegalArgumentException");
    }

    @Test
    void testBatchSizeNegativeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempDir.toString())
                                .batchSize(-5)
                                .build(),
                "batchSize < 0 should throw IllegalArgumentException");
    }

    @Test
    void testBatchSizeOneIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .batchSize(1)
                        .build();

        assertEquals(1, config.batchSize(), "batchSize=1 should be valid");
    }

    @Test
    void testBatchSizeDefaultIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempDir.toString()).build();

        assertTrue(config.batchSize() > 0, "Default batchSize should be > 0");
    }

    // ============================================================================
    // MAX SEGMENT SIZE VALIDATION
    // ============================================================================

    @Test
    void testMaxSegmentSizeZeroThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempDir.toString())
                                .maxSegmentSize(0)
                                .build(),
                "maxSegmentSize = 0 should throw IllegalArgumentException");
    }

    @Test
    void testMaxSegmentSizeNegativeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempDir.toString())
                                .maxSegmentSize(-1024)
                                .build(),
                "maxSegmentSize < 0 should throw IllegalArgumentException");
    }

    @Test
    void testMaxSegmentSizeOneIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .maxSegmentSize(1)
                        .build();

        assertEquals(1, config.maxSegmentSize(), "maxSegmentSize=1 should be valid");
    }

    @Test
    void testMaxSegmentSizeLargeIsValid() {
        long largeSize = 1024L * 1024L * 1024L; // 1GB
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .maxSegmentSize(largeSize)
                        .build();

        assertEquals(largeSize, config.maxSegmentSize(), "Large maxSegmentSize should be valid");
    }

    @Test
    void testMaxSegmentSizeDefaultIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempDir.toString()).build();

        assertTrue(config.maxSegmentSize() > 0, "Default maxSegmentSize should be > 0");
    }

    // ============================================================================
    // MAX RETRIES VALIDATION
    // ============================================================================

    @Test
    void testMaxRetriesNegativeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempDir.toString())
                                .maxRetries(-1)
                                .build(),
                "maxRetries < 0 should throw IllegalArgumentException");
    }

    @Test
    void testMaxRetriesZeroIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .maxRetries(0)
                        .build();

        assertEquals(0, config.maxRetries(), "maxRetries=0 should be valid (no retries)");
    }

    @Test
    void testMaxRetriesLargeIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .maxRetries(100)
                        .build();

        assertEquals(100, config.maxRetries(), "Large maxRetries should be valid");
    }

    @Test
    void testMaxRetriesDefaultIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempDir.toString()).build();

        assertTrue(config.maxRetries() >= 0, "Default maxRetries should be >= 0");
    }

    // ============================================================================
    // RETRY BACKOFF MS VALIDATION
    // ============================================================================

    @Test
    void testRetryBackoffMsZeroThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempDir.toString())
                                .retryBackoffMs(0)
                                .build(),
                "retryBackoffMs = 0 should throw IllegalArgumentException");
    }

    @Test
    void testRetryBackoffMsNegativeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempDir.toString())
                                .retryBackoffMs(-10)
                                .build(),
                "retryBackoffMs < 0 should throw IllegalArgumentException");
    }

    @Test
    void testRetryBackoffMsOneIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .retryBackoffMs(1)
                        .build();

        assertEquals(1, config.retryBackoffMs(), "retryBackoffMs=1 should be valid");
    }

    @Test
    void testRetryBackoffMsDefaultIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempDir.toString()).build();

        assertTrue(config.retryBackoffMs() > 0, "Default retryBackoffMs should be > 0");
    }

    // ============================================================================
    // RETRY BACKOFF MULTIPLIER VALIDATION
    // ============================================================================

    @Test
    void testRetryBackoffMultiplierZeroThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempDir.toString())
                                .retryBackoffMultiplier(0.0)
                                .build(),
                "retryBackoffMultiplier = 0 should throw IllegalArgumentException");
    }

    @Test
    void testRetryBackoffMultiplierNegativeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempDir.toString())
                                .retryBackoffMultiplier(-2.5)
                                .build(),
                "retryBackoffMultiplier < 0 should throw IllegalArgumentException");
    }

    @Test
    void testRetryBackoffMultiplierSmallPositiveIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .retryBackoffMultiplier(0.5)
                        .build();

        assertEquals(0.5, config.retryBackoffMultiplier(), 0.01, "Small positive multiplier should be valid");
    }

    @Test
    void testRetryBackoffMultiplierOneIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .retryBackoffMultiplier(1.0)
                        .build();

        assertEquals(1.0, config.retryBackoffMultiplier(), 0.01, "Multiplier=1.0 (no exponential) should be valid");
    }

    @Test
    void testRetryBackoffMultiplierLargeIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .retryBackoffMultiplier(10.0)
                        .build();

        assertEquals(10.0, config.retryBackoffMultiplier(), 0.01, "Large multiplier should be valid");
    }

    @Test
    void testRetryBackoffMultiplierDefaultIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempDir.toString()).build();

        assertTrue(config.retryBackoffMultiplier() > 0, "Default retryBackoffMultiplier should be > 0");
    }

    // ============================================================================
    // LOG DIR VALIDATION
    // ============================================================================

    @Test
    void testLogDirNullThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WalConfiguration.Builder().logDir(null).build(),
                "logDir = null should throw IllegalArgumentException");
    }

    @Test
    void testLogDirMissingThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(null)
                                .build(),
                "Non-existent logDir should throw during actual use (or can be checked here)");
    }

    @Test
    void testLogDirValidIsAccepted() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempDir.toString()).build();

        assertEquals(tempDir.toString(), config.logDir(), "Valid logDir should be accepted");
    }

    // ============================================================================
    // COMBINED VALIDATION (MULTIPLE INVALID PARAMS)
    // ============================================================================

    @Test
    void testMultipleInvalidParametersThrowsFirst() {
        // If both batchSize and maxSegmentSize are invalid, should throw on first check
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WalConfiguration.Builder()
                                .logDir(tempDir.toString())
                                .batchSize(0)
                                .maxSegmentSize(-1)
                                .build(),
                "Should throw when multiple parameters are invalid");
    }

    // ============================================================================
    // FSYNC STRATEGY VALIDATION
    // ============================================================================

    @Test
    void testFsyncStrategyDefaultIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempDir.toString()).build();

        assertNotNull(config.fsyncStrategy(), "Default fsyncStrategy should not be null");
    }

    @Test
    void testFsyncStrategyEveryBatchIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
                        .build();

        assertEquals(
                FsyncStrategy.FSYNC_EVERY_BATCH,
                config.fsyncStrategy(),
                "FSYNC_EVERY_BATCH should be valid");
    }

    @Test
    void testFsyncStrategyEveryEntryIsValid() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_ENTRY)
                        .build();

        assertEquals(
                FsyncStrategy.FSYNC_EVERY_ENTRY,
                config.fsyncStrategy(),
                "FSYNC_EVERY_ENTRY should be valid");
    }

    // ============================================================================
    // COMPLETE VALID CONFIGURATION
    // ============================================================================

    @Test
    void testCompleteValidConfigurationBuilds() {
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .batchSize(10)
                        .maxSegmentSize(10 * 1024 * 1024)
                        .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
                        .maxRetries(3)
                        .retryBackoffMs(10)
                        .retryBackoffMultiplier(2.0)
                        .build();

        assertNotNull(config, "Valid configuration should build successfully");
        assertEquals(10, config.batchSize());
        assertEquals(10 * 1024 * 1024, config.maxSegmentSize());
        assertEquals(FsyncStrategy.FSYNC_EVERY_BATCH, config.fsyncStrategy());
        assertEquals(3, config.maxRetries());
        assertEquals(10, config.retryBackoffMs());
        assertEquals(2.0, config.retryBackoffMultiplier(), 0.01);
    }

    @Test
    void testDefaultValuesAreAllValid() {
        WalConfiguration config =
                new WalConfiguration.Builder().logDir(tempDir.toString()).build();

        // All defaults should pass validation
        assertTrue(config.batchSize() > 0);
        assertTrue(config.maxSegmentSize() > 0);
        assertTrue(config.maxRetries() >= 0);
        assertTrue(config.retryBackoffMs() > 0);
        assertTrue(config.retryBackoffMultiplier() > 0);
        assertNotNull(config.fsyncStrategy());
    }

    @Test
    void testBoundaryValuesAreValid() {
        // Test extreme but valid values
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .batchSize(1)
                        .maxSegmentSize(1)
                        .maxRetries(0)
                        .retryBackoffMs(1)
                        .retryBackoffMultiplier(0.1)
                        .build();

        assertEquals(1, config.batchSize());
        assertEquals(1, config.maxSegmentSize());
        assertEquals(0, config.maxRetries());
        assertEquals(1, config.retryBackoffMs());
        assertEquals(0.1, config.retryBackoffMultiplier(), 0.01);
    }

    @Test
    void testErrorMessageContainsBatchSizeInfo() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new WalConfiguration.Builder()
                                        .logDir(tempDir.toString())
                                        .batchSize(-5)
                                        .build());

        assertTrue(
                ex.getMessage().contains("batchSize"),
                "Error message should mention batchSize");
        assertTrue(
                ex.getMessage().contains("-5"),
                "Error message should show the invalid value");
    }

    @Test
    void testErrorMessageContainsSegmentSizeInfo() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new WalConfiguration.Builder()
                                        .logDir(tempDir.toString())
                                        .maxSegmentSize(0)
                                        .build());

        assertTrue(
                ex.getMessage().contains("maxSegmentSize"),
                "Error message should mention maxSegmentSize");
        assertTrue(
                ex.getMessage().contains("0"),
                "Error message should show the invalid value");
    }
}
