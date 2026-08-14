package io.writeahead.log.segments.orchestrators;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.config.WalConstants;
import io.writeahead.log.enums.exceptions.CorruptionType;
import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.metrics.WalMetricsRecorder;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.segments.filter.reads.AfterTimestampFilter;
import io.writeahead.log.segments.filter.reads.ReadFilter;
import io.writeahead.log.segments.operators.SegmentCollection;
import io.writeahead.log.segments.operators.SegmentEntriesReader;
import io.writeahead.log.utils.FileUtils;
import io.writeahead.log.utils.WalErrorClassifier;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SegmentReader {

  private static final Logger log = LoggerFactory.getLogger(SegmentReader.class);

  private final SegmentEntriesReader segmentReader;
  private final SegmentCollection segmentCollection;
  private final WalConfiguration config;
  private final WalMetricsRecorder metrics;

  public SegmentReader(
      SegmentEntriesReader segmentEntriesReader,
      SegmentCollection segmentCollection,
      WalConfiguration config,
      WalMetricsRecorder metrics) {
    this.segmentReader = segmentEntriesReader;
    this.segmentCollection = segmentCollection;
    this.config = config;
    this.metrics = metrics;
  }

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

  public List<LogEntry> readAllAfterTimestamp(long timestamp) throws IOException {
    return readAllMatching(new AfterTimestampFilter(timestamp));
  }

  public List<LogEntry> readCurrentOpenSegment(File segmentFile) throws IOException {
    if (!segmentFile.exists() || segmentFile.length() <= WalConstants.SEGMENT_HEADER_SIZE) {
      return List.of();
    }
    byte[] allBytes = FileUtils.readAllBytes(segmentFile);
    byte[] entryRegion = new byte[allBytes.length - WalConstants.SEGMENT_HEADER_SIZE];
    System.arraycopy(
        allBytes, WalConstants.SEGMENT_HEADER_SIZE, entryRegion, 0, entryRegion.length);
    SegmentEntriesReader.SegmentReadResult result =
        segmentReader.readEntriesFromRegion(entryRegion);
    return result.entries();
  }
}
