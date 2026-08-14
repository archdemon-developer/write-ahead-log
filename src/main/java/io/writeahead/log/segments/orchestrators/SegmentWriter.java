package io.writeahead.log.segments.orchestrators;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.config.WalConstants;
import io.writeahead.log.fsync.executors.FsyncExecutor;
import io.writeahead.log.fsync.executors.FsyncExecutorFactory;
import io.writeahead.log.fsync.retryers.FsyncRetryStrategy;
import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.metrics.WalMetricsRecorder;
import io.writeahead.log.models.*;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.models.results.AppendResult;
import io.writeahead.log.models.states.BatchState;
import io.writeahead.log.models.states.RotationDecision;
import io.writeahead.log.models.states.SegmentFinalizationData;
import io.writeahead.log.models.states.SegmentState;
import io.writeahead.log.segments.management.SegmentLifecycleManager;
import io.writeahead.log.segments.operators.SegmentCollection;
import io.writeahead.log.segments.policies.RotationPolicy;
import io.writeahead.log.serdes.EntrySerdes;
import io.writeahead.log.utils.Crc32Utils;
import io.writeahead.log.utils.FileUtils;
import io.writeahead.log.utils.WalErrorClassifier;
import java.io.File;
import java.io.IOException;

public class SegmentWriter {

  private static final Logger log = LoggerFactory.getLogger(SegmentWriter.class);

  private final SegmentLifecycleManager lifecycleManager;
  private final WalConfiguration config;
  private final SegmentCollection segmentCollection;

  private long nextSegmentSequence;

  private volatile long currentSequenceNumber;
  private volatile FileStream currentStream;
  private volatile long currentStreamSize;
  private volatile int currentEntryCount;
  private volatile long currentMinTimestamp;
  private volatile long currentMaxTimestamp;
  private volatile long currentSegmentCreatedAt;

  private FsyncExecutor fsyncExecutor;

  private final FsyncRetryStrategy fsyncRetryStrategy;
  private final WalMetricsRecorder metrics;
  private final RotationPolicy rotationPolicy;

  public SegmentWriter(
      SegmentLifecycleManager lifecycleManager,
      WalConfiguration config,
      SegmentCollection segmentCollection,
      long nextSegmentSequence,
      FsyncRetryStrategy fsyncRetryStrategy,
      WalMetricsRecorder metrics,
      RotationPolicy rotationPolicy)
      throws IOException {
    this.lifecycleManager = lifecycleManager;
    this.config = config;
    this.segmentCollection = segmentCollection;
    this.nextSegmentSequence = nextSegmentSequence;
    this.fsyncRetryStrategy = fsyncRetryStrategy;
    this.metrics = metrics;
    this.rotationPolicy = rotationPolicy;

    this.currentSequenceNumber = nextSegmentSequence;
    this.nextSegmentSequence += 1;
    this.currentStream = lifecycleManager.createNewSegment(currentSequenceNumber);
    this.currentStreamSize = WalConstants.SEGMENT_HEADER_SIZE;
    this.currentEntryCount = 0;
    this.currentMinTimestamp = Long.MAX_VALUE;
    this.currentMaxTimestamp = Long.MIN_VALUE;
    this.currentSegmentCreatedAt = System.currentTimeMillis();

    this.fsyncExecutor =
        FsyncExecutorFactory.create(config.fsyncStrategy(), fsyncRetryStrategy, currentStream);
  }

  public void appendDirectly(LogEntry entry) throws IOException {
    writeEntryToStream(entry);
  }

  public AppendResult writeBatch() throws IOException {
    try {
      fsyncExecutor.onBatchComplete();
    } catch (IOException ex) {
      throw WalErrorClassifier.classifyIOException(ex, "fsync after batch complete");
    }

    SegmentState currentSegState =
        new SegmentState(
            currentSequenceNumber,
            currentEntryCount,
            currentStreamSize,
            currentMinTimestamp,
            currentMaxTimestamp,
            currentSegmentCreatedAt,
            false);

    RotationDecision rotationDecision =
        segmentCollection.shouldRotate(rotationPolicy, currentSegState, config.maxSegmentSize());

    if (rotationDecision.needsRotation()) {
      rotateSegment();
    }

    return AppendResult.successfulAppendWithFlush(
        currentSequenceNumber,
        currentEntryCount,
        currentStreamSize,
        currentEntryCount > 0 ? currentMinTimestamp : 0L,
        currentEntryCount > 0 ? currentMaxTimestamp : 0L);
  }

  private void rotateSegment() throws IOException {
    try {
      lifecycleManager.finalizeSegment(
          currentStream,
          new SegmentFinalizationData(currentEntryCount, currentMinTimestamp, currentMaxTimestamp));
    } catch (IOException ex) {
      throw WalErrorClassifier.classifyIOException(ex, "finalize segment during rotation");
    }

    SegmentMetadata completedMetadata =
        new SegmentMetadata(
            SegmentLifecycleManager.generateSegmentFilename(currentSequenceNumber),
            currentSequenceNumber,
            System.currentTimeMillis(),
            currentStreamSize,
            currentEntryCount,
            currentMinTimestamp,
            currentMaxTimestamp);

    segmentCollection.add(completedMetadata);
    currentSequenceNumber = nextSegmentSequence++;
    try {
      this.currentStream = lifecycleManager.createNewSegment(currentSequenceNumber);
    } catch (IOException ex) {
      throw WalErrorClassifier.classifyIOException(ex, "create new segment during rotation");
    }
    this.currentSegmentCreatedAt = System.currentTimeMillis();
    this.currentStreamSize = WalConstants.SEGMENT_HEADER_SIZE;
    this.currentEntryCount = 0;
    this.currentMinTimestamp = Long.MAX_VALUE;
    this.currentMaxTimestamp = Long.MIN_VALUE;

    this.fsyncExecutor =
        FsyncExecutorFactory.create(config.fsyncStrategy(), fsyncRetryStrategy, currentStream);

    metrics.recordSegmentRotation();
    metrics.setTotalSegmentCount(segmentCollection.size());

    log.info("Rotated segment: {} -> {}", currentSequenceNumber - 1, currentSequenceNumber);
  }

  public long getCurrentSequenceNumber() {
    return currentSequenceNumber;
  }

  public int getCurrentEntryCount() {
    return currentEntryCount;
  }

  public long getCurrentStreamSize() {
    return currentStreamSize;
  }

  public long getCurrentMinTimestamp() {
    return currentMinTimestamp;
  }

  public long getCurrentMaxTimestamp() {
    return currentMaxTimestamp;
  }

  public long getCurrentSegmentCreatedAt() {
    return currentSegmentCreatedAt;
  }

  public String getCurrentSegmentFilename() {
    return SegmentLifecycleManager.generateSegmentFilename(currentSequenceNumber);
  }

  public BatchState getBatchState() {
    if (currentEntryCount == 0) {
      return BatchState.emptyBatch();
    }
    return BatchState.withPendingEntries(
        currentEntryCount,
        currentStreamSize - WalConstants.SEGMENT_HEADER_SIZE,
        currentMinTimestamp,
        currentMaxTimestamp);
  }

  public void close() throws IOException {
    if (currentEntryCount == 0) {
      log.warn("Segment {} has 0 entries, deleting empty file", currentSequenceNumber);
      try {
        if (currentStream != null) {
          currentStream.closeAll();
        }
      } catch (IOException ex) {
        log.error("Failed to close empty segment stream", ex);
      }
      File emptyFile = new File(config.logDir(), getCurrentSegmentFilename());
      if (emptyFile.exists() && !emptyFile.delete()) {
        log.warn("Failed to delete zero-entry segment file: {}", emptyFile.getName());
      }
      return;
    }

    lifecycleManager.finalizeSegment(
        currentStream,
        new SegmentFinalizationData(currentEntryCount, currentMinTimestamp, currentMaxTimestamp));

    SegmentMetadata currentMetadata =
        new SegmentMetadata(
            SegmentLifecycleManager.generateSegmentFilename(currentSequenceNumber),
            currentSequenceNumber,
            System.currentTimeMillis(),
            currentStreamSize,
            currentEntryCount,
            currentMinTimestamp,
            currentMaxTimestamp);

    segmentCollection.add(currentMetadata);
  }

  private void writeEntryToStream(LogEntry entry) throws IOException {

    long crc = Crc32Utils.computeEntryCrc(entry.timestamp(), entry.data().length, entry.data());

    byte[] entryBytes =
        EntrySerdes.serializeEntryWithCrc(
            entry.timestamp(), entry.data().length, entry.data(), crc);

    try {
      FileUtils.writeToStream(currentStream, entryBytes);
    } catch (IOException ex) {
      throw WalErrorClassifier.classifyIOException(ex, "write entry to segment");
    }

    currentStreamSize += entryBytes.length;
    currentEntryCount++;
    currentMinTimestamp = Math.min(currentMinTimestamp, entry.timestamp());
    currentMaxTimestamp = Math.max(currentMaxTimestamp, entry.timestamp());

    metrics.recordEntryAppended(entry.data().length);
  }
}
