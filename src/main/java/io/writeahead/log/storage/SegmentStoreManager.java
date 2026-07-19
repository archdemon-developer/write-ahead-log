package io.writeahead.log.storage;

import io.writeahead.log.constants.WalConstants;
import io.writeahead.log.exceptions.CorruptedEntryException;
import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.models.*;
import io.writeahead.log.utils.Crc32Utils;
import io.writeahead.log.utils.FileUtils;
import java.io.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SegmentStoreManager implements SegmentStore {

  private final String logDir;
  private final MetadataStore metadataStore;

  private final WalConfiguration config;

  private File currentSegment;
  private FileStream currentStream;
  private long currentSegmentSize;
  private long currentSegmentMinTimestamp;
  private long currentSegmentMaxTimestamp;

  private static final Logger log = LoggerFactory.getLogger(SegmentStoreManager.class);

  public SegmentStoreManager(WalConfiguration configuration, MetadataStore metadataStore)
      throws IOException {
    this.config = configuration;
    this.logDir = config.logDir();
    FileUtils.createDirectory(logDir);
    this.metadataStore = metadataStore;
    WalMetadata walMetadata = metadataStore.read();

    if (walMetadata.lastActiveSegment() != null) {
      currentSegment = new File(logDir + "/" + walMetadata.lastActiveSegment());
      currentStream = FileUtils.openAppendStream(currentSegment);
      currentSegmentSize = FileUtils.getFileSize(currentSegment);
      log.info("Opened existing segment: {}", currentSegment.getName());
    } else {
      createNewSegment();
    }
  }

  @Override
  public void writeBatch(List<LogEntry> batch) throws IOException {
    log.debug(
        "Writing batch: {} entries, {} bytes",
        batch.size(),
        batch.stream().mapToInt(LogEntry::size).sum());
    for (LogEntry logEntry : batch) {
      byte[] entryBytes = logEntryToBytes(logEntry);
      FileUtils.writeToStream(currentStream, entryBytes);

      if (currentSegmentSize == 0) {
        currentSegmentMinTimestamp = logEntry.timestamp();
      }

      currentSegmentMaxTimestamp = logEntry.timestamp();
      currentSegmentSize += entryBytes.length;
    }

    FileUtils.fsyncStream(currentStream);

    if (currentSegmentSize > config.segmentSizeBytes()) {
      rotateSegment();
    }

    log.debug("Batch written and fsynced");
  }

  @Override
  public List<LogEntry> readAllSegments() throws IOException {
    List<LogEntry> allEntries = new ArrayList<>();

    for (File logFile : FileUtils.listLogFiles(logDir)) {
      byte[] allBytes = FileUtils.readAllBytes(logFile);

      ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(allBytes);
      DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);

      long entriesRead = 0;

      while (byteArrayInputStream.available() > 0) {
        try {
          long timestamp = dataInputStream.readLong();
          int size = dataInputStream.readInt();
          byte[] data = new byte[size];
          dataInputStream.readFully(data);

          long computedCrc = Crc32Utils.compute(timestamp, size, data);
          long storedCrc = dataInputStream.readLong();

          if (computedCrc != storedCrc) {
            throw new CorruptedEntryException(
                logFile.getName(),
                allBytes.length - byteArrayInputStream.available(),
                computedCrc,
                storedCrc,
                entriesRead);
          }

          allEntries.add(new LogEntry(size, data, timestamp));
          entriesRead++;
        } catch (EOFException ex) {
          log.debug("Incomplete entry at end of file: {}", logFile.getName());
          break;
        }
      }

      dataInputStream.close();
      byteArrayInputStream.close();
    }
    return allEntries;
  }

  @Override
  public List<LogEntry> readAllAfterTimestamp(long timestamp) throws IOException {
    WalMetadata walMetadata = metadataStore.read();

    List<SegmentMetadata> allSegments = walMetadata.segments();
    List<SegmentMetadata> segmentsAfterTimestamp = new ArrayList<>();

    for (SegmentMetadata segmentMetadata : allSegments) {
      if (segmentMetadata.maxTimestamp() > timestamp) {
        segmentsAfterTimestamp.add(segmentMetadata);
      }
    }

    List<LogEntry> entriesAfterTimestamp = new ArrayList<>();

    for (SegmentMetadata segmentMetadata : segmentsAfterTimestamp) {
      byte[] allBytes =
          FileUtils.readAllBytes(FileUtils.getLogFile(logDir, segmentMetadata.filename()));

      ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(allBytes);
      DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);

      int entriesRead = 0;

      while (byteArrayInputStream.available() > 0) {
        try {
          long entryTimestamp = dataInputStream.readLong();
          int size = dataInputStream.readInt();
          byte[] data = new byte[size];
          dataInputStream.readFully(data);

          long computedCrc = Crc32Utils.compute(entryTimestamp, size, data);
          long storedCrc = dataInputStream.readLong();

          if (computedCrc != storedCrc) {
            throw new CorruptedEntryException(
                segmentMetadata.filename(),
                allBytes.length - byteArrayInputStream.available(),
                computedCrc,
                storedCrc,
                entriesRead);
          }

          if (entryTimestamp > timestamp) {
            entriesAfterTimestamp.add(new LogEntry(size, data, entryTimestamp));
          }
          entriesRead++;
        } catch (EOFException ex) {
          log.debug("Incomplete entry at end of segment: {}", segmentMetadata.filename());
          break;
        }
      }

      dataInputStream.close();
      byteArrayInputStream.close();
    }

    return entriesAfterTimestamp;
  }

  @Override
  public void truncateBeforeTimestamp(long timestamp) throws IOException {
    WalMetadata walMetadata = metadataStore.read();

    List<SegmentMetadata> allSegments = walMetadata.segments();
    List<SegmentMetadata> segmentsToDelete = new ArrayList<>();

    for (int idx = 0; idx < allSegments.size() - 1; idx++) {
      SegmentMetadata segmentMetadata = allSegments.get(idx);
      if (segmentMetadata.maxTimestamp() <= timestamp) {
        segmentsToDelete.add(segmentMetadata);
      }
    }

    if (segmentsToDelete.isEmpty()) {
      return;
    }

    List<SegmentMetadata> segmentsToKeep = new ArrayList<>(allSegments);
    segmentsToKeep.removeAll(segmentsToDelete);

    metadataStore.write(new WalMetadata(segmentsToKeep.getLast().filename(), segmentsToKeep));

    for (SegmentMetadata segmentToDelete : segmentsToDelete) {
      boolean deleted =
          FileUtils.deleteFile(FileUtils.getLogFile(logDir, segmentToDelete.filename()));
      if (!deleted) {
        log.warn("Segment file didn't exist: {}", segmentToDelete.filename());
      }
    }
  }

  @Override
  public void close() throws IOException {
    WalMetadata walMetadata = metadataStore.read();

    List<SegmentMetadata> segments = new ArrayList<>(walMetadata.segments());
    SegmentMetadata finalSegment = segments.getLast();

    SegmentMetadata newFinal =
        new SegmentMetadata(
            finalSegment.filename(), currentSegmentMinTimestamp, currentSegmentMaxTimestamp);
    segments.set(segments.size() - 1, newFinal);

    metadataStore.write(new WalMetadata(walMetadata.lastActiveSegment(), segments));
    FileUtils.closeStream(currentStream);
  }

  private void createNewSegment() throws IOException {
    String formattedTimestamp = formatTimestamp(Instant.now().toEpochMilli());
    String filename = "wal-" + formattedTimestamp + "-001.log";

    this.currentSegment = FileUtils.getLogFile(logDir, filename);
    this.currentStream = FileUtils.openAppendStream(currentSegment);

    this.currentSegmentSize = 0;
    this.currentSegmentMinTimestamp = 0;
    this.currentSegmentMaxTimestamp = 0;

    WalMetadata old = metadataStore.read();
    List<SegmentMetadata> allSegments = new ArrayList<>(old.segments());
    allSegments.add(new SegmentMetadata(filename, 0, 0));

    metadataStore.write(new WalMetadata(filename, allSegments));
  }

  private void rotateSegment() throws IOException {
    log.info(
        "Rotating segment: {} (size: {} MB)",
        currentSegment.getName(),
        currentSegmentSize / (1024 * 1024));
    FileUtils.closeStream(currentStream);

    WalMetadata oldWal = metadataStore.read();
    List<SegmentMetadata> allSegments = new ArrayList<>(oldWal.segments());

    SegmentMetadata updated =
        new SegmentMetadata(
            allSegments.getLast().filename(),
            currentSegmentMinTimestamp,
            currentSegmentMaxTimestamp);

    allSegments.set(allSegments.size() - 1, updated);

    metadataStore.write(new WalMetadata(oldWal.lastActiveSegment(), allSegments));

    createNewSegment();
    log.info("New segment created: {}", currentSegment.getName());
  }

  private String formatTimestamp(long timestampMs) {
    Instant timestampInstant = Instant.ofEpochMilli(timestampMs);
    ZonedDateTime timestamp = ZonedDateTime.ofInstant(timestampInstant, ZoneId.systemDefault());
    DateTimeFormatter timestampFormatter =
        DateTimeFormatter.ofPattern(WalConstants.LOG_FILE_DATE_FORMAT);
    return timestampFormatter.format(timestamp);
  }

  private byte[] logEntryToBytes(LogEntry entry) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

    dataOutputStream.writeLong(entry.timestamp());
    dataOutputStream.writeInt(entry.size());
    dataOutputStream.write(entry.data());

    byte[] result = byteArrayOutputStream.toByteArray();

    long crc32 = Crc32Utils.compute(result);

    dataOutputStream.writeLong(crc32);

    dataOutputStream.flush();

    byte[] resultWithCrc = byteArrayOutputStream.toByteArray();

    dataOutputStream.close();
    byteArrayOutputStream.close();

    return resultWithCrc;
  }
}
