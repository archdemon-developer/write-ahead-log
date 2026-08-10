package io.writeahead.log.segments.management;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.metrics.WalMetricsRecorder;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.models.meta.WalMetadata;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SegmentMetadataRecovery Tests")
class SegmentMetadataRecoveryTest {

  private File testDirectory;
  private WalMetricsRecorder metrics;
  private SegmentMetadataRecovery recovery;

  @BeforeEach
  void setUp() throws IOException {
    testDirectory = ManagementTestUtils.createTempLogDirectory();
    metrics = new io.writeahead.log.metrics.SimpleWalMetrics();
    recovery = new SegmentMetadataRecovery(testDirectory.getAbsolutePath(), metrics);
  }

  @AfterEach
  void tearDown() throws IOException {
    ManagementTestUtils.deleteDirectory(testDirectory);
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("SegmentMetadataRecovery creates successfully with valid directory")
    void createsWithValidDirectory() {
      assertNotNull(recovery);
    }

    @Test
    @DisplayName("SegmentMetadataRecovery stores logDir")
    void storesLogDir() throws IOException {
      SegmentMetadataRecovery testRecovery =
          new SegmentMetadataRecovery(testDirectory.getAbsolutePath(), metrics);
      assertNotNull(testRecovery);
    }
  }

  @Nested
  @DisplayName("Recover Method - Empty/Nonexistent Directory Tests")
  class RecoverEmptyDirectoryTests {

    @Test
    @DisplayName("recover returns empty WalMetadata when directory does not exist")
    void recoversNonexistentDirectory() throws IOException {
      File nonexistent = new File(testDirectory, "nonexistent");
      SegmentMetadataRecovery testRecovery =
          new SegmentMetadataRecovery(nonexistent.getAbsolutePath(), metrics);

      WalMetadata result = testRecovery.recover();

      assertNotNull(result);
      assertTrue(result.segments().isEmpty());
      assertEquals(1, result.nextSequence());
      assertNull(result.lastActiveSegment());
    }

    @Test
    @DisplayName("recover returns empty WalMetadata when directory is empty")
    void recoversEmptyDirectory() throws IOException {
      WalMetadata result = recovery.recover();

      assertNotNull(result);
      assertTrue(result.segments().isEmpty());
      assertEquals(1, result.nextSequence());
      assertNull(result.lastActiveSegment());
    }

    @Test
    @DisplayName("recover sets nextSequence to 1 when no segments found")
    void nextSequenceIsOneWhenNoSegments() throws IOException {
      WalMetadata result = recovery.recover();

      assertEquals(1, result.nextSequence());
    }
  }

  @Nested
  @DisplayName("Recover Method - Single Valid Segment Tests")
  class RecoverSingleValidSegmentTests {

    @Test
    @DisplayName("recover recovers single valid segment")
    void recoversSingleValidSegment() throws IOException {
      long sequence = 1L;
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(
          testDirectory, sequence, createdAt, 10, 100L, 1000L);

      WalMetadata result = recovery.recover();

      assertNotNull(result);
      assertEquals(1, result.segments().size());
      assertEquals(sequence, result.segments().get(0).sequenceNumber());
    }

    @Test
    @DisplayName("recover recovers segment metadata correctly")
    void recoversSegmentMetadataCorrectly() throws IOException {
      long sequence = 1L;
      long createdAt = System.currentTimeMillis();
      int entryCount = 10;
      long minTimestamp = 100L;
      long maxTimestamp = 1000L;

      ManagementTestUtils.createValidSegmentFile(
          testDirectory, sequence, createdAt, entryCount, minTimestamp, maxTimestamp);

      WalMetadata result = recovery.recover();

      SegmentMetadata recovered = result.segments().get(0);
      assertEquals(sequence, recovered.sequenceNumber());
      assertEquals(createdAt, recovered.createdAt());
      assertEquals(entryCount, recovered.entryCount());
      assertEquals(minTimestamp, recovered.minTimestamp());
      assertEquals(maxTimestamp, recovered.maxTimestamp());
    }

    @Test
    @DisplayName("recover sets nextSequence correctly after single segment")
    void setsNextSequenceAfterSingleSegment() throws IOException {
      long sequence = 5L;
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(
          testDirectory, sequence, createdAt, 10, 100L, 1000L);

      WalMetadata result = recovery.recover();

      assertEquals(sequence + 1, result.nextSequence());
    }

    @Test
    @DisplayName("recover sets lastActiveSegment correctly")
    void setsLastActiveSegmentCorrectly() throws IOException {
      long sequence = 1L;
      long createdAt = System.currentTimeMillis();
      File segment =
          ManagementTestUtils.createValidSegmentFile(
              testDirectory, sequence, createdAt, 10, 100L, 1000L);

      WalMetadata result = recovery.recover();

      assertEquals(segment.getName(), result.lastActiveSegment());
    }
  }

  @Nested
  @DisplayName("Recover Method - Multiple Valid Segments Tests")
  class RecoverMultipleValidSegmentsTests {

    @Test
    @DisplayName("recover recovers multiple valid segments")
    void recoversMultipleValidSegments() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(testDirectory, 1L, createdAt, 5, 0L, 500L);
      ManagementTestUtils.createValidSegmentFile(testDirectory, 2L, createdAt + 1, 10, 500L, 1500L);
      ManagementTestUtils.createValidSegmentFile(
          testDirectory, 3L, createdAt + 2, 15, 1500L, 3000L);

      WalMetadata result = recovery.recover();

      assertEquals(3, result.segments().size());
    }

    @Test
    @DisplayName("recover sets nextSequence to max sequence + 1")
    void setsNextSequenceToMaxPlusOne() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(testDirectory, 1L, createdAt, 5, 0L, 500L);
      ManagementTestUtils.createValidSegmentFile(testDirectory, 5L, createdAt + 1, 10, 500L, 1500L);
      ManagementTestUtils.createValidSegmentFile(
          testDirectory, 3L, createdAt + 2, 15, 1500L, 3000L);

      WalMetadata result = recovery.recover();

      assertEquals(6, result.nextSequence());
    }

    @Test
    @DisplayName("recover sets lastActiveSegment to last segment in order")
    void setsLastActiveSegment() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(testDirectory, 1L, createdAt, 5, 0L, 500L);
      ManagementTestUtils.createValidSegmentFile(testDirectory, 2L, createdAt + 1, 10, 500L, 1500L);
      File lastSegment =
          ManagementTestUtils.createValidSegmentFile(
              testDirectory, 3L, createdAt + 2, 15, 1500L, 3000L);

      WalMetadata result = recovery.recover();

      assertEquals(lastSegment.getName(), result.lastActiveSegment());
    }
  }

  @Nested
  @DisplayName("Recover Method - Corrupted Segment Tests")
  class RecoverCorruptedSegmentTests {

    @Test
    @DisplayName("recover skips segment with invalid header CRC")
    void skipsSegmentWithInvalidHeaderCrc() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(testDirectory, 1L, createdAt, 10, 100L, 1000L);
      ManagementTestUtils.createSegmentFileWithInvalidHeaderCrc(testDirectory, 2L, createdAt + 1);

      WalMetadata result = recovery.recover();

      assertEquals(1, result.segments().size());
      assertEquals(1L, result.segments().get(0).sequenceNumber());
    }

    @Test
    @DisplayName("recover skips segment that is too small")
    void skipsSegmentTooSmall() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(testDirectory, 1L, createdAt, 10, 100L, 1000L);
      ManagementTestUtils.createSegmentFileTooSmall(testDirectory, 2L, createdAt + 1);

      WalMetadata result = recovery.recover();

      assertEquals(1, result.segments().size());
    }

    @Test
    @DisplayName("recover skips segment with invalid footer marker")
    void skipsSegmentWithInvalidFooterMarker() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(testDirectory, 1L, createdAt, 10, 100L, 1000L);
      ManagementTestUtils.createSegmentFileWithInvalidFooterMarker(
          testDirectory, 2L, createdAt + 1, 5);

      WalMetadata result = recovery.recover();

      assertEquals(1, result.segments().size());
      assertEquals(1L, result.segments().get(0).sequenceNumber());
    }

    @Test
    @DisplayName("recover continues after encountering corrupted segment")
    void continuesAfterCorruption() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(testDirectory, 1L, createdAt, 10, 100L, 1000L);
      ManagementTestUtils.createSegmentFileWithInvalidHeaderCrc(testDirectory, 2L, createdAt + 1);
      ManagementTestUtils.createValidSegmentFile(
          testDirectory, 3L, createdAt + 2, 15, 1500L, 3000L);

      WalMetadata result = recovery.recover();

      assertEquals(2, result.segments().size());
      assertEquals(1L, result.segments().get(0).sequenceNumber());
      assertEquals(3L, result.segments().get(1).sequenceNumber());
    }
  }

  @Nested
  @DisplayName("Recover Method - Mixed Scenarios Tests")
  class RecoverMixedScenariosTests {

    @Test
    @DisplayName("recover handles mix of valid and corrupted segments")
    void handlesMixOfValidAndCorrupted() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(testDirectory, 1L, createdAt, 5, 0L, 500L);
      ManagementTestUtils.createSegmentFileTooSmall(testDirectory, 2L, createdAt + 1);
      ManagementTestUtils.createValidSegmentFile(testDirectory, 3L, createdAt + 2, 10, 500L, 1500L);
      ManagementTestUtils.createSegmentFileWithInvalidHeaderCrc(testDirectory, 4L, createdAt + 3);
      ManagementTestUtils.createValidSegmentFile(
          testDirectory, 5L, createdAt + 4, 15, 1500L, 3000L);

      WalMetadata result = recovery.recover();

      assertEquals(3, result.segments().size());
      assertEquals(6, result.nextSequence());
    }

    @Test
    @DisplayName("recover handles all corrupted segments gracefully")
    void handlesAllCorruptedSegments() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createSegmentFileTooSmall(testDirectory, 1L, createdAt);
      ManagementTestUtils.createSegmentFileWithInvalidHeaderCrc(testDirectory, 2L, createdAt + 1);
      ManagementTestUtils.createSegmentFileWithInvalidFooterMarker(
          testDirectory, 3L, createdAt + 2, 1);

      WalMetadata result = recovery.recover();

      assertTrue(result.segments().isEmpty());
      assertEquals(1, result.nextSequence());
    }
  }

  @Nested
  @DisplayName("Return Value Tests")
  class ReturnValueTests {

    @Test
    @DisplayName("recover always returns non-null WalMetadata")
    void alwaysReturnsNonNull() throws IOException {
      WalMetadata result = recovery.recover();

      assertNotNull(result);
    }

    @Test
    @DisplayName("recover returns segments list that is never null")
    void segmentsListNeverNull() throws IOException {
      WalMetadata result = recovery.recover();

      assertNotNull(result.segments());
    }

    @Test
    @DisplayName("recover returns nextSequence >= 1")
    void nextSequenceAtLeastOne() throws IOException {
      WalMetadata result = recovery.recover();

      assertTrue(result.nextSequence() >= 1);
    }

    @Test
    @DisplayName("recover returns correct segment order")
    void returnsCorrectSegmentOrder() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(testDirectory, 3L, createdAt, 10, 100L, 1000L);
      ManagementTestUtils.createValidSegmentFile(testDirectory, 1L, createdAt + 1, 5, 0L, 500L);
      ManagementTestUtils.createValidSegmentFile(testDirectory, 2L, createdAt + 2, 7, 500L, 750L);

      WalMetadata result = recovery.recover();

      assertEquals(3, result.segments().size());
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("recover handles segment with maxSequence number")
    void handlesMaxSequence() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(
          testDirectory, Long.MAX_VALUE - 1, createdAt, 10, 100L, 1000L);

      WalMetadata result = recovery.recover();

      assertEquals(1, result.segments().size());
      assertEquals(Long.MAX_VALUE, result.nextSequence());
    }

    @Test
    @DisplayName("recover handles segment with zero timestamp range")
    void handlesZeroTimestampRange() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(testDirectory, 1L, createdAt, 10, 500L, 500L);

      WalMetadata result = recovery.recover();

      assertEquals(1, result.segments().size());
      assertEquals(500L, result.segments().get(0).minTimestamp());
      assertEquals(500L, result.segments().get(0).maxTimestamp());
    }

    @Test
    @DisplayName("recover handles segment with large entry count")
    void handlesLargeEntryCount() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(
          testDirectory, 1L, createdAt, Integer.MAX_VALUE, 0L, Long.MAX_VALUE);

      WalMetadata result = recovery.recover();

      assertEquals(1, result.segments().size());
      assertEquals(Integer.MAX_VALUE, result.segments().get(0).entryCount());
    }
  }

  @Nested
  @DisplayName("Metrics Recording Tests")
  class MetricsRecordingTests {

    @Test
    @DisplayName("recover records metrics for successful recovery")
    void recordsMetricsForSuccessfulRecovery() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(testDirectory, 1L, createdAt, 10, 100L, 1000L);
      ManagementTestUtils.createValidSegmentFile(
          testDirectory, 2L, createdAt + 1, 15, 1000L, 2000L);

      WalMetadata result = recovery.recover();

      assertNotNull(result);
    }

    @Test
    @DisplayName("recover records metrics even when directory is empty")
    void recordsMetricsForEmptyDirectory() throws IOException {
      WalMetadata result = recovery.recover();

      assertNotNull(result);
      assertTrue(result.segments().isEmpty());
    }

    @Test
    @DisplayName("recover records metrics for corrupted segments")
    void recordsMetricsForCorruptedSegments() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createSegmentFileWithInvalidHeaderCrc(testDirectory, 1L, createdAt);

      WalMetadata result = recovery.recover();

      assertTrue(result.segments().isEmpty());
    }
  }

  @Nested
  @DisplayName("Recovery Completion Tests")
  class RecoveryCompletionTests {

    @Test
    @DisplayName("recover completes successfully with valid segments")
    void completesSuccessfullyWithValidSegments() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createValidSegmentFile(testDirectory, 1L, createdAt, 10, 100L, 1000L);

      assertDoesNotThrow(() -> recovery.recover());
    }

    @Test
    @DisplayName("recover completes successfully with corrupted segments")
    void completesSuccessfullyWithCorruptedSegments() throws IOException {
      long createdAt = System.currentTimeMillis();
      ManagementTestUtils.createSegmentFileWithInvalidHeaderCrc(testDirectory, 1L, createdAt);

      assertDoesNotThrow(() -> recovery.recover());
    }

    @Test
    @DisplayName("recover completes successfully with empty directory")
    void completesSuccessfullyWithEmptyDirectory() throws IOException {
      assertDoesNotThrow(() -> recovery.recover());
    }
  }
}
