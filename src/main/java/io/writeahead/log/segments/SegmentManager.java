package io.writeahead.log.segments;

import io.writeahead.log.constants.WalConstants;
import io.writeahead.log.exceptions.CorruptedEntryException;
import io.writeahead.log.fileio.FileUtils;
import io.writeahead.log.meta.MetaDataManager;
import io.writeahead.log.models.FileStream;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.SegmentMetadata;
import io.writeahead.log.models.WalMetadata;
import io.writeahead.log.utils.Crc32Utils;
import java.io.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SegmentManager {

  private final String logDir;
  private final MetaDataManager metaDataManager;

  private File currentSegment;
  private FileStream currentStream;
  private long currentSegmentSize;
  private long currentSegmentMinTimestamp;
  private long currentSegmentMaxTimestamp;

  private static final long MAX_SEGMENT_SIZE = 10 * 1024 * 1024;

  public SegmentManager(String logDir) throws IOException {
    this.logDir = logDir;
    FileUtils.createDirectory(logDir);
    this.metaDataManager = new MetaDataManager(logDir);
    WalMetadata walMetadata = metaDataManager.read();

    if (walMetadata.lastActiveSegment() != null) {
      currentSegment = new File(logDir + "/" + walMetadata.lastActiveSegment());
      currentStream = FileUtils.openAppendStream(currentSegment);
      currentSegmentSize = FileUtils.getFileSize(currentSegment);
    } else {
      createNewSegment();
    }
  }

  public void writeBatch(List<LogEntry> batch) throws IOException {
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

    if (currentSegmentSize > MAX_SEGMENT_SIZE) {
      rotateSegment();
    }
  }

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
          System.err.println("Incomplete entry at end of " + logFile.getName());
          break;
        }
      }

      dataInputStream.close();
      byteArrayInputStream.close();
    }
    return allEntries;
  }

  public List<LogEntry> readAllAfterTimestamp(long timestamp) throws IOException {
    WalMetadata walMetadata = metaDataManager.read();

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
          System.err.println("Incomplete entry at end of " + segmentMetadata.filename());
          break;
        }
      }

      dataInputStream.close();
      byteArrayInputStream.close();
    }

    return entriesAfterTimestamp;
  }

  public void truncateBeforeTimestamp(long timestamp) throws IOException {
    WalMetadata walMetadata = metaDataManager.read();

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

    metaDataManager.write(new WalMetadata(segmentsToKeep.getLast().filename(), segmentsToKeep));

    for (SegmentMetadata segmentToDelete : segmentsToDelete) {
      boolean deleted =
          FileUtils.deleteFile(FileUtils.getLogFile(logDir, segmentToDelete.filename()));
      if (!deleted) {
        System.err.println("Warning: segment file didn't exist: " + segmentToDelete.filename());
      }
    }
  }

  public void close() throws IOException {
    WalMetadata walMetadata = metaDataManager.read();

    List<SegmentMetadata> segments = new ArrayList<>(walMetadata.segments());
    SegmentMetadata finalSegment = segments.getLast();

    SegmentMetadata newFinal =
        new SegmentMetadata(
            finalSegment.filename(), currentSegmentMinTimestamp, currentSegmentMaxTimestamp);
    segments.set(segments.size() - 1, newFinal);

    metaDataManager.write(new WalMetadata(walMetadata.lastActiveSegment(), segments));
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

    WalMetadata old = metaDataManager.read();
    List<SegmentMetadata> allSegments = new ArrayList<>(old.segments());
    allSegments.add(new SegmentMetadata(filename, 0, 0));

    metaDataManager.write(new WalMetadata(filename, allSegments));
  }

  private void rotateSegment() throws IOException {
    FileUtils.closeStream(currentStream);

    WalMetadata oldWal = metaDataManager.read();
    List<SegmentMetadata> allSegments = new ArrayList<>(oldWal.segments());

    SegmentMetadata updated =
        new SegmentMetadata(
            allSegments.getLast().filename(),
            currentSegmentMinTimestamp,
            currentSegmentMaxTimestamp);

    allSegments.set(allSegments.size() - 1, updated);

    metaDataManager.write(new WalMetadata(oldWal.lastActiveSegment(), allSegments));

    createNewSegment();
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
