package io.writeahead.log.segments.orchestrators;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.enums.strategies.RotationPolicyType;
import io.writeahead.log.metrics.SimpleWalMetrics;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.segments.filter.reads.AfterTimestampFilter;
import io.writeahead.log.segments.operators.SegmentCollection;
import io.writeahead.log.segments.operators.SegmentEntriesReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegmentReaderTest {

  @TempDir Path tempDir;

  private SegmentReader reader;
  private SegmentCollection segmentCollection;
  private SimpleWalMetrics metrics;
  private String logDir;

  private static final int HEADER_SIZE = 48;
  private static final int FOOTER_SIZE = 36;

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

    metrics = new SimpleWalMetrics();
    segmentCollection = new SegmentCollection();
    SegmentEntriesReader entriesReader = new SegmentEntriesReader(metrics);
    reader = new SegmentReader(entriesReader, segmentCollection, walConfig, metrics);
  }

  @Test
  void testReadAllSegmentsEmptyCollection() throws IOException {
    List<LogEntry> entries = reader.readAllSegments();

    assertNotNull(entries);
    assertEquals(0, entries.size());
  }

  @Test
  void testReadAllSegmentsReturnsEmptyList() throws IOException {
    List<LogEntry> entries = reader.readAllSegments();

    assertFalse(entries == null);
    assertTrue(entries.isEmpty());
  }

  @Test
  void testReadAllMatchingEmptyCollection() throws IOException {
    AfterTimestampFilter filter = new AfterTimestampFilter(1000L);
    List<LogEntry> entries = reader.readAllMatching(filter);

    assertEquals(0, entries.size());
  }

  @Test
  void testReadAllAfterTimestampEmptyCollection() throws IOException {
    List<LogEntry> entries = reader.readAllAfterTimestamp(1000L);

    assertEquals(0, entries.size());
  }

  @Test
  void testReadMissingSegmentFile() throws IOException {
    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 5000L, 6000L));

    List<LogEntry> entries = reader.readAllSegments();

    assertEquals(0, entries.size());
  }

  @Test
  void testReadMissingSegmentFileNoException() throws IOException {
    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, 200, 10, 5000L, 6000L));

    assertDoesNotThrow(() -> reader.readAllSegments());
  }

  @Test
  void testReadSingleSegmentFile() throws IOException {
    File segmentFile = new File(logDir, "wal-1000-000001.log");
    byte[] minimalSegment = createMinimalSegment();
    Files.write(segmentFile.toPath(), minimalSegment);

    segmentCollection.add(
        new SegmentMetadata(
            "wal-1000-000001.log", 1, 1000, minimalSegment.length, 1, 5000L, 6000L));

    List<LogEntry> entries = reader.readAllSegments();

    assertEquals(0, entries.size());
  }

  @Test
  void testReadMultipleSegments() throws IOException {
    File seg1 = new File(logDir, "wal-1000-000001.log");
    File seg2 = new File(logDir, "wal-1001-000002.log");
    byte[] minimal = createMinimalSegment();
    Files.write(seg1.toPath(), minimal);
    Files.write(seg2.toPath(), minimal);

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, minimal.length, 1, 5000L, 6000L));
    segmentCollection.add(
        new SegmentMetadata("wal-1001-000002.log", 2, 1001, minimal.length, 1, 7000L, 8000L));

    List<LogEntry> entries = reader.readAllSegments();

    assertEquals(0, entries.size());
  }

  @Test
  void testReadWithAfterTimestampFilter() throws IOException {
    File segmentFile = new File(logDir, "wal-1000-000001.log");
    byte[] minimal = createMinimalSegment();
    Files.write(segmentFile.toPath(), minimal);

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, minimal.length, 1, 5000L, 6000L));

    AfterTimestampFilter filter = new AfterTimestampFilter(5000L);
    List<LogEntry> entries = reader.readAllMatching(filter);

    assertNotNull(entries);
  }

  @Test
  void testReadCanSkipSegmentOptimization() throws IOException {
    File segmentFile = new File(logDir, "wal-1000-000001.log");
    byte[] minimal = createMinimalSegment();
    Files.write(segmentFile.toPath(), minimal);

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, minimal.length, 1, 5000L, 6000L));

    AfterTimestampFilter filter = new AfterTimestampFilter(7000L);

    assertTrue(filter.canSkipSegment(segmentCollection.getSegments().get(0)));
  }

  @Test
  void testReadPartialMatch() throws IOException {
    File segmentFile = new File(logDir, "wal-1000-000001.log");
    byte[] minimal = createMinimalSegment();
    Files.write(segmentFile.toPath(), minimal);

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, minimal.length, 1, 5000L, 6000L));

    AfterTimestampFilter filter = new AfterTimestampFilter(5500L);
    List<LogEntry> entries = reader.readAllMatching(filter);

    assertNotNull(entries);
  }

  @Test
  void testReadMultipleSegmentsWithFilter() throws IOException {
    File seg1 = new File(logDir, "wal-1000-000001.log");
    File seg2 = new File(logDir, "wal-1001-000002.log");
    byte[] minimal = createMinimalSegment();
    Files.write(seg1.toPath(), minimal);
    Files.write(seg2.toPath(), minimal);

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, minimal.length, 1, 1000L, 2000L));
    segmentCollection.add(
        new SegmentMetadata("wal-1001-000002.log", 2, 1001, minimal.length, 1, 5000L, 6000L));

    AfterTimestampFilter filter = new AfterTimestampFilter(1000L);
    List<LogEntry> entries = reader.readAllMatching(filter);

    assertNotNull(entries);
  }

  @Test
  void testReadMinimalSegment() throws IOException {
    File segmentFile = new File(logDir, "wal-1000-000001.log");
    byte[] minimal = createMinimalSegment();
    Files.write(segmentFile.toPath(), minimal);

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, minimal.length, 1, 5000L, 6000L));

    List<LogEntry> entries = reader.readAllSegments();

    assertEquals(0, entries.size());
  }

  @Test
  void testReadBoundaryTimestamps() throws IOException {
    File segmentFile = new File(logDir, "wal-1000-000001.log");
    byte[] minimal = createMinimalSegment();
    Files.write(segmentFile.toPath(), minimal);

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, minimal.length, 1, 100L, 200L));

    AfterTimestampFilter filter = new AfterTimestampFilter(200L);
    List<LogEntry> entries = reader.readAllMatching(filter);

    assertNotNull(entries);
  }

  @Test
  void testReadSequenceZero() throws IOException {
    File segmentFile = new File(logDir, "wal-1000-000000.log");
    byte[] minimal = createMinimalSegment();
    Files.write(segmentFile.toPath(), minimal);

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000000.log", 0, 1000, minimal.length, 1, 5000L, 6000L));

    List<LogEntry> entries = reader.readAllSegments();

    assertEquals(0, entries.size());
  }

  @Test
  void testReadOutOfOrderSegments() throws IOException {
    File seg1 = new File(logDir, "wal-1002-000003.log");
    File seg2 = new File(logDir, "wal-1000-000001.log");
    File seg3 = new File(logDir, "wal-1001-000002.log");
    byte[] minimal = createMinimalSegment();
    Files.write(seg1.toPath(), minimal);
    Files.write(seg2.toPath(), minimal);
    Files.write(seg3.toPath(), minimal);

    segmentCollection.add(
        new SegmentMetadata("wal-1002-000003.log", 3, 1002, minimal.length, 1, 7000L, 8000L));
    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, minimal.length, 1, 3000L, 4000L));
    segmentCollection.add(
        new SegmentMetadata("wal-1001-000002.log", 2, 1001, minimal.length, 1, 5000L, 6000L));

    List<LogEntry> entries = reader.readAllSegments();

    assertNotNull(entries);
  }

  @Test
  void testReadAfterTimestampDelegation() throws IOException {
    File segmentFile = new File(logDir, "wal-1000-000001.log");
    byte[] minimal = createMinimalSegment();
    Files.write(segmentFile.toPath(), minimal);

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, minimal.length, 1, 5000L, 6000L));

    List<LogEntry> entries = reader.readAllAfterTimestamp(5000L);

    assertNotNull(entries);
  }

  @Test
  void testExtractEntryRegionEmpty() throws IOException {
    File segmentFile = new File(logDir, "wal-1000-000001.log");
    byte[] minimal = createMinimalSegment();
    Files.write(segmentFile.toPath(), minimal);

    segmentCollection.add(
        new SegmentMetadata("wal-1000-000001.log", 1, 1000, minimal.length, 1, 5000L, 6000L));

    List<LogEntry> entries = reader.readAllSegments();
    assertEquals(0, entries.size());
  }

  @Test
  void testReadLargeNumericSequence() throws IOException {
    File segmentFile = new File(logDir, "wal-1000-999999.log");
    byte[] minimal = createMinimalSegment();
    Files.write(segmentFile.toPath(), minimal);

    segmentCollection.add(
        new SegmentMetadata("wal-1000-999999.log", 999999, 1000, minimal.length, 1, 5000L, 6000L));

    List<LogEntry> entries = reader.readAllSegments();

    assertEquals(0, entries.size());
  }

  private byte[] createMinimalSegment() {
    byte[] segment = new byte[HEADER_SIZE + FOOTER_SIZE];

    segment[0] = (byte) 0xAA;

    return segment;
  }
}
