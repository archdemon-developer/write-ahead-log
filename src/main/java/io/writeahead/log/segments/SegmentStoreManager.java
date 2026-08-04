package io.writeahead.log.segments;

import io.writeahead.log.constants.WalConstants;
import io.writeahead.log.enums.CorruptionType;
import io.writeahead.log.fsync.FsyncExecutor;
import io.writeahead.log.fsync.FsyncExecutorFactory;
import io.writeahead.log.fsync.FsyncRetryStrategy;
import io.writeahead.log.fsync.FsyncRetryStrategyFactory;
import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.metrics.SimpleWalMetrics;
import io.writeahead.log.metrics.WalMetricsQuery;
import io.writeahead.log.metrics.WalMetricsRecorder;
import io.writeahead.log.models.*;
import io.writeahead.log.serdes.EntrySerdes;
import io.writeahead.log.utils.Crc32Utils;
import io.writeahead.log.utils.FileUtils;
import io.writeahead.log.utils.WalErrorClassifier;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SegmentStoreManager implements SegmentStore {

  private static final Logger log = LoggerFactory.getLogger(SegmentStoreManager.class);

  private final SegmentLifecycleManager lifecycleManager;
  private final SegmentEntriesReader segmentReader;
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

  private boolean isOpen;

  public SegmentStoreManager(WalConfiguration config) throws IOException {
    this.config = config;

    this.metrics = new SimpleWalMetrics();

    SegmentMetadataRecovery metadataRecovery =
        new SegmentMetadataRecovery(config.logDir(), metrics);
    this.lifecycleManager = new SegmentLifecycleManager(config.logDir());
    this.segmentReader = new SegmentEntriesReader(metrics);

    WalMetadata walMetadata = metadataRecovery.recover();
    this.segmentCollection = new SegmentCollection();
    for (SegmentMetadata metadata : walMetadata.segments()) {
      segmentCollection.add(metadata);
    }
    this.nextSegmentSequence = walMetadata.nextSequence();

    this.currentSequenceNumber = nextSegmentSequence++;
    this.currentStream = lifecycleManager.createNewSegment(currentSequenceNumber);
    this.currentStreamSize = WalConstants.SEGMENT_HEADER_SIZE;
    this.currentEntryCount = 0;
    this.currentMinTimestamp = Long.MAX_VALUE;
    this.currentMaxTimestamp = Long.MIN_VALUE;
    this.currentSegmentCreatedAt = System.currentTimeMillis();

    this.batchBuffer = new BatchBuffer();
    this.rotationPolicy = RotationPolicyFactory.create(config.rotationPolicyType());
    this.fsyncRetryStrategy = FsyncRetryStrategyFactory.create(config, metrics);
    this.fsyncExecutor =
        FsyncExecutorFactory.create(config.fsyncStrategy(), fsyncRetryStrategy, currentStream);

    this.isOpen = true;

    log.info(
        "SegmentStoreManager initialized: {} segments recovered, next sequence: {}",
        segmentCollection.size(),
        nextSegmentSequence);
  }

  public AppendResult append(LogEntry entry) throws IOException {
    if (!isOpen) {
      throw WalErrorClassifier.classifyIOException(
          new IOException("SegmentStoreManager is closed"), "append to closed WAL");
    }

    batchBuffer.append(entry);

    BatchState batchState = batchBuffer.getBatchState();

    if (batchState.wouldExceedCapacity(entry.data().length, config.batchSize())) {
      flushAndFsync();
      return AppendResult.successfulAppendWithFlush(
          currentSequenceNumber,
          currentEntryCount,
          currentStreamSize,
          currentMinTimestamp,
          currentMaxTimestamp);
    }

    return AppendResult.successfulAppendNoFlush(
        (int) batchState.entriesPendingInBatch(),
        currentSequenceNumber,
        currentEntryCount,
        currentStreamSize,
        currentMinTimestamp,
        currentMaxTimestamp);
  }

  public AppendResult writeBatch() throws IOException {
    if (batchBuffer.isEmpty()) {
      log.debug("writeBatch called but batch is empty, nothing to flush");
      return AppendResult.successfulAppendNoFlush(
          0,
          currentSequenceNumber,
          currentEntryCount,
          currentStreamSize,
          currentMinTimestamp,
          currentMaxTimestamp);
    }

    flushAndFsync();
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

  @Override
  public List<LogEntry> readAllSegments() throws IOException {
    List<LogEntry> allEntries = new ArrayList<>();

    for (SegmentMetadata metadata : segmentCollection.getSegments()) {
      File segmentFile = new File(config.logDir(), metadata.filename());
      if (!segmentFile.exists()) {
        log.warn("Segment file not found during read: {}", metadata.filename());
        continue;
      }

      byte[] allBytes = FileUtils.readAllBytes(segmentFile);
      byte[] entryRegionBytes = extractEntryRegion(allBytes);

      SegmentEntriesReader.SegmentReadResult result =
          segmentReader.readEntriesFromRegion(entryRegionBytes);

      allEntries.addAll(result.entries());

      if (result.hasCorruption()) {
        metrics.recordSegmentCorruption();
        metrics.recordCorruptionType(CorruptionType.ENTRY_CRC_MISMATCH);

        log.error(
            "Corruption detected in segment {}: recovered {} entries, corruption at entry {}",
            metadata.filename(),
            result.entriesRead(),
            result.corruptionAtEntry());

        throw WalErrorClassifier.classifyCorruption(
            metadata.filename(),
            0,
            CorruptionType.ENTRY_CRC_MISMATCH,
            0,
            0,
            "Entry corruption at position "
                + result.corruptionAtEntry()
                + " (recovered "
                + result.entriesRead()
                + " entries before corruption)");
      }
    }

    log.debug("Read {} entries from {} segments", allEntries.size(), segmentCollection.size());
    return allEntries;
  }

  public List<LogEntry> readAllMatching(ReadFilter filter) throws IOException {
    List<LogEntry> allEntries = new ArrayList<>();

    for (SegmentMetadata metadata : segmentCollection.getSegments()) {

      if (filter.canSkipSegment(metadata)) {
        log.debug(
            "Skipping segment {} ({}-{}): filter determined no entries match",
            metadata.filename(),
            metadata.minTimestamp(),
            metadata.maxTimestamp());
        continue;
      }

      File segmentFile = new File(config.logDir(), metadata.filename());
      if (!segmentFile.exists()) {
        log.warn("Segment file not found during read: {}", metadata.filename());
        continue;
      }

      byte[] allBytes = FileUtils.readAllBytes(segmentFile);
      byte[] entryRegionBytes = extractEntryRegion(allBytes);

      SegmentEntriesReader.SegmentReadResult result =
          segmentReader.readEntriesFromRegion(entryRegionBytes);

      for (LogEntry entry : result.entries()) {
        if (filter.matches(entry).isAccepted()) {
          allEntries.add(entry);
        }
      }

      if (result.hasCorruption()) {
        metrics.recordSegmentCorruption();
        metrics.recordCorruptionType(CorruptionType.ENTRY_CRC_MISMATCH);

        log.error(
            "Corruption detected in segment {}: recovered {} entries, corruption at entry {}",
            metadata.filename(),
            result.entriesRead(),
            result.corruptionAtEntry());

        throw WalErrorClassifier.classifyCorruption(
            metadata.filename(),
            0,
            CorruptionType.ENTRY_CRC_MISMATCH,
            0,
            0,
            "Entry corruption at position "
                + result.corruptionAtEntry()
                + " (recovered "
                + result.entriesRead()
                + " entries before corruption)");
      }
    }

    log.debug(
        "Read {} entries from {} segments using filter {}",
        allEntries.size(),
        segmentCollection.size(),
        filter.name());
    return allEntries;
  }

  public List<LogEntry> readAllAfterTimestamp(long timestamp) throws IOException {
    return readAllMatching(new AfterTimestampFilter(timestamp));
  }

  @Override
  public TruncateResult truncateAllMatching(TruncateFilter filter) throws IOException {
    TruncateSegmentsResult segmentResult = segmentCollection.truncateMatching(filter);

    if (!segmentResult.wereSegmentsRemoved()) {
      return TruncateResult.nothingToTruncate(segmentResult.oldestRemainingSequence());
    }

    for (SegmentMetadata metadata : segmentResult.getSegmentsToDelete()) {
      File segmentFile = new File(config.logDir(), metadata.filename());
      try {
        FileUtils.deleteFile(segmentFile);
        log.info("Truncated segment: {}", metadata.filename());
      } catch (IOException ex) {
        String errorMsg =
            "Failed to delete segment " + metadata.filename() + ": " + ex.getMessage();
        return TruncateResult.truncationFailed(segmentResult.oldestRemainingSequence(), errorMsg);
      }
    }

    return TruncateResult.successfulTruncate(
        segmentResult.segmentsRemoved(), segmentResult.oldestRemainingSequence());
  }

  public TruncateResult truncateBeforeTimestamp(long timestamp) throws IOException {
    return truncateAllMatching(new BeforeTimestampTruncateFilter(timestamp));
  }

  public CloseResult close() throws IOException {
    if (!isOpen) {
      long oldestSeq = segmentCollection.getOldestSequenceNumber();
      long newestSeq = segmentCollection.getNewestSequenceNumber();
      return CloseResult.successfulClose(segmentCollection.size(), oldestSeq, newestSeq, 0, 0);
    }

    try {
      if (!batchBuffer.isEmpty()) {
        writeBatch();
      }
    } catch (IOException ex) {
      isOpen = false;
      long oldestSeq = segmentCollection.getOldestSequenceNumber();
      long newestSeq = segmentCollection.getNewestSequenceNumber();
      long totalEntries = 0;
      long totalBytes = 0;
      for (SegmentMetadata seg : segmentCollection.getSegments()) {
        totalEntries += seg.entryCount();
        totalBytes += seg.fileSize();
      }
      String errorMsg = "Failed to flush batch before close: " + ex.getMessage();
      return CloseResult.closeWithUnflushedEntries(
          segmentCollection.size() + 1, oldestSeq, newestSeq, totalEntries, totalBytes, errorMsg);
    }

    try {
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

      log.info(
          "SegmentStoreManager closed: finalized segment {} with {} entries",
          currentSequenceNumber,
          currentEntryCount);
    } catch (IOException ex) {
      isOpen = false;
      long oldestSeq = segmentCollection.getOldestSequenceNumber();
      long newestSeq = segmentCollection.getNewestSequenceNumber();
      String errorMsg = "Failed to finalize segment during close: " + ex.getMessage();
      return CloseResult.closeFailed(segmentCollection.size() + 1, oldestSeq, newestSeq, errorMsg);
    } finally {
      isOpen = false;
    }

    long totalEntries = 0;
    long totalBytes = 0;
    for (SegmentMetadata seg : segmentCollection.getSegments()) {
      totalEntries += seg.entryCount();
      totalBytes += seg.fileSize();
    }

    long oldestSeq = segmentCollection.getOldestSequenceNumber();
    long newestSeq = segmentCollection.getNewestSequenceNumber();

    log.info(
        "SegmentStoreManager closed: finalized segment {} with {} entries",
        currentSequenceNumber,
        currentEntryCount);

    return CloseResult.successfulClose(
        segmentCollection.size(), oldestSeq, newestSeq, totalEntries, totalBytes);
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

  private byte[] extractEntryRegion(byte[] allBytes) {
    int headerSize = WalConstants.SEGMENT_HEADER_SIZE;
    int footerSize = WalConstants.SEGMENT_FOOTER_SIZE;

    int entryRegionEnd = allBytes.length - footerSize;

    if (entryRegionEnd <= headerSize) {
      return new byte[0];
    }

    byte[] entryRegion = new byte[entryRegionEnd - headerSize];
    System.arraycopy(allBytes, headerSize, entryRegion, 0, entryRegion.length);
    return entryRegion;
  }

  public WalMetricsQuery getMetrics() {
    return (WalMetricsQuery) metrics;
  }

  public List<SegmentMetadata> getSegments() {
    return segmentCollection.getSegments();
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

  public boolean isOpen() {
    return isOpen;
  }

  @Override
  public WalSnapshot getSnapshot() throws IOException {
    return WalSnapshot.of(this);
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

    if (currentSequenceNumber == sequenceNumber) {
      return new SegmentState(
          currentSequenceNumber,
          currentEntryCount,
          currentStreamSize,
          currentMinTimestamp,
          currentMaxTimestamp,
          currentSegmentCreatedAt,
          false);
    }

    throw new IOException("Segment with sequence number " + sequenceNumber + " not found");
  }
}
