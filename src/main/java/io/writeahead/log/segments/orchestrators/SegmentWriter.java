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
import io.writeahead.log.models.results.BatchFlushResult;
import io.writeahead.log.models.states.BatchState;
import io.writeahead.log.models.states.RotationDecision;
import io.writeahead.log.models.states.SegmentFinalizationData;
import io.writeahead.log.models.states.SegmentState;
import io.writeahead.log.segments.management.SegmentLifecycleManager;
import io.writeahead.log.segments.operators.BatchBuffer;
import io.writeahead.log.segments.operators.SegmentCollection;
import io.writeahead.log.segments.policies.RotationPolicy;
import io.writeahead.log.serdes.EntrySerdes;
import io.writeahead.log.utils.Crc32Utils;
import io.writeahead.log.utils.FileUtils;
import io.writeahead.log.utils.WalErrorClassifier;
import java.io.IOException;
import java.util.List;

public class SegmentWriter {

  private static final Logger log = LoggerFactory.getLogger(SegmentWriter.class);

  private final SegmentLifecycleManager lifecycleManager;
  private final WalConfiguration config;
  private final SegmentCollection segmentCollection;

  private long nextSegmentSequence;
  private long currentSequenceNumber;
  private FileStream currentStream;
  private long currentStreamSize;
  private int currentEntryCount;
  private long currentMinTimestamp;
  private long currentMaxTimestamp;
  private long currentSegmentCreatedAt;

  private final BatchBuffer batchBuffer;

  private FsyncExecutor fsyncExecutor;

  private final FsyncRetryStrategy fsyncRetryStrategy;
  private final WalMetricsRecorder metrics;
  private final RotationPolicy rotationPolicy;

  public SegmentWriter(
      SegmentLifecycleManager lifecycleManager,
      WalConfiguration config,
      SegmentCollection segmentCollection,
      long nextSegmentSequence,
      BatchBuffer batchBuffer,
      FsyncRetryStrategy fsyncRetryStrategy,
      WalMetricsRecorder metrics,
      RotationPolicy rotationPolicy)
      throws IOException {
    this.lifecycleManager = lifecycleManager;
    this.config = config;
    this.segmentCollection = segmentCollection;
    this.nextSegmentSequence = nextSegmentSequence;
    this.batchBuffer = batchBuffer;
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
    if (!batchBuffer.isEmpty()) {
      BatchFlushResult flushResult = batchBuffer.writeBatch();
      List<LogEntry> entriesToWrite = flushResult.entries();

      for (LogEntry entry : entriesToWrite) {
        writeEntryToStream(entry);

        try {
          fsyncExecutor.onEntryWritten();
        } catch (IOException ex) {
          throw WalErrorClassifier.classifyIOException(ex, "fsync after entry write");
        }
      }
    }

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
        currentMinTimestamp,
        currentMaxTimestamp);
  }

  private void flushAndFsync() throws IOException {
    if (batchBuffer.isEmpty()) {
      return;
    }

    BatchFlushResult flushResult = batchBuffer.writeBatch();
    List<LogEntry> entriesToWrite = flushResult.entries();

    for (LogEntry entry : entriesToWrite) {
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

      try {
        fsyncExecutor.onEntryWritten();
      } catch (IOException ex) {
        throw WalErrorClassifier.classifyIOException(ex, "fsync after entry write");
      }
      metrics.recordEntryAppended(entry.data().length);
    }

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

  public BatchState getBatchState() {
    return batchBuffer.getBatchState();
  }

  public void close() throws IOException {
    if (!batchBuffer.isEmpty()) {
      writeBatch();
    }

    if (currentEntryCount == 0) {
      log.warn("Segment {} has 0 entries, skipping finalization", currentSequenceNumber);
      try {
        if (currentStream != null) {
          currentStream.dataOutputStream().close();
          currentStream.fileOutputStream().close();
        }
      } catch (IOException ex) {
        log.error("Failed to close empty segment stream", ex);
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
