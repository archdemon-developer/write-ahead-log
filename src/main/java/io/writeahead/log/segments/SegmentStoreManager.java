package io.writeahead.log.segments;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.fsync.retryers.FsyncRetryStrategy;
import io.writeahead.log.fsync.retryers.FsyncRetryStrategyFactory;
import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.metrics.SimpleWalMetrics;
import io.writeahead.log.metrics.WalMetricsQuery;
import io.writeahead.log.metrics.WalMetricsRecorder;
import io.writeahead.log.models.*;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.models.meta.WalMetadata;
import io.writeahead.log.models.results.AppendResult;
import io.writeahead.log.models.results.CloseResult;
import io.writeahead.log.models.results.TruncateResult;
import io.writeahead.log.models.states.BatchState;
import io.writeahead.log.models.states.SegmentState;
import io.writeahead.log.models.states.WalSnapshot;
import io.writeahead.log.segments.filter.reads.ReadFilter;
import io.writeahead.log.segments.filter.truncate.TruncateFilter;
import io.writeahead.log.segments.management.SegmentLifecycleManager;
import io.writeahead.log.segments.management.SegmentMetadataRecovery;
import io.writeahead.log.segments.operators.BatchBuffer;
import io.writeahead.log.segments.operators.SegmentCollection;
import io.writeahead.log.segments.operators.SegmentEntriesReader;
import io.writeahead.log.segments.orchestrators.SegmentReader;
import io.writeahead.log.segments.orchestrators.SegmentTruncator;
import io.writeahead.log.segments.orchestrators.SegmentWriter;
import io.writeahead.log.segments.policies.RotationPolicy;
import io.writeahead.log.segments.policies.RotationPolicyFactory;
import io.writeahead.log.segments.stores.SegmentStore;
import io.writeahead.log.utils.WalErrorClassifier;
import java.io.IOException;
import java.util.List;

public class SegmentStoreManager implements SegmentStore {

  private static final Logger log = LoggerFactory.getLogger(SegmentStoreManager.class);

  private final SegmentCollection segmentCollection;

  private final WalMetricsRecorder metrics;

  private final SegmentWriter writer;
  private final SegmentReader reader;
  private final SegmentTruncator truncator;

  private boolean isOpen;

  public SegmentStoreManager(WalConfiguration config) throws IOException {
    this.metrics = new SimpleWalMetrics();

    SegmentMetadataRecovery metadataRecovery =
        new SegmentMetadataRecovery(config.logDir(), metrics);

    WalMetadata walMetadata = metadataRecovery.recover();
    this.segmentCollection = new SegmentCollection();
    for (SegmentMetadata metadata : walMetadata.segments()) {
      segmentCollection.add(metadata);
    }

    RotationPolicy rotationPolicy = RotationPolicyFactory.create(config.rotationPolicyType());
    FsyncRetryStrategy fsyncRetryStrategy = FsyncRetryStrategyFactory.create(config, metrics);
    BatchBuffer batchBuffer = new BatchBuffer();

    this.writer =
        new SegmentWriter(
            new SegmentLifecycleManager(config.logDir()),
            config,
            segmentCollection,
            walMetadata.nextSequence(),
            batchBuffer,
            fsyncRetryStrategy,
            metrics,
            rotationPolicy);

    this.reader =
        new SegmentReader(new SegmentEntriesReader(metrics), segmentCollection, config, metrics);

    this.truncator = new SegmentTruncator(segmentCollection, config);

    this.isOpen = true;

    log.info(
        "SegmentStoreManager initialized: {} segments recovered, next sequence: {}",
        segmentCollection.size(),
        walMetadata.nextSequence());
  }

  public void appendDirectly(LogEntry entry) throws IOException {
    if (!isOpen) {
      throw WalErrorClassifier.classifyIOException(
          new IOException("SegmentStoreManager is closed"), "appendDirectly on closed WAL");
    }

    writer.appendDirectly(entry);
  }

  @Override
  public AppendResult writeBatch() throws IOException {
    if (!isOpen) {
      throw WalErrorClassifier.classifyIOException(
          new IOException("SegmentStoreManager is closed"), "writeBatch on closed WAL");
    }
    return writer.writeBatch();
  }

  @Override
  public List<LogEntry> readAllSegments() throws IOException {
    return reader.readAllSegments();
  }

  @Override
  public List<LogEntry> readAllMatching(ReadFilter filter) throws IOException {
    return reader.readAllMatching(filter);
  }

  public List<LogEntry> readAllAfterTimestamp(long timestamp) throws IOException {
    return reader.readAllAfterTimestamp(timestamp);
  }

  @Override
  public TruncateResult truncateAllMatching(TruncateFilter filter) throws IOException {
    return truncator.truncateAllMatching(filter);
  }

  public TruncateResult truncateBeforeTimestamp(long timestamp) throws IOException {
    return truncator.truncateBeforeTimestamp(timestamp);
  }

  public long getCurrentSequenceNumber() {
    return writer.getCurrentSequenceNumber();
  }

  public int getCurrentEntryCount() {
    return writer.getCurrentEntryCount();
  }

  public long getCurrentStreamSize() {
    return writer.getCurrentStreamSize();
  }

  public long getCurrentMinTimestamp() {
    return writer.getCurrentMinTimestamp();
  }

  public long getCurrentMaxTimestamp() {
    return writer.getCurrentMaxTimestamp();
  }

  public long getCurrentSegmentCreatedAt() {
    return writer.getCurrentSegmentCreatedAt();
  }

  public BatchState getBatchState() {
    return writer.getBatchState();
  }

  public boolean isOpen() {
    return isOpen;
  }

  public WalMetricsQuery getMetrics() {
    return (WalMetricsQuery) metrics;
  }

  public List<SegmentMetadata> getSegments() {
    return segmentCollection.getSegments();
  }

  @Override
  public CloseResult close() throws IOException {
    if (!isOpen) {
      long oldestSeq = segmentCollection.getOldestSequenceNumber();
      long newestSeq = segmentCollection.getNewestSequenceNumber();
      return CloseResult.successfulClose(segmentCollection.size(), oldestSeq, newestSeq, 0, 0);
    }

    try {
      writer.close();
      isOpen = false;

      long oldestSeq = segmentCollection.getOldestSequenceNumber();
      long newestSeq = segmentCollection.getNewestSequenceNumber();
      long totalEntries = 0;
      long totalBytes = 0;
      for (SegmentMetadata seg : segmentCollection.getSegments()) {
        totalEntries += seg.entryCount();
        totalBytes += seg.fileSize();
      }

      return CloseResult.successfulClose(
          segmentCollection.size(), oldestSeq, newestSeq, totalEntries, totalBytes);
    } catch (IOException ex) {
      isOpen = false;
      return CloseResult.closeFailed(
          segmentCollection.size(),
          segmentCollection.getOldestSequenceNumber(),
          segmentCollection.getNewestSequenceNumber(),
          ex.getMessage());
    }
  }

  @Override
  public WalSnapshot getSnapshot() throws IOException {
    return WalSnapshot.of(
        getSegments(),
        getCurrentSequenceNumber(),
        getCurrentStreamSize(),
        getCurrentEntryCount(),
        getCurrentMinTimestamp(),
        getCurrentMaxTimestamp(),
        getCurrentSegmentCreatedAt(),
        getBatchState(),
        getMetrics(),
        isOpen());
  }

  @Override
  public SegmentState getSegmentState(long sequenceNumber) throws IOException {
    for (SegmentMetadata metadata : segmentCollection.getSegments()) {
      if (metadata.sequenceNumber() == sequenceNumber) {
        return new SegmentState(
            metadata.sequenceNumber(),
            metadata.entryCount(),
            metadata.fileSize(),
            metadata.minTimestamp(),
            metadata.maxTimestamp(),
            metadata.createdAt(),
            true);
      }
    }

    if (writer.getCurrentSequenceNumber() == sequenceNumber) {
      return new SegmentState(
          writer.getCurrentSequenceNumber(),
          writer.getCurrentEntryCount(),
          writer.getCurrentStreamSize(),
          writer.getCurrentMinTimestamp(),
          writer.getCurrentMaxTimestamp(),
          writer.getCurrentSegmentCreatedAt(),
          false);
    }

    throw new IOException("Segment with sequence number " + sequenceNumber + " not found");
  }
}
