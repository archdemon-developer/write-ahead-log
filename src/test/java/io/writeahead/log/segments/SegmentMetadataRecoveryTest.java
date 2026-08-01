package io.writeahead.log.segments;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.metrics.SimpleWalMetrics;
import io.writeahead.log.models.WalMetadata;
import io.writeahead.log.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class SegmentMetadataRecoveryTest {

  private static final long FIRST_SEGMENT_SEQUENCE = 1L;
  private static final long SECOND_SEGMENT_SEQUENCE = 2L;
  private static final long THIRD_SEGMENT_SEQUENCE = 3L;
  private static final long FIRST_ENTRY_TIMESTAMP = 1000L;
  private static final long SECOND_ENTRY_TIMESTAMP = 2000L;
  private static final long THIRD_ENTRY_TIMESTAMP = 3000L;
  private static final long FOURTH_ENTRY_TIMESTAMP = 4000L;
  private static final long FIFTH_ENTRY_TIMESTAMP = 5000L;
  private static final long SIXTH_ENTRY_TIMESTAMP = 6000L;
  private static final int FIRST_ENTRY_COUNT = 100;
  private static final int SECOND_ENTRY_COUNT = 200;
  private static final int THIRD_ENTRY_COUNT = 300;
  private static final int ZERO_SEGMENTS = 0;
  private static final int SINGLE_SEGMENT = 1;
  private static final int TWO_SEGMENTS = 2;
  private static final int THREE_SEGMENTS = 3;
  private static final int MAX_SEGMENT_SIZE = 1_000_000;
  private static final int BATCH_SIZE_ONE = 1;
  private Path tempLogDirectory;
  private SegmentMetadataRecovery recoveryUnderTest;
  private SegmentLifecycleManager lifecycleManager;

  @BeforeEach
  void setUp() throws IOException {
    tempLogDirectory = Files.createTempDirectory("segment-recovery-test-");
    recoveryUnderTest =
        new SegmentMetadataRecovery(tempLogDirectory.toString(), new SimpleWalMetrics());
    lifecycleManager = new SegmentLifecycleManager(tempLogDirectory.toString());
  }

  @AfterEach
  void tearDown() throws IOException {
    Files.walk(tempLogDirectory)
        .sorted(Comparator.reverseOrder())
        .forEach(
            path -> {
              try {
                Files.delete(path);
              } catch (IOException ignored) {
              }
            });
  }

  @Test
  void recoverFromEmptyDirectoryReturnsEmptySegmentList() throws IOException {
    WalMetadata recoveredMetadata = recoveryUnderTest.recover();

    assertEquals(ZERO_SEGMENTS, recoveredMetadata.segments().size());
    assertEquals(1L, recoveredMetadata.nextSequence());
  }

  @Test
  void recoverSingleValidSegmentMetadata() throws IOException {
    SegmentLifecycleManager lifecycleManager =
        new SegmentLifecycleManager(tempLogDirectory.toString());
    var segmentStream = lifecycleManager.createNewSegment(FIRST_SEGMENT_SEQUENCE);
    lifecycleManager.finalizeSegment(
        segmentStream, FIRST_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

    WalMetadata recoveredMetadata = recoveryUnderTest.recover();

    assertEquals(SINGLE_SEGMENT, recoveredMetadata.segments().size());
    assertEquals(FIRST_SEGMENT_SEQUENCE, recoveredMetadata.segments().getFirst().sequenceNumber());
    assertEquals(FIRST_ENTRY_COUNT, recoveredMetadata.segments().getFirst().entryCount());
    assertEquals(FIRST_ENTRY_TIMESTAMP, recoveredMetadata.segments().getFirst().minTimestamp());
    assertEquals(SECOND_ENTRY_TIMESTAMP, recoveredMetadata.segments().getFirst().maxTimestamp());
  }

  @Test
  void recoverMultipleSegmentMetadata() throws IOException {
    SegmentLifecycleManager lifecycleManager =
        new SegmentLifecycleManager(tempLogDirectory.toString());

    var firstSegmentStream = lifecycleManager.createNewSegment(FIRST_SEGMENT_SEQUENCE);
    lifecycleManager.finalizeSegment(
        firstSegmentStream, FIRST_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

    var secondSegmentStream = lifecycleManager.createNewSegment(SECOND_SEGMENT_SEQUENCE);
    lifecycleManager.finalizeSegment(
        secondSegmentStream, SECOND_ENTRY_COUNT, THIRD_ENTRY_TIMESTAMP, FOURTH_ENTRY_TIMESTAMP);

    var thirdSegmentStream = lifecycleManager.createNewSegment(THIRD_SEGMENT_SEQUENCE);
    lifecycleManager.finalizeSegment(
        thirdSegmentStream, THIRD_ENTRY_COUNT, FIFTH_ENTRY_TIMESTAMP, SIXTH_ENTRY_TIMESTAMP);

    WalMetadata recoveredMetadata = recoveryUnderTest.recover();

    assertEquals(THREE_SEGMENTS, recoveredMetadata.segments().size());
    assertEquals(FIRST_SEGMENT_SEQUENCE, recoveredMetadata.segments().get(0).sequenceNumber());
    assertEquals(SECOND_SEGMENT_SEQUENCE, recoveredMetadata.segments().get(1).sequenceNumber());
    assertEquals(THIRD_SEGMENT_SEQUENCE, recoveredMetadata.segments().get(2).sequenceNumber());
  }

  @Test
  void recoverPreservesSegmentOrderBySequence() throws IOException {
    SegmentLifecycleManager lifecycleManager =
        new SegmentLifecycleManager(tempLogDirectory.toString());

    var firstSegmentStream = lifecycleManager.createNewSegment(FIRST_SEGMENT_SEQUENCE);
    lifecycleManager.finalizeSegment(
        firstSegmentStream, FIRST_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

    var secondSegmentStream = lifecycleManager.createNewSegment(SECOND_SEGMENT_SEQUENCE);
    lifecycleManager.finalizeSegment(
        secondSegmentStream, SECOND_ENTRY_COUNT, THIRD_ENTRY_TIMESTAMP, FOURTH_ENTRY_TIMESTAMP);

    WalMetadata recoveredMetadata = recoveryUnderTest.recover();

    assertTrue(
        recoveredMetadata.segments().get(0).sequenceNumber()
            <= recoveredMetadata.segments().get(1).sequenceNumber());
  }

  @Test
  void recoverCalculatesNextSequenceAsMaxPlusOne() throws IOException {
    SegmentLifecycleManager lifecycleManager =
        new SegmentLifecycleManager(tempLogDirectory.toString());

    var firstSegmentStream = lifecycleManager.createNewSegment(FIRST_SEGMENT_SEQUENCE);
    lifecycleManager.finalizeSegment(
        firstSegmentStream, FIRST_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

    var secondSegmentStream = lifecycleManager.createNewSegment(SECOND_SEGMENT_SEQUENCE);
    lifecycleManager.finalizeSegment(
        secondSegmentStream, SECOND_ENTRY_COUNT, THIRD_ENTRY_TIMESTAMP, FOURTH_ENTRY_TIMESTAMP);

    var thirdSegmentStream = lifecycleManager.createNewSegment(THIRD_SEGMENT_SEQUENCE);
    lifecycleManager.finalizeSegment(
        thirdSegmentStream, THIRD_ENTRY_COUNT, FIFTH_ENTRY_TIMESTAMP, SIXTH_ENTRY_TIMESTAMP);

    WalMetadata recoveredMetadata = recoveryUnderTest.recover();

    long expectedNextSequence = THIRD_SEGMENT_SEQUENCE + 1;
    assertEquals(expectedNextSequence, recoveredMetadata.nextSequence());
  }

  @Test
  void recoverHandlesZeroEntryCountSegment() throws IOException {
    SegmentLifecycleManager lifecycleManager =
        new SegmentLifecycleManager(tempLogDirectory.toString());
    var segmentStream = lifecycleManager.createNewSegment(FIRST_SEGMENT_SEQUENCE);
    lifecycleManager.finalizeSegment(
        segmentStream, 0, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

    WalMetadata recoveredMetadata = recoveryUnderTest.recover();

    assertEquals(SINGLE_SEGMENT, recoveredMetadata.segments().size());
    assertEquals(0, recoveredMetadata.segments().getFirst().entryCount());
  }

  @Test
  void recoverReturnsLastActiveSegmentFilename() throws IOException {
    SegmentLifecycleManager lifecycleManager =
        new SegmentLifecycleManager(tempLogDirectory.toString());

    var firstSegmentStream = lifecycleManager.createNewSegment(FIRST_SEGMENT_SEQUENCE);
    lifecycleManager.finalizeSegment(
        firstSegmentStream, FIRST_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

    var secondSegmentStream = lifecycleManager.createNewSegment(SECOND_SEGMENT_SEQUENCE);
    lifecycleManager.finalizeSegment(
        secondSegmentStream, SECOND_ENTRY_COUNT, THIRD_ENTRY_TIMESTAMP, FOURTH_ENTRY_TIMESTAMP);

    WalMetadata recoveredMetadata = recoveryUnderTest.recover();

    assertNotNull(recoveredMetadata.lastActiveSegment());
    assertTrue(recoveredMetadata.lastActiveSegment().contains("000002"));
  }

  @Test
  void recoverDeterministicallyRecreatesMetadataFromHeaders() throws IOException {
    SegmentLifecycleManager lifecycleManager =
        new SegmentLifecycleManager(tempLogDirectory.toString());
    var segmentStream = lifecycleManager.createNewSegment(FIRST_SEGMENT_SEQUENCE);
    lifecycleManager.finalizeSegment(
        segmentStream, FIRST_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

    WalMetadata firstRecovery = recoveryUnderTest.recover();
    WalMetadata secondRecovery = recoveryUnderTest.recover();

    assertEquals(firstRecovery.segments().size(), secondRecovery.segments().size());
    assertEquals(
        firstRecovery.segments().getFirst().sequenceNumber(),
        secondRecovery.segments().getFirst().sequenceNumber());
  }

  @Nested
  class CorruptionDetectionTests {

    private static final long TIMESTAMP_1000 = 1000L;
    private static final long TIMESTAMP_2000 = 2000L;
    private static final String TEST_ENTRY_DATA = "test entry payload";
    private static final long SEGMENT_SEQUENCE = 1L;
    private static final int ENTRY_COUNT = 100;
    private static final long MIN_TIMESTAMP = 1000L;
    private static final long MAX_TIMESTAMP = 2000L;

    @Test
    void corruptedFooterCrcDetected() throws Exception {
      var segmentStream = lifecycleManager.createNewSegment(SEGMENT_SEQUENCE);
      lifecycleManager.finalizeSegment(segmentStream, ENTRY_COUNT, MIN_TIMESTAMP, MAX_TIMESTAMP);

      File segmentFile = getSegmentFile();
      byte[] allBytes = FileUtils.readAllBytes(segmentFile);
      int footerStart = allBytes.length - 36;
      allBytes[footerStart + 28] = (byte) ~allBytes[footerStart + 28];
      Files.write(segmentFile.toPath(), allBytes);

      SegmentMetadataRecovery recoveryUnderTest =
          new SegmentMetadataRecovery(tempLogDirectory.toString(), new SimpleWalMetrics());
      WalMetadata recoveredMetadata = recoveryUnderTest.recover();

      assertEquals(0, recoveredMetadata.segments().size());
    }

    private File getSegmentFile() throws Exception {
      File logDir = tempLogDirectory.toFile();
      File[] segmentFiles = logDir.listFiles((dir, name) -> name.endsWith(".log"));
      if (segmentFiles == null || segmentFiles.length == 0) {
        throw new Exception("No segment file found");
      }
      return segmentFiles[0];
    }
  }

  @Nested
  class EdgeCasesTests {

    private static final int ENTRY_COUNT = 100;
    private static final long MIN_TIMESTAMP = 1000L;
    private static final long MAX_TIMESTAMP = 2000L;

    @Test
    void recoverSegmentWithSizeJustBelowMinimumSkipsSegmentFromRecovery() throws Exception {
      createSegmentFileWithCustomSize(1L, 83L);

      WalMetadata recoveredWalMetadata = recoveryUnderTest.recover();

      assertEquals(0, recoveredWalMetadata.segments().size());
    }

    @Test
    void recoverMultipleSegmentsOneTooSmallSkipsSmallOnly() throws Exception {
      var firstSegmentStream = lifecycleManager.createNewSegment(1L);
      lifecycleManager.finalizeSegment(firstSegmentStream, 50, 1000L, 2000L);

      createSegmentFileWithCustomSize(2L, 50L);

      var thirdSegmentStream = lifecycleManager.createNewSegment(3L);
      lifecycleManager.finalizeSegment(thirdSegmentStream, 75, 3000L, 4000L);

      WalMetadata recoveredWalMetadata = recoveryUnderTest.recover();

      assertEquals(2, recoveredWalMetadata.segments().size());
      assertEquals(1L, recoveredWalMetadata.segments().get(0).sequenceNumber());
      assertEquals(3L, recoveredWalMetadata.segments().get(1).sequenceNumber());
    }

    @Test
    void recoverSegmentWithSingleByteSkipsSegmentFromRecovery() throws Exception {
      createSegmentFileWithCustomSize(1L, 1L);

      WalMetadata recoveredWalMetadata = recoveryUnderTest.recover();

      assertEquals(0, recoveredWalMetadata.segments().size());
    }

    @Test
    void recoverSegmentWithSmallSizeSkipsSegmentFromRecovery() throws Exception {
      createSegmentFileWithCustomSize(1L, 50L);

      WalMetadata recoveredWalMetadata = recoveryUnderTest.recover();

      assertEquals(0, recoveredWalMetadata.segments().size());
    }

    @Test
    void recoverSegmentWithZeroBytesSkipsSegmentFromRecovery() throws Exception {
      createSegmentFileWithCustomSize(1L, 0L);

      WalMetadata recoveredWalMetadata = recoveryUnderTest.recover();

      assertEquals(0, recoveredWalMetadata.segments().size());
    }

    @Test
    void recoverContinuesAfterEncounteringSegmentsTooSmall() throws Exception {
      var firstSegmentStream = lifecycleManager.createNewSegment(1L);
      lifecycleManager.finalizeSegment(firstSegmentStream, 50, 1000L, 2000L);

      createSegmentFileWithCustomSize(2L, 25L);

      var thirdSegmentStream = lifecycleManager.createNewSegment(3L);
      lifecycleManager.finalizeSegment(thirdSegmentStream, 75, 3000L, 4000L);

      createSegmentFileWithCustomSize(4L, 40L);

      var fifthSegmentStream = lifecycleManager.createNewSegment(5L);
      lifecycleManager.finalizeSegment(fifthSegmentStream, 60, 5000L, 6000L);

      WalMetadata recoveredWalMetadata = recoveryUnderTest.recover();

      assertEquals(3, recoveredWalMetadata.segments().size());
      assertEquals(1L, recoveredWalMetadata.segments().get(0).sequenceNumber());
      assertEquals(3L, recoveredWalMetadata.segments().get(1).sequenceNumber());
      assertEquals(5L, recoveredWalMetadata.segments().get(2).sequenceNumber());
    }

    @Test
    void recoverAllSegmentsTooSmallReturnsEmptyList() throws Exception {
      createSegmentFileWithCustomSize(1L, 50L);
      createSegmentFileWithCustomSize(2L, 60L);
      createSegmentFileWithCustomSize(3L, 70L);

      WalMetadata recoveredWalMetadata = recoveryUnderTest.recover();

      assertEquals(0, recoveredWalMetadata.segments().size());
    }

    @Test
    void recoverWithValidSegmentsReturnsCorrectMetadata() throws Exception {
      var firstSegmentStream = lifecycleManager.createNewSegment(1L);
      lifecycleManager.finalizeSegment(firstSegmentStream, 50, 1000L, 2000L);

      createSegmentFileWithCustomSize(2L, 40L);

      var thirdSegmentStream = lifecycleManager.createNewSegment(3L);
      lifecycleManager.finalizeSegment(thirdSegmentStream, 75, 3000L, 4000L);

      WalMetadata recoveredWalMetadata = recoveryUnderTest.recover();

      assertEquals(2, recoveredWalMetadata.segments().size());
      assertEquals(50, recoveredWalMetadata.segments().get(0).entryCount());
      assertEquals(75, recoveredWalMetadata.segments().get(1).entryCount());
    }

    @Test
    void recoverNextSequenceCalculatedFromValidSegmentsOnly() throws Exception {
      var firstSegmentStream = lifecycleManager.createNewSegment(1L);
      lifecycleManager.finalizeSegment(firstSegmentStream, 50, 1000L, 2000L);

      createSegmentFileWithCustomSize(2L, 30L);

      var thirdSegmentStream = lifecycleManager.createNewSegment(3L);
      lifecycleManager.finalizeSegment(thirdSegmentStream, 75, 3000L, 4000L);

      createSegmentFileWithCustomSize(4L, 20L);

      WalMetadata recoveredWalMetadata = recoveryUnderTest.recover();

      assertEquals(2, recoveredWalMetadata.segments().size());
      assertEquals(4L, recoveredWalMetadata.nextSequence());
    }

    @Test
    void recoverSegmentWithMinimumValidSizeIsRecovered() throws Exception {
      var firstSegmentStream = lifecycleManager.createNewSegment(1L);
      lifecycleManager.finalizeSegment(
          firstSegmentStream, ENTRY_COUNT, MIN_TIMESTAMP, MAX_TIMESTAMP);

      WalMetadata recoveredWalMetadata = recoveryUnderTest.recover();

      assertEquals(1, recoveredWalMetadata.segments().size());
    }

    private File createSegmentFileWithCustomSize(long sequenceNumber, long fileSizeInBytes)
        throws Exception {
      File logDir = tempLogDirectory.toFile();
      String timestampedFilename = SegmentLifecycleManager.generateSegmentFilename(sequenceNumber);
      File segmentFile = new File(logDir, timestampedFilename);

      byte[] minimumSizeBytes = new byte[(int) fileSizeInBytes];
      Files.write(segmentFile.toPath(), minimumSizeBytes);

      return segmentFile;
    }
  }
}
