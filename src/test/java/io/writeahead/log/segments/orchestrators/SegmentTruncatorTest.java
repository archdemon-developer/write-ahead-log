package io.writeahead.log.segments.orchestrators;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.enums.strategies.RotationPolicyType;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.models.results.TruncateResult;
import io.writeahead.log.segments.filter.truncate.BeforeTimestampTruncateFilter;
import io.writeahead.log.segments.filter.truncate.TruncateFilter;
import io.writeahead.log.segments.operators.SegmentCollection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegmentTruncatorTest {

  @TempDir Path tempDir;

  private SegmentTruncator truncator;
  private SegmentCollection segmentCollection;
  private String logDir;

  @BeforeEach
  void setUp() throws IOException {
    logDir = tempDir.toString();
    Files.createDirectories(tempDir);

    WalConfiguration walConfig =
        new WalConfiguration.Builder()
            .logDir(logDir)
            .batchSize(10)
            .maxSegmentSize(10 * 1024 * 1024)
            .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
            .rotationPolicyType(RotationPolicyType.SIZE_BASED)
            .maxRetries(3)
            .retryBackoffMs(10)
            .retryBackoffMultiplier(2.0)
            .build();

    segmentCollection = new SegmentCollection();
    truncator = new SegmentTruncator(segmentCollection, walConfig);
  }

  @Test
  void testTruncateEmptyCollection() throws IOException {
    BeforeTimestampTruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);
    TruncateResult result = truncator.truncateAllMatching(filter);

    assertNotNull(result);
    assertTrue(result.success());
  }

  @Test
  void testTruncateEmptyCollectionReturnsNoException() throws IOException {
    BeforeTimestampTruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);

    assertDoesNotThrow(() -> truncator.truncateAllMatching(filter));
  }

  @Test
  void testTruncateSegmentNoMatch() throws IOException {
    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 5000L, 6000L));

    TruncateFilter filter = new BeforeTimestampTruncateFilter(100L);

    TruncateResult result = truncator.truncateAllMatching(filter);

    assertTrue(result.success());
    assertEquals(0, result.segmentsRemoved());
    assertEquals(1, segmentCollection.size());
  }

  @Test
  void testTruncateSegmentNoMatchReturnsNothingRemoved() throws IOException {
    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 5000L, 6000L));

    TruncateFilter filter = new BeforeTimestampTruncateFilter(100L);

    TruncateResult result = truncator.truncateAllMatching(filter);

    assertFalse(result.didRemoveSegments());
  }

  @Test
  void testTruncateSingleSegment() throws IOException {
    File seg1 = new File(logDir, "wal-1000-000001.log");
    File seg2 = new File(logDir, "wal-1001-000002.log");
    seg1.createNewFile();
    seg2.createNewFile();

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 5000L, 6000L));
    segmentCollection.add(
        new SegmentMetadata("wal-1001-000002.log", 2, 1001, 200, 10, 7000L, 8000L));

    BeforeTimestampTruncateFilter filter = new BeforeTimestampTruncateFilter(6000L);
    TruncateResult result = truncator.truncateAllMatching(filter);

    assertTrue(result.success());
    assertEquals(1, result.segmentsRemoved());
    assertFalse(seg1.exists());
    assertTrue(seg2.exists()); // Keep at least one
  }

  @Test
  void testTruncateSingleSegmentFromCollection() throws IOException {
    File seg1 = new File(logDir, "wal-1000-000001.log");
    File seg2 = new File(logDir, "wal-1001-000002.log");
    seg1.createNewFile();
    seg2.createNewFile();

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 5000L, 6000L));
    segmentCollection.add(
        new SegmentMetadata("wal-1001-000002.log", 2, 1001, 200, 10, 7000L, 8000L));

    BeforeTimestampTruncateFilter filter = new BeforeTimestampTruncateFilter(6000L);
    truncator.truncateAllMatching(filter);

    assertEquals(1, segmentCollection.size());
  }

  @Test
  void testTruncateMultipleSegmentsSelective() throws IOException {

    File seg1 = new File(logDir, "wal-1000-000001.log");
    File seg2 = new File(logDir, "wal-1001-000002.log");
    File seg3 = new File(logDir, "wal-1002-000003.log");
    seg1.createNewFile();
    seg2.createNewFile();
    seg3.createNewFile();

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 1000L, 1500L));
    segmentCollection.add(
        new SegmentMetadata("wal-1001-000002.log", 2, 1001, 200, 10, 2000L, 2500L));
    segmentCollection.add(
        new SegmentMetadata("wal-1002-000003.log", 3, 1002, 200, 10, 3000L, 3500L));

    BeforeTimestampTruncateFilter filter = new BeforeTimestampTruncateFilter(2000L);
    TruncateResult result = truncator.truncateAllMatching(filter);

    assertTrue(result.success());
    assertEquals(1, result.segmentsRemoved());
    assertFalse(seg1.exists());
    assertTrue(seg2.exists() || seg3.exists());
  }

  @Test
  void testTruncateAllSegmentsKeepsOne() throws IOException {
    File seg1 = new File(logDir, "wal-1000-000001.log");
    File seg2 = new File(logDir, "wal-1001-000002.log");
    seg1.createNewFile();
    seg2.createNewFile();

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 1000L, 1500L));
    segmentCollection.add(
        new SegmentMetadata("wal-1001-000002.log", 2, 1001, 200, 10, 2000L, 2500L));

    BeforeTimestampTruncateFilter filter = new BeforeTimestampTruncateFilter(3000L);
    TruncateResult result = truncator.truncateAllMatching(filter);

    assertTrue(result.success());

    assertEquals(1, segmentCollection.size());
  }

  @Test
  void testTruncateFileNotFoundHandled() throws IOException {
    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 5000L, 6000L));

    BeforeTimestampTruncateFilter filter = new BeforeTimestampTruncateFilter(6000L);
    TruncateResult result = truncator.truncateAllMatching(filter);

    assertTrue(result.success());
  }

  @Test
  void testTruncateMissingFileDoesNotThrow() throws IOException {
    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 5000L, 6000L));

    BeforeTimestampTruncateFilter filter = new BeforeTimestampTruncateFilter(6000L);

    assertDoesNotThrow(() -> truncator.truncateAllMatching(filter));
  }

  @Test
  void testTruncateResultReturnsSegmentsRemoved() throws IOException {
    File seg1 = new File(logDir, "wal-1000-000001.log");
    File seg2 = new File(logDir, "wal-1001-000002.log");
    File seg3 = new File(logDir, "wal-1002-000003.log");
    seg1.createNewFile();
    seg2.createNewFile();
    seg3.createNewFile();

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 1000L, 1500L));
    segmentCollection.add(
        new SegmentMetadata("wal-1001-000002.log", 2, 1001, 200, 10, 2000L, 2500L));
    segmentCollection.add(
        new SegmentMetadata("wal-1002-000003.log", 3, 1002, 200, 10, 3000L, 3500L));

    BeforeTimestampTruncateFilter filter = new BeforeTimestampTruncateFilter(2000L);
    TruncateResult result = truncator.truncateAllMatching(filter);

    assertEquals(1, result.segmentsRemoved());
  }

  @Test
  void testTruncateResultReturnsOldestRemaining() throws IOException {
    File seg1 = new File(logDir, "wal-1000-000001.log");
    File seg2 = new File(logDir, "wal-1001-000002.log");
    seg1.createNewFile();
    seg2.createNewFile();

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 1000L, 1500L));
    segmentCollection.add(
        new SegmentMetadata("wal-1001-000002.log", 2, 1001, 200, 10, 2000L, 2500L));

    BeforeTimestampTruncateFilter filter = new BeforeTimestampTruncateFilter(1500L);
    TruncateResult result = truncator.truncateAllMatching(filter);

    assertEquals(2, result.oldestRemainingSegmentSequence());
  }

  @Test
  void testTruncateBeforeTimestamp() throws IOException {
    File seg1 = new File(logDir, "wal-1000-000001.log");
    File seg2 = new File(logDir, "wal-1001-000002.log");
    seg1.createNewFile();
    seg2.createNewFile();

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 5000L, 6000L));
    segmentCollection.add(
        new SegmentMetadata("wal-1001-000002.log", 2, 1001, 200, 10, 7000L, 8000L));

    TruncateResult result = truncator.truncateBeforeTimestamp(6000L);

    assertTrue(result.success());
    assertEquals(1, result.segmentsRemoved());
  }

  @Test
  void testTruncateBoundaryValue() throws IOException {
    File seg1 = new File(logDir, "wal-1000-000001.log");
    seg1.createNewFile();

    segmentCollection.add(new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 100L, 200L));

    TruncateResult result = truncator.truncateBeforeTimestamp(200L);

    assertTrue(result.success());
  }

  @Test
  void testTruncateSequenceZero() throws IOException {
    File seg1 = new File(logDir, "wal-1000-000000.log");
    seg1.createNewFile();

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000000.log", 0, 1000, 200, 10, 1000L, 1500L));

    BeforeTimestampTruncateFilter filter = new BeforeTimestampTruncateFilter(1500L);
    TruncateResult result = truncator.truncateAllMatching(filter);

    assertTrue(result.success());
  }

  @Test
  void testTruncateLargeSequences() throws IOException {
    File seg1 = new File(logDir, "wal-1000-999999.log");
    File seg2 = new File(logDir, "wal-1001-1000000.log");
    seg1.createNewFile();
    seg2.createNewFile();

    segmentCollection.add(
        new SegmentMetadata("wal-1000-999999.log", 999999, 1000, 200, 10, 1000L, 1500L));
    segmentCollection.add(
        new SegmentMetadata("wal-1001-1000000.log", 1000000, 1001, 200, 10, 2000L, 2500L));

    BeforeTimestampTruncateFilter filter = new BeforeTimestampTruncateFilter(1500L);
    TruncateResult result = truncator.truncateAllMatching(filter);

    assertTrue(result.success());
    assertEquals(1, result.segmentsRemoved());
  }

  @Test
  void testTruncateMultipleFilesDeleted() throws IOException {
    File seg1 = new File(logDir, "wal-1000-000001.log");
    File seg2 = new File(logDir, "wal-1001-000002.log");
    seg1.createNewFile();
    seg2.createNewFile();

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 1000L, 1500L));
    segmentCollection.add(
        new SegmentMetadata("wal-1001-000002.log", 2, 1001, 200, 10, 2000L, 2500L));

    BeforeTimestampTruncateFilter filter = new BeforeTimestampTruncateFilter(2000L);
    truncator.truncateAllMatching(filter);

    assertFalse(seg1.exists());
  }

  @Test
  void testTruncateNothingRemoved() throws IOException {
    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 5000L, 6000L));

    TruncateResult result = truncator.truncateBeforeTimestamp(100L);

    assertTrue(result.success());
    assertEquals(0, result.segmentsRemoved());
  }
}
