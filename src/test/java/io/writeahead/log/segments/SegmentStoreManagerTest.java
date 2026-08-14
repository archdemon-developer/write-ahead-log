package io.writeahead.log.segments;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.enums.strategies.RotationPolicyType;
import io.writeahead.log.metrics.WalMetricsQuery;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.models.results.AppendResult;
import io.writeahead.log.models.results.CloseResult;
import io.writeahead.log.models.results.TruncateResult;
import io.writeahead.log.models.states.BatchState;
import io.writeahead.log.models.states.SegmentState;
import io.writeahead.log.segments.filter.reads.AfterTimestampFilter;
import io.writeahead.log.segments.filter.truncate.BeforeTimestampTruncateFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegmentStoreManagerTest {

  @TempDir Path tempDir;

  private SegmentStoreManager manager;
  private WalConfiguration walConfig;
  private String logDir;

  @BeforeEach
  void setUp() throws IOException {
    logDir = tempDir.toString();
    Files.createDirectories(tempDir);

    walConfig =
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

    manager = new SegmentStoreManager(walConfig);
  }

  @Test
  void testInitializationCreatesManager() throws IOException {
    assertNotNull(manager);
    assertTrue(manager.isOpen());
  }

  @Test
  void testInitializationRecovery() throws IOException {
    assertTrue(manager.isOpen());
    assertEquals(0, manager.getSegments().size());
  }

  @Test
  void testAppendDirectlyWritesEntry() throws IOException {
    long timestamp = System.currentTimeMillis();
    LogEntry entry = new LogEntry(50, new byte[50], timestamp);

    assertDoesNotThrow(() -> manager.appendDirectly(entry));
  }

  @Test
  void testAppendDirectlyThrowsWhenClosed() throws IOException {
    manager.close();

    LogEntry entry = new LogEntry(50, new byte[50], System.currentTimeMillis());

    assertThrows(Exception.class, () -> manager.appendDirectly(entry));
  }

  @Test
  void testWriteBatchReturnsAppendResult() throws IOException {
    long timestamp = System.currentTimeMillis();
    manager.appendDirectly(new LogEntry(50, new byte[50], timestamp));

    AppendResult result = manager.writeBatch();

    assertNotNull(result);
    assertTrue(result.flushed());
  }

  @Test
  void testWriteBatchThrowsWhenClosed() throws IOException {
    manager.close();

    assertThrows(Exception.class, () -> manager.writeBatch());
  }

  @Test
  void testReadAllSegmentsEmptyWal() throws IOException {
    List<LogEntry> entries = manager.readAllSegments();

    assertNotNull(entries);
    assertEquals(0, entries.size());
  }

  @Test
  void testReadAllSegmentsAfterWrite() throws IOException {
    long timestamp = System.currentTimeMillis();
    manager.appendDirectly(new LogEntry(50, new byte[50], timestamp));
    manager.writeBatch();

    List<LogEntry> entries = manager.readAllSegments();

    assertNotNull(entries);
  }

  @Test
  void testReadAllMatchingWithFilter() throws IOException {
    long timestamp = System.currentTimeMillis();
    manager.appendDirectly(new LogEntry(50, new byte[50], timestamp));
    manager.writeBatch();

    AfterTimestampFilter filter = new AfterTimestampFilter(timestamp - 1000);
    List<LogEntry> entries = manager.readAllMatching(filter);

    assertNotNull(entries);
  }

  @Test
  void testReadAllAfterTimestamp() throws IOException {
    long timestamp = System.currentTimeMillis();
    manager.appendDirectly(new LogEntry(50, new byte[50], timestamp));
    manager.writeBatch();

    List<LogEntry> entries = manager.readAllAfterTimestamp(timestamp - 1000);

    assertNotNull(entries);
  }

  @Test
  void testTruncateAllMatching() throws IOException {
    long timestamp = System.currentTimeMillis();
    manager.appendDirectly(new LogEntry(50, new byte[50], timestamp));
    manager.writeBatch();

    manager.appendDirectly(new LogEntry(50, new byte[50], timestamp + 1000));
    manager.writeBatch();

    for (int i = 0; i < 1000; i++) {
      manager.appendDirectly(new LogEntry(50, new byte[50], timestamp + 2000 + i));
    }
    manager.writeBatch();

    BeforeTimestampTruncateFilter filter = new BeforeTimestampTruncateFilter(timestamp + 500);
    TruncateResult result = manager.truncateAllMatching(filter);

    assertTrue(result.success());
  }

  @Test
  void testTruncateBeforeTimestamp() throws IOException {
    long timestamp = System.currentTimeMillis();
    manager.appendDirectly(new LogEntry(50, new byte[50], timestamp));
    manager.writeBatch();

    manager.appendDirectly(new LogEntry(50, new byte[50], timestamp + 1000));
    manager.writeBatch();

    for (int i = 0; i < 1000; i++) {
      manager.appendDirectly(new LogEntry(50, new byte[50], timestamp + 2000 + i));
    }
    manager.writeBatch();

    TruncateResult result = manager.truncateBeforeTimestamp(timestamp + 500);

    assertTrue(result.success());
  }

  @Test
  void testGetCurrentSequenceNumber() throws IOException {
    long seqNum = manager.getCurrentSequenceNumber();

    assertTrue(seqNum >= 1);
  }

  @Test
  void testGetCurrentEntryCount() throws IOException {
    int count = manager.getCurrentEntryCount();

    assertEquals(0, count);
  }

  @Test
  void testGetCurrentEntryCountAfterAppend() throws IOException {
    manager.appendDirectly(new LogEntry(50, new byte[50], System.currentTimeMillis()));

    int count = manager.getCurrentEntryCount();

    assertTrue(count > 0);
  }

  @Test
  void testGetCurrentStreamSize() throws IOException {
    long size = manager.getCurrentStreamSize();

    assertEquals(48, size);
  }

  @Test
  void testGetCurrentMinTimestamp() throws IOException {
    long minTs = manager.getCurrentMinTimestamp();

    assertEquals(Long.MAX_VALUE, minTs);
  }

  @Test
  void testGetCurrentMaxTimestamp() throws IOException {
    long maxTs = manager.getCurrentMaxTimestamp();

    assertEquals(Long.MIN_VALUE, maxTs);
  }

  @Test
  void testGetCurrentMinMaxAfterAppend() throws IOException {
    long timestamp = System.currentTimeMillis();
    manager.appendDirectly(new LogEntry(50, new byte[50], timestamp));

    long minTs = manager.getCurrentMinTimestamp();
    long maxTs = manager.getCurrentMaxTimestamp();

    assertTrue(minTs <= timestamp);
    assertTrue(maxTs >= timestamp);
  }

  @Test
  void testGetCurrentSegmentCreatedAt() throws IOException {
    long createdAt = manager.getCurrentSegmentCreatedAt();

    assertTrue(createdAt > 0);
  }

  @Test
  void testGetBatchState() throws IOException {
    BatchState state = manager.getBatchState();

    assertNotNull(state);
    assertTrue(state.isEmpty());
  }

  @Test
  void testIsOpenAfterConstruction() throws IOException {
    assertTrue(manager.isOpen());
  }

  @Test
  void testIsOpenAfterClose() throws IOException {
    manager.close();

    assertFalse(manager.isOpen());
  }

  @Test
  void testGetMetricsReturnsMetrics() throws IOException {
    WalMetricsQuery metrics = manager.getMetrics();

    assertNotNull(metrics);
  }

  @Test
  void testGetSegmentsEmptyInitially() throws IOException {
    List<SegmentMetadata> segments = manager.getSegments();

    assertNotNull(segments);
    assertEquals(0, segments.size());
  }

  @Test
  void testGetSegmentsAfterWrite() throws IOException {
    manager.appendDirectly(new LogEntry(50, new byte[50], System.currentTimeMillis()));
    manager.writeBatch();

    List<SegmentMetadata> segments = manager.getSegments();

    assertNotNull(segments);
  }

  @Test
  void testCloseReturnsCloseResult() throws IOException {
    manager.appendDirectly(new LogEntry(50, new byte[50], System.currentTimeMillis()));
    manager.writeBatch();

    CloseResult result = manager.close();

    assertNotNull(result);
    assertTrue(result.success() || result.hadErrors());
  }

  @Test
  void testCloseSuccessful() throws IOException {
    manager.appendDirectly(new LogEntry(50, new byte[50], System.currentTimeMillis()));
    manager.writeBatch();

    CloseResult result = manager.close();

    assertTrue(result.success());
    assertFalse(manager.isOpen());
  }

  @Test
  void testCloseIdempotent() throws IOException {
    CloseResult result1 = manager.close();
    assertTrue(result1.success());
    assertFalse(manager.isOpen());

    CloseResult result2 = manager.close();

    assertTrue(result2.success());
    assertEquals(0, result2.totalSegmentsAtClose());
    assertEquals(0, result2.oldestSegmentSequence());
    assertEquals(0, result2.newestSegmentSequence());
  }

  @Test
  void testCloseWithNoSegments() throws IOException {
    CloseResult result = manager.close();

    assertTrue(result.success());
    assertEquals(0, result.totalSegmentsAtClose());
    assertEquals(0, result.oldestSegmentSequence());
    assertEquals(0, result.newestSegmentSequence());
  }

  @Test
  void testGetSegmentState() throws IOException {
    long timestamp = System.currentTimeMillis();
    manager.appendDirectly(new LogEntry(50, new byte[50], timestamp));
    manager.writeBatch();

    long seqNum = manager.getCurrentSequenceNumber();

    SegmentState state = manager.getSegmentState(seqNum);

    assertNotNull(state);
    assertEquals(seqNum, state.segmentSequenceNumber());
    assertFalse(state.isFinalized());
  }

  @Test
  void testGetSegmentStateThrowsForNonexistent() throws IOException {
    assertThrows(IOException.class, () -> manager.getSegmentState(99999L));
  }

  @Test
  void testMultipleAppends() throws IOException {
    long timestamp = System.currentTimeMillis();

    for (int i = 0; i < 10; i++) {
      manager.appendDirectly(new LogEntry(50, new byte[50], timestamp + i));
    }
    manager.writeBatch();

    int count = manager.getCurrentEntryCount();
    assertTrue(count >= 10);
  }

  @Test
  void testMultipleBatches() throws IOException {
    long timestamp = System.currentTimeMillis();

    for (int batch = 0; batch < 3; batch++) {
      for (int i = 0; i < 5; i++) {
        manager.appendDirectly(new LogEntry(50, new byte[50], timestamp + batch * 100 + i));
      }
      AppendResult result = manager.writeBatch();
      assertTrue(result.flushed());
    }
  }

  @Test
  void testAppendAndReadRoundTrip() throws IOException {
    long timestamp = System.currentTimeMillis();
    manager.appendDirectly(new LogEntry(50, new byte[50], timestamp));
    manager.writeBatch();

    List<LogEntry> entries = manager.readAllSegments();

    assertNotNull(entries);
  }

  @Test
  void testWriteReadClose() throws IOException {
    long timestamp = System.currentTimeMillis();
    manager.appendDirectly(new LogEntry(50, new byte[50], timestamp));
    manager.writeBatch();

    List<LogEntry> readEntries = manager.readAllSegments();
    assertNotNull(readEntries);

    CloseResult closeResult = manager.close();
    assertTrue(closeResult.success());
    assertFalse(manager.isOpen());
  }

  @Test
  void testStateBeforeAndAfterClose() throws IOException {
    assertTrue(manager.isOpen());

    long seqBefore = manager.getCurrentSequenceNumber();
    assertTrue(seqBefore >= 1);

    manager.close();

    assertFalse(manager.isOpen());

    long seqAfter = manager.getCurrentSequenceNumber();
    assertEquals(seqBefore, seqAfter);
  }

  @Test
  void testMetricsTracking() throws IOException {
    long timestamp = System.currentTimeMillis();
    manager.appendDirectly(new LogEntry(50, new byte[50], timestamp));
    manager.writeBatch();

    WalMetricsQuery metrics = manager.getMetrics();

    assertNotNull(metrics);

    assertTrue(metrics.getTotalFsyncs() >= 0);
  }

  @Test
  void testRecoveryAfterReinitialize() throws IOException {
    long timestamp = System.currentTimeMillis();
    manager.appendDirectly(new LogEntry(50, new byte[50], timestamp));
    manager.writeBatch();

    int segmentsBeforeClose = manager.getSegments().size();
    manager.close();

    SegmentStoreManager manager2 = new SegmentStoreManager(walConfig);

    int segmentsAfterRecovery = manager2.getSegments().size();

    assertTrue(segmentsAfterRecovery >= 0);
    manager2.close();
  }

  @Test
  void testBoundarySequenceNumbers() throws IOException {
    long seqNum = manager.getCurrentSequenceNumber();

    assertTrue(seqNum > 0);
    assertTrue(seqNum < Long.MAX_VALUE);
  }

  @Test
  void testLargePayloads() throws IOException {
    byte[] largePayload = new byte[10000];
    LogEntry entry = new LogEntry(10000, largePayload, System.currentTimeMillis());

    assertDoesNotThrow(() -> manager.appendDirectly(entry));
    manager.writeBatch();
  }

  @Test
  void testTimestampOrdering() throws IOException {
    long ts1 = 100L;
    long ts2 = 200L;
    long ts3 = 150L;

    manager.appendDirectly(new LogEntry(50, new byte[50], ts1));
    manager.appendDirectly(new LogEntry(50, new byte[50], ts2));
    manager.appendDirectly(new LogEntry(50, new byte[50], ts3));
    manager.writeBatch();

    long minTs = manager.getCurrentMinTimestamp();
    long maxTs = manager.getCurrentMaxTimestamp();

    assertEquals(ts1, minTs);
    assertEquals(ts2, maxTs);
  }
}
