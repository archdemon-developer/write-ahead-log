package io.writeahead.log.config;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.enums.strategies.RotationPolicyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WalConfiguration Tests — 100% Validation Coverage")
public class WalConfigurationTest {

  @Nested
  @DisplayName("Constructor Validation — All Parameters")
  class ConstructorValidation {

    @Test
    @DisplayName("rejects batchSize <= 0")
    void rejectsBatchSizeZero() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new WalConfiguration(
                      0,
                      10485760L,
                      "/tmp/wal",
                      FsyncStrategy.FSYNC_EVERY_BATCH,
                      "yyyy-MM-dd'T'HH:mm:ss.SSS",
                      3,
                      10L,
                      5.0,
                      RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("batchSize") && ex.getMessage().contains("> 0"));
    }

    @Test
    @DisplayName("rejects batchSize negative")
    void rejectsBatchSizeNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new WalConfiguration(
                      -5,
                      10485760L,
                      "/tmp/wal",
                      FsyncStrategy.FSYNC_EVERY_BATCH,
                      "yyyy-MM-dd'T'HH:mm:ss.SSS",
                      3,
                      10L,
                      5.0,
                      RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("batchSize"));
    }

    @Test
    @DisplayName("rejects maxSegmentSize <= 0")
    void rejectsMaxSegmentSizeZero() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new WalConfiguration(
                      10,
                      0L,
                      "/tmp/wal",
                      FsyncStrategy.FSYNC_EVERY_BATCH,
                      "yyyy-MM-dd'T'HH:mm:ss.SSS",
                      3,
                      10L,
                      5.0,
                      RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("maxSegmentSize") && ex.getMessage().contains("> 0"));
    }

    @Test
    @DisplayName("rejects logDir null")
    void rejectsLogDirNull() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new WalConfiguration(
                      10,
                      10485760L,
                      null,
                      FsyncStrategy.FSYNC_EVERY_BATCH,
                      "yyyy-MM-dd'T'HH:mm:ss.SSS",
                      3,
                      10L,
                      5.0,
                      RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("logDir") && ex.getMessage().contains("cannot be null"));
    }

    @Test
    @DisplayName("rejects logDir blank")
    void rejectsLogDirBlank() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new WalConfiguration(
                      10,
                      10485760L,
                      "   ",
                      FsyncStrategy.FSYNC_EVERY_BATCH,
                      "yyyy-MM-dd'T'HH:mm:ss.SSS",
                      3,
                      10L,
                      5.0,
                      RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("logDir"));
    }

    @Test
    @DisplayName("rejects fsyncStrategy null")
    void rejectsFsyncStrategyNull() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new WalConfiguration(
                      10,
                      10485760L,
                      "/tmp/wal",
                      null,
                      "yyyy-MM-dd'T'HH:mm:ss.SSS",
                      3,
                      10L,
                      5.0,
                      RotationPolicyType.SIZE_BASED));
      assertTrue(
          ex.getMessage().contains("fsyncStrategy") && ex.getMessage().contains("cannot be null"));
    }

    @Test
    @DisplayName("rejects timestampFormat null")
    void rejectsTimestampFormatNull() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new WalConfiguration(
                      10,
                      10485760L,
                      "/tmp/wal",
                      FsyncStrategy.FSYNC_EVERY_BATCH,
                      null,
                      3,
                      10L,
                      5.0,
                      RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("timestampFormat"));
    }

    @Test
    @DisplayName("rejects timestampFormat blank")
    void rejectsTimestampFormatBlank() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new WalConfiguration(
                      10,
                      10485760L,
                      "/tmp/wal",
                      FsyncStrategy.FSYNC_EVERY_BATCH,
                      "   ",
                      3,
                      10L,
                      5.0,
                      RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("timestampFormat"));
    }

    @Test
    @DisplayName("rejects maxRetries < 0")
    void rejectsMaxRetriesNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new WalConfiguration(
                      10,
                      10485760L,
                      "/tmp/wal",
                      FsyncStrategy.FSYNC_EVERY_BATCH,
                      "yyyy-MM-dd'T'HH:mm:ss.SSS",
                      -1,
                      10L,
                      5.0,
                      RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("maxRetries") && ex.getMessage().contains(">= 0"));
    }

    @Test
    @DisplayName("accepts maxRetries == 0")
    void acceptsMaxRetriesZero() {
      WalConfiguration config =
          new WalConfiguration(
              10,
              10485760L,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              0,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      assertEquals(0, config.maxRetries());
    }

    @Test
    @DisplayName("rejects retryBackoffMs <= 0")
    void rejectsRetryBackoffMsZero() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new WalConfiguration(
                      10,
                      10485760L,
                      "/tmp/wal",
                      FsyncStrategy.FSYNC_EVERY_BATCH,
                      "yyyy-MM-dd'T'HH:mm:ss.SSS",
                      3,
                      0L,
                      5.0,
                      RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("retryBackoffMs") && ex.getMessage().contains("> 0"));
    }

    @Test
    @DisplayName("rejects retryBackoffMultiplier <= 0")
    void rejectsRetryBackoffMultiplierZero() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new WalConfiguration(
                      10,
                      10485760L,
                      "/tmp/wal",
                      FsyncStrategy.FSYNC_EVERY_BATCH,
                      "yyyy-MM-dd'T'HH:mm:ss.SSS",
                      3,
                      10L,
                      0.0,
                      RotationPolicyType.SIZE_BASED));
      assertTrue(
          ex.getMessage().contains("retryBackoffMultiplier") && ex.getMessage().contains("> 0"));
    }

    @Test
    @DisplayName("rejects rotationPolicyType null")
    void rejectsRotationPolicyTypeNull() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new WalConfiguration(
                      10,
                      10485760L,
                      "/tmp/wal",
                      FsyncStrategy.FSYNC_EVERY_BATCH,
                      "yyyy-MM-dd'T'HH:mm:ss.SSS",
                      3,
                      10L,
                      5.0,
                      null));
      assertTrue(
          ex.getMessage().contains("rotationPolicyType")
              && ex.getMessage().contains("cannot be null"));
    }

    @Test
    @DisplayName("accepts valid configuration")
    void acceptsValidConfiguration() {
      WalConfiguration config =
          new WalConfiguration(
              10,
              10485760L,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      assertEquals(10, config.batchSize());
      assertEquals(10485760L, config.maxSegmentSize());
      assertEquals("/tmp/wal", config.logDir());
    }
  }

  @Nested
  @DisplayName("Builder Pattern Tests")
  class BuilderTests {

    @Test
    @DisplayName("builder() has default values")
    void builderHasDefaults() {
      WalConfiguration config = new WalConfiguration.Builder().logDir("/tmp/wal").build();

      assertEquals(10, config.batchSize());
      assertEquals(10 * 1024 * 1024L, config.maxSegmentSize());
      assertEquals("/tmp/wal", config.logDir());
      assertEquals(FsyncStrategy.FSYNC_EVERY_BATCH, config.fsyncStrategy());
      assertEquals("yyyy-MM-dd'T'HH:mm:ss.SSS", config.timestampFormat());
      assertEquals(3, config.maxRetries());
      assertEquals(10L, config.retryBackoffMs());
      assertEquals(5.0, config.retryBackoffMultiplier());
      assertEquals(RotationPolicyType.SIZE_BASED, config.rotationPolicyType());
    }

    @Test
    @DisplayName("builder().batchSize() overrides default")
    void builderOverridesBatchSize() {
      WalConfiguration config =
          new WalConfiguration.Builder().batchSize(25).logDir("/tmp/wal").build();
      assertEquals(25, config.batchSize());
    }

    @Test
    @DisplayName("builder().maxSegmentSize() overrides default")
    void builderOverridesMaxSegmentSize() {
      WalConfiguration config =
          new WalConfiguration.Builder()
              .maxSegmentSize(100 * 1024 * 1024L)
              .logDir("/tmp/wal")
              .build();
      assertEquals(100 * 1024 * 1024L, config.maxSegmentSize());
    }

    @Test
    @DisplayName("builder().fsyncStrategy() overrides default")
    void builderOverridesFsyncStrategy() {
      WalConfiguration config =
          new WalConfiguration.Builder()
              .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_ENTRY)
              .logDir("/tmp/wal")
              .build();
      assertEquals(FsyncStrategy.FSYNC_EVERY_ENTRY, config.fsyncStrategy());
    }

    @Test
    @DisplayName("builder().rotationPolicyType() overrides default")
    void builderOverridesRotationPolicyType() {
      WalConfiguration config =
          new WalConfiguration.Builder()
              .rotationPolicyType(RotationPolicyType.SIZE_BASED)
              .logDir("/tmp/wal")
              .build();
      assertEquals(RotationPolicyType.SIZE_BASED, config.rotationPolicyType());
    }

    @Test
    @DisplayName("builder().timestampFormat() overrides default")
    void builderOverridesTimestampFormat() {
      WalConfiguration config =
          new WalConfiguration.Builder()
              .timestampFormat("yyyy-MM-dd HH:mm:ss")
              .logDir("/tmp/wal")
              .build();
      assertEquals("yyyy-MM-dd HH:mm:ss", config.timestampFormat());
    }

    @Test
    @DisplayName("builder().maxRetries() overrides default")
    void builderOverridesMaxRetries() {
      WalConfiguration config =
          new WalConfiguration.Builder().maxRetries(5).logDir("/tmp/wal").build();
      assertEquals(5, config.maxRetries());
    }

    @Test
    @DisplayName("builder().retryBackoffMs() overrides default")
    void builderOverridesRetryBackoffMs() {
      WalConfiguration config =
          new WalConfiguration.Builder().retryBackoffMs(50L).logDir("/tmp/wal").build();
      assertEquals(50L, config.retryBackoffMs());
    }

    @Test
    @DisplayName("builder().retryBackoffMultiplier() overrides default")
    void builderOverridesRetryBackoffMultiplier() {
      WalConfiguration config =
          new WalConfiguration.Builder().retryBackoffMultiplier(2.5).logDir("/tmp/wal").build();
      assertEquals(2.5, config.retryBackoffMultiplier());
    }

    @Test
    @DisplayName("builder() chains multiple overrides")
    void builderChainsMultipleOverrides() {
      WalConfiguration config =
          new WalConfiguration.Builder()
              .batchSize(20)
              .maxSegmentSize(50 * 1024 * 1024L)
              .logDir("/var/log/wal")
              .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
              .timestampFormat("HH:mm:ss.SSS")
              .maxRetries(0)
              .retryBackoffMs(100L)
              .retryBackoffMultiplier(3.0)
              .rotationPolicyType(RotationPolicyType.SIZE_BASED)
              .build();

      assertEquals(20, config.batchSize());
      assertEquals(50 * 1024 * 1024L, config.maxSegmentSize());
      assertEquals("/var/log/wal", config.logDir());
      assertEquals(FsyncStrategy.FSYNC_EVERY_BATCH, config.fsyncStrategy());
      assertEquals("HH:mm:ss.SSS", config.timestampFormat());
      assertEquals(0, config.maxRetries());
      assertEquals(100L, config.retryBackoffMs());
      assertEquals(3.0, config.retryBackoffMultiplier());
      assertEquals(RotationPolicyType.SIZE_BASED, config.rotationPolicyType());
    }

    @Test
    @DisplayName("builder() validates on build()")
    void builderValidatesOnBuild() {

      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new WalConfiguration.Builder().batchSize(10).build());
      assertTrue(ex.getMessage().contains("logDir"));
    }

    @Test
    @DisplayName("builder().batchSize() rejects invalid value")
    void builderBatchSizeValidation() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new WalConfiguration.Builder().batchSize(0).logDir("/tmp/wal").build());
      assertTrue(ex.getMessage().contains("batchSize"));
    }
  }

  @Nested
  @DisplayName("Edge Cases & Boundary Values")
  class EdgeCases {

    @Test
    @DisplayName("handles minimum valid batchSize")
    void handlesMinimumBatchSize() {
      WalConfiguration config =
          new WalConfiguration(
              1,
              10485760L,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      assertEquals(1, config.batchSize());
    }

    @Test
    @DisplayName("handles maximum integer batchSize")
    void handlesMaximumBatchSize() {
      WalConfiguration config =
          new WalConfiguration(
              Integer.MAX_VALUE,
              10485760L,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      assertEquals(Integer.MAX_VALUE, config.batchSize());
    }

    @Test
    @DisplayName("handles minimum valid maxSegmentSize")
    void handlesMinimumSegmentSize() {
      WalConfiguration config =
          new WalConfiguration(
              10,
              1L,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      assertEquals(1L, config.maxSegmentSize());
    }

    @Test
    @DisplayName("handles very large maxSegmentSize")
    void handlesLargeSegmentSize() {
      WalConfiguration config =
          new WalConfiguration(
              10,
              Long.MAX_VALUE,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      assertEquals(Long.MAX_VALUE, config.maxSegmentSize());
    }

    @Test
    @DisplayName("handles very small retryBackoffMultiplier")
    void handlesSmallRetryBackoffMultiplier() {
      WalConfiguration config =
          new WalConfiguration(
              10,
              10485760L,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              0.1,
              RotationPolicyType.SIZE_BASED);
      assertEquals(0.1, config.retryBackoffMultiplier());
    }

    @Test
    @DisplayName("handles very large retryBackoffMultiplier")
    void handlesLargeRetryBackoffMultiplier() {
      WalConfiguration config =
          new WalConfiguration(
              10,
              10485760L,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              1000.0,
              RotationPolicyType.SIZE_BASED);
      assertEquals(1000.0, config.retryBackoffMultiplier());
    }

    @Test
    @DisplayName("handles single-character logDir")
    void handlesSingleCharLogDir() {
      WalConfiguration config =
          new WalConfiguration(
              10,
              10485760L,
              "/",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      assertEquals("/", config.logDir());
    }

    @Test
    @DisplayName("handles long logDir path")
    void handlesLongLogDirPath() {
      String longPath =
          "/this/is/a/very/long/path/to/the/wal/directory/with/many/segments/and/backups";
      WalConfiguration config =
          new WalConfiguration(
              10,
              10485760L,
              longPath,
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      assertEquals(longPath, config.logDir());
    }

    @Test
    @DisplayName("handles complex timestampFormat")
    void handlesComplexTimestampFormat() {
      String complexFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX VV z";
      WalConfiguration config =
          new WalConfiguration(
              10,
              10485760L,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              complexFormat,
              3,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      assertEquals(complexFormat, config.timestampFormat());
    }
  }

  @Nested
  @DisplayName("Record Properties & Equality")
  class RecordProperties {

    @Test
    @DisplayName("two configs with same values are equal")
    void equalConfigs() {
      WalConfiguration config1 =
          new WalConfiguration(
              10,
              10485760L,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      WalConfiguration config2 =
          new WalConfiguration(
              10,
              10485760L,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      assertEquals(config1, config2);
    }

    @Test
    @DisplayName("two configs with different values are not equal")
    void unequalConfigs() {
      WalConfiguration config1 =
          new WalConfiguration(
              10,
              10485760L,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      WalConfiguration config2 =
          new WalConfiguration(
              20,
              10485760L,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      assertNotEquals(config1, config2);
    }

    @Test
    @DisplayName("toString() produces string representation")
    void toStringWorks() {
      WalConfiguration config =
          new WalConfiguration(
              10,
              10485760L,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      String str = config.toString();
      assertNotNull(str);
      assertTrue(str.contains("WalConfiguration"));
    }

    @Test
    @DisplayName("hashCode() is consistent")
    void hashCodeConsistent() {
      WalConfiguration config =
          new WalConfiguration(
              10,
              10485760L,
              "/tmp/wal",
              FsyncStrategy.FSYNC_EVERY_BATCH,
              "yyyy-MM-dd'T'HH:mm:ss.SSS",
              3,
              10L,
              5.0,
              RotationPolicyType.SIZE_BASED);
      int hash1 = config.hashCode();
      int hash2 = config.hashCode();
      assertEquals(hash1, hash2);
    }
  }
}
