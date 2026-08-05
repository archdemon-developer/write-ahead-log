package io.writeahead.log.segments.management;

import io.writeahead.log.config.WalConstants;
import io.writeahead.log.enums.exceptions.CorruptionType;
import io.writeahead.log.enums.strategies.RecoveryType;
import io.writeahead.log.exceptions.CorruptionException;
import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.metrics.WalMetricsRecorder;
import io.writeahead.log.models.meta.SegmentFooter;
import io.writeahead.log.models.meta.SegmentHeader;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.models.meta.WalMetadata;
import io.writeahead.log.utils.FileUtils;
import io.writeahead.log.utils.WalErrorClassifier;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SegmentMetadataRecovery {

  private static final Logger log = LoggerFactory.getLogger(SegmentMetadataRecovery.class);
  private final String logDir;
  private final WalMetricsRecorder metrics;

  private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000;

  public SegmentMetadataRecovery(String logDir, WalMetricsRecorder metrics) {
    this.logDir = logDir;
    this.metrics = metrics;
  }

  public WalMetadata recover() throws IOException {
    long recoveryStartNs = System.nanoTime();
    List<SegmentMetadata> segments = new ArrayList<>();
    long maxSequenceFromHeaders = 0;

    File logDirectory = new File(logDir);

    if (!logDirectory.exists()) {
      long recoveryDurationMs = (System.nanoTime() - recoveryStartNs) / NANOSECONDS_PER_MILLISECOND;
      metrics.recordRecoveryCompleted(recoveryDurationMs, 0, 0);
      log.info("Log directory does not exist: {}", logDir);
      return new WalMetadata(null, segments, 1);
    }

    List<File> logFiles = FileUtils.listLogFiles(logDir);
    if (logFiles.isEmpty()) {
      long recoveryDurationMs = (System.nanoTime() - recoveryStartNs) / NANOSECONDS_PER_MILLISECOND;
      metrics.recordRecoveryCompleted(recoveryDurationMs, 0, 0);
      log.info("No segment found in: {}", logDir);
      return new WalMetadata(null, segments, 1);
    }

    for (File logFile : logFiles) {
      try {
        SegmentMetadata segMeta = validateAndRecoverSegment(logFile);
        segments.add(segMeta);
        maxSequenceFromHeaders = Math.max(maxSequenceFromHeaders, segMeta.sequenceNumber());
        log.debug(
            "Recovered segment: {} (seq: {}, entries: {})",
            logFile.getName(),
            segMeta.sequenceNumber(),
            segMeta.entryCount());
      } catch (CorruptionException ex) {
        metrics.recordSegmentCorruption();
        metrics.recordCorruptionType(ex.corruptionType());
        log.error(
            "Corruption in segment {}: {} (offset: {}, type: {})",
            logFile.getName(),
            ex.getMessage(),
            ex.byteOffset(),
            ex.corruptionType());
      } catch (IOException ex) {
        log.error("IO error recovering segment {}: {}", logFile.getName(), ex.getMessage());
      }
    }

    long recoveryDurationMs = (System.nanoTime() - recoveryStartNs) / NANOSECONDS_PER_MILLISECOND;
    metrics.recordRecoveryCompleted(recoveryDurationMs, logFiles.size(), segments.size());

    String lastActiveSegment = segments.isEmpty() ? null : segments.getLast().filename();
    long nextSequence = maxSequenceFromHeaders + 1;

    log.info(
        "Recovery complete: {} valid segments, max sequence: {}, next sequence: {}",
        segments.size(),
        maxSequenceFromHeaders,
        nextSequence);

    return new WalMetadata(lastActiveSegment, segments, nextSequence);
  }

  private SegmentMetadata validateAndRecoverSegment(File segmentFile) throws IOException {
    long fileSize = FileUtils.getFileSize(segmentFile);

    if (fileSize < 84) {
      throw WalErrorClassifier.classifyRecoveryError(
          RecoveryType.SEGMENT_TOO_SMALL,
          "Segment " + segmentFile.getName() + " is only " + fileSize + " bytes");
    }

    byte[] headerBytes = FileUtils.readBytes(segmentFile, 0, WalConstants.SEGMENT_HEADER_SIZE);
    SegmentHeader header = SegmentHeader.fromBytes(headerBytes);

    if (header.magic() != (byte) 0xAA) {
      throw WalErrorClassifier.classifyCorruption(
          segmentFile.getName(),
          0,
          CorruptionType.INVALID_MAGIC,
          header.magic(),
          0xAA,
          "Header magic byte mismatch");
    }

    if (!header.isValid()) {
      throw WalErrorClassifier.classifyCorruption(
          segmentFile.getName(),
          0,
          CorruptionType.HEADER_CRC_MISMATCH,
          header.computedChecksum(),
          header.checksum(),
          "Header CRC validation failed");
    }

    byte[] footerBytes =
        FileUtils.readBytes(
            segmentFile,
            fileSize - WalConstants.SEGMENT_FOOTER_SIZE,
            WalConstants.SEGMENT_FOOTER_SIZE);
    SegmentFooter footer = SegmentFooter.fromBytes(footerBytes);

    if (footer.completeMarker() != 0xDEADBEEFL) {
      throw WalErrorClassifier.classifyCorruption(
          segmentFile.getName(),
          fileSize - WalConstants.SEGMENT_FOOTER_SIZE,
          CorruptionType.INVALID_FOOTER_MARKER,
          footer.completeMarker(),
          0xDEADBEEFL,
          "Footer complete marker is not 0xDEADBEEF, got "
              + String.format("0x%08X", footer.completeMarker()));
    }

    if (!footer.isValid()) {
      throw WalErrorClassifier.classifyCorruption(
          segmentFile.getName(),
          0,
          CorruptionType.FOOTER_CRC_MISMATCH,
          footer.computedChecksum(),
          footer.checksum(),
          "Footer CRC validation failed");
    }

    if (footer.entryCount() == 0) {
      throw WalErrorClassifier.classifyRecoveryError(
          RecoveryType.INCOMPLETE_SEGMENT, "Segment has 0 entries: " + segmentFile.getName());
    }
    return new SegmentMetadata(
        segmentFile.getName(),
        header.segmentSequence(),
        header.createdAt(),
        fileSize,
        footer.entryCount(),
        footer.minTimestamp(),
        footer.maxTimestamp());
  }
}
