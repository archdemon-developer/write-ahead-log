package io.writeahead.log.segments.orchestrators;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.enums.strategies.RotationPolicyType;
import io.writeahead.log.fsync.retryers.FsyncRetryStrategy;
import io.writeahead.log.fsync.retryers.FsyncRetryStrategyFactory;
import io.writeahead.log.metrics.SimpleWalMetrics;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.models.results.AppendResult;
import io.writeahead.log.models.states.BatchState;
import io.writeahead.log.segments.management.SegmentLifecycleManager;
import io.writeahead.log.segments.operators.SegmentCollection;
import io.writeahead.log.segments.policies.RotationPolicy;
import io.writeahead.log.segments.policies.RotationPolicyFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegmentWriterTest {

  @TempDir Path tempDir;

  private SegmentWriter writer;
  private SegmentLifecycleManager lifecycleManager;
  private WalConfiguration walConfig;
  private SegmentCollection segmentCollection;
  private SimpleWalMetrics metrics;
  private FsyncRetryStrategy fsyncRetryStrategy;
  private RotationPolicy rotationPolicy;

  @BeforeEach
  void setUp() throws IOException {
    String logDir = tempDir.toString();
    Files.createDirectories(tempDir);

    walConfig =
        new WalConfiguration.Builder()
            .logDir(logDir)
            .batchSize(10)
            .maxSegmentSize(1024)
            .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
            .rotationPolicyType(RotationPolicyType.SIZE_BASED)
            .maxRetries(3)
            .retryBackoffMs(10)
            .retryBackoffMultiplier(2.0)
            .build();

    metrics = new SimpleWalMetrics();
    lifecycleManager = new SegmentLifecycleManager(logDir);
    segmentCollection = new SegmentCollection();
    fsyncRetryStrategy = FsyncRetryStrategyFactory.create(walConfig, metrics);
    rotationPolicy = RotationPolicyFactory.create(walConfig.rotationPolicyType());

    writer =
        new SegmentWriter(
            lifecycleManager,
            walConfig,
            segmentCollection,
            1,
            fsyncRetryStrategy,
            metrics,
            rotationPolicy);
  }

  @Test
  void testConstructorInitializesSequenceNumber() {
    assertEquals(1, writer.getCurrentSequenceNumber());
  }

  @Test
  void testConstructorInitializesEntryCount() {
    assertEquals(0, writer.getCurrentEntryCount());
  }

  @Test
  void testConstructorInitializesStreamSize() {
    assertEquals(48, writer.getCurrentStreamSize()); // SEGMENT_HEADER_SIZE
  }

  @Test
  void testConstructorInitializesTimestamps() {
    assertEquals(Long.MAX_VALUE, writer.getCurrentMinTimestamp());
    assertEquals(Long.MIN_VALUE, writer.getCurrentMaxTimestamp());
  }

  @Test
  void testConstructorWithCustomSequence() throws IOException {
    SegmentWriter customWriter =
        new SegmentWriter(
            lifecycleManager,
            walConfig,
            segmentCollection,
            42,
            fsyncRetryStrategy,
            metrics,
            rotationPolicy);
    assertEquals(42, customWriter.getCurrentSequenceNumber());
  }

  @Test
  void testAppendDirectlyAddsEntry() throws IOException {
    LogEntry entry = new LogEntry(10, new byte[10], System.currentTimeMillis());
    writer.appendDirectly(entry);

    assertTrue(writer.getCurrentEntryCount() > 0);
  }

  @Test
  void testAppendDirectlyUpdatesTimestamps() throws IOException {
    long timestamp = 1000L;
    LogEntry entry = new LogEntry(5, new byte[5], timestamp);
    writer.appendDirectly(entry);

    assertTrue(writer.getCurrentMinTimestamp() <= timestamp);
    assertTrue(writer.getCurrentMaxTimestamp() >= timestamp);
  }

  @Test
  void testAppendDirectlyIncreasesStreamSize() throws IOException {
    long initialSize = writer.getCurrentStreamSize();
    LogEntry entry = new LogEntry(10, new byte[10], System.currentTimeMillis());
    writer.appendDirectly(entry);

    assertTrue(writer.getCurrentStreamSize() > initialSize);
  }

  @Test
  void testWriteBatchReturnsAppendResult() throws IOException {
    writer.appendDirectly(new LogEntry(5, new byte[5], System.currentTimeMillis()));
    AppendResult result = writer.writeBatch();

    assertNotNull(result);
    assertTrue(result.flushed());
  }

  @Test
  void testWriteBatchFlushesMultipleTimes() throws IOException {
    long timestamp = System.currentTimeMillis();

    writer.appendDirectly(new LogEntry(5, new byte[5], timestamp));
    AppendResult result1 = writer.writeBatch();
    assertTrue(result1.flushed());

    writer.appendDirectly(new LogEntry(5, new byte[5], timestamp + 1));
    AppendResult result2 = writer.writeBatch();
    assertTrue(result2.flushed());
  }

  @Test
  void testWriteBatchMultipleTimes() throws IOException {
    for (int i = 0; i < 3; i++) {
      writer.appendDirectly(new LogEntry(5, new byte[5], System.currentTimeMillis()));
      AppendResult result = writer.writeBatch();
      assertTrue(result.flushed());
    }
  }

  @Test
  void testCurrentSequenceNumberAccessor() {
    assertEquals(1, writer.getCurrentSequenceNumber());
  }

  @Test
  void testCurrentEntryCountAccessor() throws IOException {
    assertEquals(0, writer.getCurrentEntryCount());
    writer.appendDirectly(new LogEntry(5, new byte[5], System.currentTimeMillis()));
    assertTrue(writer.getCurrentEntryCount() > 0);
  }

  @Test
  void testCurrentStreamSizeAccessor() {
    long initialSize = writer.getCurrentStreamSize();
    assertEquals(48, initialSize);
  }

  @Test
  void testMinTimestampAccessor() {
    assertEquals(Long.MAX_VALUE, writer.getCurrentMinTimestamp());
  }

  @Test
  void testMaxTimestampAccessor() {
    assertEquals(Long.MIN_VALUE, writer.getCurrentMaxTimestamp());
  }

  @Test
  void testSegmentCreatedAtAccessor() {
    long createdAt = writer.getCurrentSegmentCreatedAt();
    assertTrue(createdAt > 0);
  }

  @Test
  void testGetBatchState() throws IOException {
    BatchState state = writer.getBatchState();
    assertNotNull(state);
  }

  @Test
  void testCloseWithEntries() throws IOException {
    long timestamp = System.currentTimeMillis();
    writer.appendDirectly(new LogEntry(50, new byte[50], timestamp));
    writer.close();

    assertEquals(1, segmentCollection.size());
  }

  @Test
  void testCloseEmptySegment() throws IOException {
    writer.close();

    assertEquals(0, segmentCollection.size());
  }

  @Test
  void testCloseWithMultipleEntries() throws IOException {
    for (int i = 0; i < 5; i++) {
      writer.appendDirectly(new LogEntry(10, new byte[10], System.currentTimeMillis()));
    }
    writer.close();

    assertEquals(1, segmentCollection.size());
    SegmentMetadata metadata = segmentCollection.getSegments().get(0);
    assertEquals(5, metadata.entryCount());
  }

  @Test
  void testAppendEmptyPayload() throws IOException {
    LogEntry entry = new LogEntry(0, new byte[0], System.currentTimeMillis());
    writer.appendDirectly(entry);

    assertTrue(writer.getCurrentEntryCount() > 0);
  }

  @Test
  void testAppendLargePayload() throws IOException {
    byte[] largePayload = new byte[1000];
    LogEntry entry = new LogEntry(1000, largePayload, System.currentTimeMillis());
    writer.appendDirectly(entry);

    assertTrue(writer.getCurrentStreamSize() > 1000);
  }

  @Test
  void testAppendMultipleEntriesMaintainsOrder() throws IOException {
    long ts1 = 100L;
    long ts2 = 200L;
    long ts3 = 150L;

    writer.appendDirectly(new LogEntry(5, new byte[5], ts1));
    writer.appendDirectly(new LogEntry(5, new byte[5], ts2));
    writer.appendDirectly(new LogEntry(5, new byte[5], ts3));

    assertEquals(ts1, writer.getCurrentMinTimestamp());
    assertEquals(ts2, writer.getCurrentMaxTimestamp());
  }

  @Test
  void testTimestampBoundaryValues() throws IOException {
    writer.appendDirectly(new LogEntry(5, new byte[5], Long.MIN_VALUE));
    assertEquals(Long.MIN_VALUE, writer.getCurrentMinTimestamp());
  }

  @Test
  void testTimestampBoundaryMax() throws IOException {
    writer.appendDirectly(new LogEntry(5, new byte[5], Long.MAX_VALUE - 1));
    assertEquals(Long.MAX_VALUE - 1, writer.getCurrentMaxTimestamp());
  }

  @Test
  void testWriteBatchReturnsValidSequenceNumber() throws IOException {
    writer.appendDirectly(new LogEntry(5, new byte[5], System.currentTimeMillis()));
    AppendResult result = writer.writeBatch();

    assertEquals(1, result.currentSegmentSequenceNumber());
  }

  @Test
  void testWriteBatchReturnsValidEntryCount() throws IOException {
    writer.appendDirectly(new LogEntry(5, new byte[5], System.currentTimeMillis()));
    AppendResult result = writer.writeBatch();

    assertTrue(result.currentSegmentEntryCount() >= 1);
  }

  @Test
  void testWriteBatchReturnsValidByteCount() throws IOException {
    writer.appendDirectly(new LogEntry(5, new byte[5], System.currentTimeMillis()));
    AppendResult result = writer.writeBatch();

    assertTrue(result.currentSegmentByteCount() >= 48);
  }

  @Test
  void testAppendResultNoCorruptionInitially() throws IOException {
    writer.appendDirectly(new LogEntry(5, new byte[5], System.currentTimeMillis()));
    AppendResult result = writer.writeBatch();

    assertFalse(result.corruptionDetected());
    assertNull(result.errorMessage());
  }

  @Test
  void testMultipleAppendsBoundaryTimestamp() throws IOException {
    long ts1 = 50L;
    long ts2 = 100L;
    long ts3 = 75L;

    writer.appendDirectly(new LogEntry(5, new byte[5], ts1));
    writer.appendDirectly(new LogEntry(5, new byte[5], ts2));
    writer.appendDirectly(new LogEntry(5, new byte[5], ts3));

    assertEquals(ts1, writer.getCurrentMinTimestamp());
    assertEquals(ts2, writer.getCurrentMaxTimestamp());
  }
}
