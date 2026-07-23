//package io.writeahead.log.storage;
//
//import io.writeahead.log.constants.WalConstants;
//
//import io.writeahead.log.fsync.FsyncExecutor;
//import io.writeahead.log.fsync.FsyncRetryStrategy;
//import io.writeahead.log.fsync.factory.FsyncExecutorFactory;
//import io.writeahead.log.fsync.factory.FsyncRetryStrategyFactory;
//import io.writeahead.log.logging.Logger;
//import io.writeahead.log.logging.LoggerFactory;
//import io.writeahead.log.metrics.SimpleWalMetrics;
//import io.writeahead.log.models.*;
//import io.writeahead.log.models.file.FileStream;
//import io.writeahead.log.models.segment.SegmentFooter;
//import io.writeahead.log.models.segment.SegmentHeader;
//import io.writeahead.log.models.segment.SegmentMetadata;
//import io.writeahead.log.models.wal.WalConfiguration;
//import io.writeahead.log.models.wal.WalMetadata;
//import io.writeahead.log.utils.Crc32Utils;
//import io.writeahead.log.utils.FileUtils;
//import java.io.*;
//import java.time.Instant;
//import java.time.ZoneId;
//import java.time.ZonedDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.List;
//
//public class SegmentStoreManager implements SegmentStore {
//
//  private final String logDir;
//
//  private final WalConfiguration config;
//
//  private File currentSegment;
//  private FileStream currentStream;
//  private long currentSegmentSize;
//  private long currentSegmentMinTimestamp;
//  private long currentSegmentMaxTimestamp;
//
//  private FsyncExecutor fsyncExecutor;
//  private final FsyncRetryStrategy fsyncRetryStrategy;
//
//  private final SimpleWalMetrics metrics = new SimpleWalMetrics();
//  private long nextSegmentSequence = 1;
//  private int currentSegmentEntryCount = 0;
//  private boolean firstEntryWritten = false;
//
//  private static final Logger log = LoggerFactory.getLogger(SegmentStoreManager.class);
//
//  public SegmentStoreManager(WalConfiguration configuration)
//      throws IOException {
//    this.config = configuration;
//    this.logDir = config.logDir();
//    FileUtils.createDirectory(logDir);
//    WalMetadata walMetadata = reconstructMetadataFromSegments();
//
//    createNewSegment();
//
//    metrics.setSegmentCount(walMetadata.segments().size());
//    this.fsyncRetryStrategy = FsyncRetryStrategyFactory.create(config, metrics);
//    this.fsyncExecutor =
//        FsyncExecutorFactory.create(config.fsyncStrategy(), fsyncRetryStrategy, currentStream);
//  }
//
//  private WalMetadata reconstructMetadataFromSegments() throws IOException {
//    List<File> segmentFiles = FileUtils.listLogFiles(logDir);
//
//    if(segmentFiles.isEmpty()) {
//      log.info("No existing segments found, will create new one");
//      this.nextSegmentSequence = 1;
//      return new WalMetadata(null, new ArrayList<>());
//    }
//
//    List<SegmentMetadata> reconstructedSegments = new ArrayList<>();
//    long maxSequenceFromHeaders = 0;
//
//    for(File segmentFile : segmentFiles) {
//      try {
//        byte[] headerBytes = FileUtils.readSegmentHeader(segmentFile);
//        SegmentHeader header = SegmentHeader.fromBytes(headerBytes);
//
//        if(!header.isValid()) {
//          log.warn("Segment header invalid: {}, skipping", segmentFile.getName());
//          continue;
//        }
//
//        byte[] footerBytes = FileUtils.readSegmentFooter(segmentFile);
//        SegmentFooter footer = SegmentFooter.fromBytes(footerBytes);
//        maxSequenceFromHeaders = Math.max(maxSequenceFromHeaders, header.segmentSequence());
//
//        if(!footer.isValid()) {
//          log.warn("Segment footer invalid: {}, skipping", segmentFile.getName());
//          continue;
//        }
//
//        if(!footer.isComplete()) {
//          log.warn("Segment incomplete (no complete marker): {}, skipping", segmentFile.getName());
//          continue;
//        }
//
//        SegmentMetadata segmentMetadata = new SegmentMetadata(
//                segmentFile.getName(),
//                footer.minTimestamp(),
//                footer.maxTimestamp()
//        );
//        reconstructedSegments.add(segmentMetadata);
//
//        log.debug("Recovered segment: {} with {} entries, timestamps [{}, {}]",
//                segmentFile.getName(),
//                footer.entryCount(),
//                footer.minTimestamp(),
//                footer.maxTimestamp());
//      } catch(IOException e) {
//        log.error("Error reading segment {}: {}, skipping due to: {}", e, segmentFile.getName(), e.getMessage());
//      }
//    }
//
//    this.nextSegmentSequence = maxSequenceFromHeaders + 1;
//
//    if(reconstructedSegments.isEmpty()) {
//      return new WalMetadata(null, new ArrayList<>());
//    }
//
//    String lastSegmentName = reconstructedSegments.getLast().filename();
//    return new WalMetadata(lastSegmentName, reconstructedSegments);
//  }
//
//  private SegmentFooter readSegmentFooter(File segmentFile) throws IOException {
//    byte[] segmentFooter = FileUtils.readSegmentFooter(segmentFile);
//    return SegmentFooter.fromBytes(segmentFooter);
//  }
//
//  @Override
//  public void writeBatch(List<LogEntry> batch) throws IOException {
//    log.debug(
//        "Writing batch: {} entries, {} bytes",
//        batch.size(),
//        batch.stream().mapToInt(LogEntry::size).sum());
//    for (LogEntry logEntry : batch) {
//      byte[] entryBytes = logEntryToBytes(logEntry);
//      FileUtils.writeToStream(currentStream, entryBytes);
//
//      if (!firstEntryWritten) {
//        currentSegmentMinTimestamp = logEntry.timestamp();
//        firstEntryWritten = true;
//      }
//
//
//      currentSegmentMaxTimestamp = logEntry.timestamp();
//      currentSegmentSize += entryBytes.length;
//      currentSegmentEntryCount++;
//
//      fsyncExecutor.onEntryWritten();
//      metrics.recordEntryWritten(logEntry.size());
//    }
//
//    fsyncExecutor.onBatchComplete();
//
//    if (currentSegmentSize > config.segmentSizeBytes()) {
//      rotateSegment();
//    }
//
//    log.debug("Batch written and fsynced");
//  }
//
//  @Override
//  public List<LogEntry> readAllSegments() throws IOException {
//    List<LogEntry> allEntries = new ArrayList<>();
//
//    for (File logFile : FileUtils.listLogFiles(logDir)) {
//      try {
//
//        byte[] headerBytes = FileUtils.readSegmentHeader(logFile);
//        SegmentHeader header = SegmentHeader.fromBytes(headerBytes);
//
//        if (!header.isValid()) {
//          log.warn("Segment header invalid: {}, skipping", logFile.getName());
//          continue;
//        }
//
//        byte[] footerBytes = FileUtils.readSegmentFooter(logFile);
//        SegmentFooter footer = SegmentFooter.fromBytes(footerBytes);
//
//        if (!footer.isValid()) {
//          log.warn("Segment footer invalid: {}, skipping", logFile.getName());
//          continue;
//        }
//
//        if (!footer.isComplete()) {
//          log.warn("Segment incomplete (no complete marker): {}, skipping", logFile.getName());
//          continue;
//        }
//
//        long fileSize = logFile.length();
//        int headerSize = WalConstants.SEGMENT_HEADER_SIZE;
//        int footerSize = WalConstants.SEGMENT_FOOTER_SIZE;
//        long entryRegionSize = fileSize - headerSize - footerSize;
//
//        if (entryRegionSize < 0) {
//          log.error("Segment {} too small: {} < {}",
//                  logFile.getName(), fileSize, headerSize + footerSize);
//          continue;
//        }
//
//        byte[] allBytes = FileUtils.readAllBytes(logFile);
//        byte[] entryRegionBytes = new byte[(int)entryRegionSize];
//        System.arraycopy(allBytes, headerSize, entryRegionBytes, 0, (int) entryRegionSize);
//
//        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(entryRegionBytes);
//        DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);
//
//        long entriesRead = 0;
//
//        try {
//          while (byteArrayInputStream.available() > 0) {
//            try {
//              long timestamp = dataInputStream.readLong();
//              int size = dataInputStream.readInt();
//              byte[] data = new byte[size];
//              dataInputStream.readFully(data);
//
//              ByteArrayOutputStream baos = new ByteArrayOutputStream();
//              DataOutputStream dos = new DataOutputStream(baos);
//              dos.writeLong(timestamp);
//              dos.writeInt(size);
//              dos.write(data);
//              byte[] entryBytes = baos.toByteArray();
//              long computedCrc = Crc32Utils.compute(entryBytes);
//              long storedCrc = dataInputStream.readLong();
//
//              if (computedCrc != storedCrc) {
//                metrics.recordCorruptedEntry();
//                log.error("Entry corruption in segment {} at entry {}: CRC mismatch",
//                        logFile.getName(), entriesRead);
//                break;
//              }
//
//              allEntries.add(new LogEntry(size, data, timestamp));
//              entriesRead++;
//
//            } catch (EOFException ex) {
//              log.debug("Reached end of entries in segment: {}", logFile.getName());
//              break;
//            }
//          }
//        } finally {
//          dataInputStream.close();
//          byteArrayInputStream.close();
//        }
//
//
//        if (entriesRead != footer.entryCount()) {
//          metrics.recordCorruptedEntry();
//          log.error("Entry count mismatch in segment {}: read {} but footer declares {}",
//                  logFile.getName(), entriesRead, footer.entryCount());
//
//          for (int i = 0; i < entriesRead; i++) {
//            allEntries.removeLast();
//          }
//          continue;
//        }
//
//        log.info("Recovered segment: {} with {} entries, timestamps [{}, {}]",
//                logFile.getName(), entriesRead, footer.minTimestamp(), footer.maxTimestamp());
//
//      } catch (IOException e) {
//        log.error("Error reading segment {}: {}, skipping", logFile.getName(), e.getMessage());
//      }
//    }
//
//    return allEntries;
//  }
//
//  @Override
//  public List<LogEntry> readAllAfterTimestamp(long timestamp) throws IOException {
//    List<LogEntry> entriesAfterTimestamp = new ArrayList<>();
//
//    for (File logFile : FileUtils.listLogFiles(logDir)) {
//      try {
//
//        byte[] headerBytes = FileUtils.readSegmentHeader(logFile);
//        SegmentHeader header = SegmentHeader.fromBytes(headerBytes);
//
//        if (!header.isValid()) {
//          log.warn("Segment header invalid: {}, skipping", logFile.getName());
//          continue;
//        }
//
//        byte[] footerBytes = FileUtils.readSegmentFooter(logFile);
//        SegmentFooter footer = SegmentFooter.fromBytes(footerBytes);
//
//        if (!footer.isValid()) {
//          log.warn("Segment footer invalid: {}, skipping", logFile.getName());
//          continue;
//        }
//
//        if (!footer.isComplete()) {
//          log.warn("Segment incomplete: {}, skipping", logFile.getName());
//          continue;
//        }
//
//        if (footer.maxTimestamp() <= timestamp) {
//          log.debug("Segment {} is before timestamp {}, skipping", logFile.getName(), timestamp);
//          continue;
//        }
//
//        long fileSize = logFile.length();
//        int headerSize = WalConstants.SEGMENT_HEADER_SIZE;
//        int footerSize = WalConstants.SEGMENT_FOOTER_SIZE;
//        long entryRegionSize = fileSize - headerSize - footerSize;
//
//        if (entryRegionSize < 0) {
//          log.error("Segment {} too small, skipping", logFile.getName());
//          continue;
//        }
//
//        byte[] allBytes = FileUtils.readAllBytes(logFile);
//        byte[] entryRegionBytes = new byte[(int)entryRegionSize];
//        System.arraycopy(allBytes, headerSize, entryRegionBytes, 0, (int)entryRegionSize);
//
//        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(entryRegionBytes);
//        DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);
//
//        long entriesRead = 0;
//        long entriesAfterFilter = 0;
//
//        try {
//          while (byteArrayInputStream.available() > 0) {
//            try {
//              long entryTimestamp = dataInputStream.readLong();
//              int size = dataInputStream.readInt();
//              byte[] data = new byte[size];
//              dataInputStream.readFully(data);
//
//
//              ByteArrayOutputStream baos = new ByteArrayOutputStream();
//              DataOutputStream dos = new DataOutputStream(baos);
//              dos.writeLong(entryTimestamp);
//              dos.writeInt(size);
//              dos.write(data);
//              byte[] entryBytes = baos.toByteArray();
//              long computedCrc = Crc32Utils.compute(entryBytes);
//
//              long storedCrc = dataInputStream.readLong();
//
//              if (computedCrc != storedCrc) {
//                metrics.recordCorruptedEntry();
//                log.error("Entry corruption in segment {} at entry {}",
//                        logFile.getName(), entriesRead);
//                break;
//              }
//
//              if (entryTimestamp > timestamp) {
//                entriesAfterTimestamp.add(new LogEntry(size, data, entryTimestamp));
//                entriesAfterFilter++;
//              }
//
//              entriesRead++;
//
//            } catch (EOFException ex) {
//              log.debug("Reached end of entries in segment: {}", logFile.getName());
//              break;
//            }
//          }
//        } finally {
//          dataInputStream.close();
//          byteArrayInputStream.close();
//        }
//
//        if (entriesRead != footer.entryCount()) {
//          metrics.recordCorruptedEntry();
//          log.error("Entry count mismatch in segment {}: read {} but footer says {}",
//                  logFile.getName(), entriesRead, footer.entryCount());
//          continue;
//        }
//
//        log.debug("Scanned segment: {} read {} entries, {} after timestamp {}",
//                logFile.getName(), entriesRead, entriesAfterFilter, timestamp);
//
//      } catch (IOException e) {
//        log.error("Error reading segment {}: {}, skipping", logFile.getName(), e.getMessage());
//      }
//    }
//
//    return entriesAfterTimestamp;
//  }
//
//  @Override
//  public void truncateBeforeTimestamp(long timestamp) throws IOException {
//    List<File> segmentFiles = FileUtils.listLogFiles(logDir);
//
//    if (segmentFiles.isEmpty()) {
//      return;
//    }
//
//    List<File> segmentsToDelete = new ArrayList<>();
//
//    for (int idx = 0; idx < segmentFiles.size() - 1; idx++) {
//      File segmentFile = segmentFiles.get(idx);
//
//      try {
//        byte[] headerBytes = FileUtils.readSegmentHeader(segmentFile);
//        SegmentHeader header = SegmentHeader.fromBytes(headerBytes);
//
//        if (!header.isValid()) {
//          log.warn("Segment header invalid: {}, skipping deletion check", segmentFile.getName());
//          continue;
//        }
//
//        byte[] footerBytes = FileUtils.readSegmentFooter(segmentFile);
//        SegmentFooter footer = SegmentFooter.fromBytes(footerBytes);
//
//        if (!footer.isValid()) {
//          log.warn("Segment footer invalid: {}, skipping deletion check", segmentFile.getName());
//          continue;
//        }
//
//        if (footer.maxTimestamp() <= timestamp) {
//          segmentsToDelete.add(segmentFile);
//          log.debug("Marking segment for deletion: {} (max timestamp: {})",
//                  segmentFile.getName(), footer.maxTimestamp());
//        }
//
//      } catch (IOException e) {
//        log.error("Error reading segment {}: {}, skipping deletion check",
//                segmentFile.getName(), e.getMessage());
//      }
//    }
//
//    if (segmentsToDelete.isEmpty()) {
//      log.debug("No segments to delete before timestamp {}", timestamp);
//      return;
//    }
//
//    for (File segmentFile : segmentsToDelete) {
//      try {
//        boolean deleted = FileUtils.deleteFile(segmentFile);
//        if (deleted) {
//          log.info("Deleted segment: {}", segmentFile.getName());
//        } else {
//          log.warn("Segment file not found: {}", segmentFile.getName());
//        }
//      } catch (IOException e) {
//        log.error("Failed to delete segment {}: {}", segmentFile.getName(), e.getMessage());
//      }
//    }
//
//    log.info("Truncated {} segments before timestamp {}", segmentsToDelete.size(), timestamp);
//  }
//
//  @Override
//  public void close() throws IOException {
//    try {
//      SegmentFooter footer = SegmentFooter.create(
//              currentSegmentEntryCount,
//              currentSegmentMinTimestamp,
//              currentSegmentMaxTimestamp);
//      FileUtils.writeToStream(currentStream, footer.toBytes());
//      FileUtils.fsyncStream(currentStream);
//    } finally{
//      FileUtils.closeStream(currentStream);
//    }
//
//    log.info("WriteAheadLog closed, final segment: {} with {} entries",
//            currentSegment.getName(), currentSegmentEntryCount);
//  }
//
//  private void createNewSegment() throws IOException {
//    String formattedTimestamp = formatTimestamp(Instant.now().toEpochMilli());
//    String filename = "wal-" + formattedTimestamp + "-"
//            +String.format("%03d", nextSegmentSequence) + ".log";
//
//    this.currentSegment = FileUtils.getLogFile(logDir, filename);
//
//    this.currentStream = FileUtils.openAppendStream(currentSegment);
//    SegmentHeader header = SegmentHeader.create(System.currentTimeMillis(), nextSegmentSequence);
//    FileUtils.writeToStream(currentStream, header.toBytes());
//
//    this.currentSegmentSize = header.toBytes().length;
//    this.currentSegmentMinTimestamp = 0;
//    this.currentSegmentMaxTimestamp = 0;
//    this.currentSegmentEntryCount = 0;
//    this.firstEntryWritten = false;
//
//    nextSegmentSequence++;
//
//    log.info("Created new segment: {} (sequence: {})", filename, nextSegmentSequence - 1);
//  }
//
//  private void rotateSegment() throws IOException {
//    log.info(
//        "Rotating segment: {} (size: {} MB)",
//        currentSegment.getName(),
//        currentSegmentSize / (1024 * 1024));
//
//    SegmentFooter footer = SegmentFooter.create(
//            currentSegmentEntryCount,
//            currentSegmentMinTimestamp,
//            currentSegmentMaxTimestamp);
//    FileUtils.writeToStream(currentStream, footer.toBytes());
//    FileUtils.fsyncStream(currentStream);
//
//    FileUtils.closeStream(currentStream);
//
//    createNewSegment();
//
//    metrics.recordSegmentRotation();
//    metrics.setSegmentCount(nextSegmentSequence - 1);
//    this.fsyncExecutor =
//        FsyncExecutorFactory.create(config.fsyncStrategy(), fsyncRetryStrategy, currentStream);
//
//    log.info("New segment created: {}", currentSegment.getName());
//  }
//
//  private String formatTimestamp(long timestampMs) {
//    Instant timestampInstant = Instant.ofEpochMilli(timestampMs);
//    ZonedDateTime timestamp = ZonedDateTime.ofInstant(timestampInstant, ZoneId.systemDefault());
//    DateTimeFormatter timestampFormatter =
//        DateTimeFormatter.ofPattern(WalConstants.LOG_FILE_DATE_FORMAT);
//    return timestampFormatter.format(timestamp);
//  }
//
//  private byte[] logEntryToBytes(LogEntry entry) throws IOException {
//    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//    DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
//
//    dataOutputStream.writeLong(entry.timestamp());
//    dataOutputStream.writeInt(entry.size());
//    dataOutputStream.write(entry.data());
//
//    byte[] result = byteArrayOutputStream.toByteArray();
//
//    long crc32 = Crc32Utils.compute(result);
//
//    dataOutputStream.writeLong(crc32);
//
//    dataOutputStream.flush();
//
//    byte[] resultWithCrc = byteArrayOutputStream.toByteArray();
//
//    dataOutputStream.close();
//    byteArrayOutputStream.close();
//
//    return resultWithCrc;
//  }
//
//  public SimpleWalMetrics getMetrics() {
//      return metrics;
// }
//}
