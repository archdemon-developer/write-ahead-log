package io.writeahead.log.storage;

import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.models.segment.SegmentMetadata;
import io.writeahead.log.models.wal.WalConfiguration;
import io.writeahead.log.models.wal.WalMetadata;
import io.writeahead.log.utils.FileUtils;
import io.writeahead.log.utils.MetadataParserUtils;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class MetaDataStoreManager implements MetadataStore {

  private final String metaPath;
  private final WalConfiguration config;

  private static final Logger log = LoggerFactory.getLogger(MetaDataStoreManager.class);

  public MetaDataStoreManager(WalConfiguration config) {
    this.config = config;
    this.metaPath = config.logDir() + "/.meta";
  }

  @Override
  public WalMetadata read() throws IOException {
    Path metadataPath = Paths.get(metaPath);

    if (Files.exists(metadataPath)) {
      try {
        String content = new String(Files.readAllBytes(metadataPath));
        WalMetadata metadata = MetadataParserUtils.parseJson(content);
        log.debug("Metadata read successfully: {} segments", metadata.segments().size());
        return metadata;
      } catch (Exception e) {
        log.warn(
            "Failed to parse metdata file: {}. Attempting recovery from segments.", e.getMessage());
      }
    } else {
      log.warn("Metadata file not found: {}. Attempting recovery from segments.", metaPath);
    }
    return recoverMetadataFromSegments();
  }

  @Override
  public void write(WalMetadata metadata) throws IOException {
    String tempPath = metaPath + ".tmp";
    Path temporaryPath = Paths.get(tempPath);
    String metadataAsJson = MetadataParserUtils.toJson(metadata);

    Files.write(temporaryPath, metadataAsJson.getBytes());
    log.debug("Metadata written to temp file");
    Files.move(
        temporaryPath,
        Paths.get(metaPath),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
    log.info("Metadata persisted atomically: {} segments", metadata.segments().size());
  }

  private WalMetadata recoverMetadataFromSegments() throws IOException {
    List<File> logFiles = FileUtils.listLogFiles(config.logDir());

    if (logFiles.isEmpty()) {
      log.warn("No segment files found during recovery. Returning empty metadata.");
      return new WalMetadata(null, new ArrayList<>());
    }

    List<SegmentMetadata> recovered = new ArrayList<>();
    for (File logFile : logFiles) {
      long minTimestamp = readFirstEntryTimestamp(logFile);
      long maxTimestamp = readLastEntryTimestamp(logFile);
      SegmentMetadata generated =
          new SegmentMetadata(logFile.getName(), minTimestamp, maxTimestamp);
      recovered.add(generated);
      log.debug(
          "Recovered segment {} with timestamps [{}, {}]",
          logFile.getName(),
          minTimestamp,
          maxTimestamp);
    }

    String lastSegment = logFiles.getLast().getName();
    WalMetadata recoveredWal = new WalMetadata(lastSegment, recovered);

    write(recoveredWal);

    log.info("Metadata recovered successfully from {} segments and persisted", recovered.size());

    return recoveredWal;
  }

  private long readFirstEntryTimestamp(File logFile) throws IOException {
    try (DataInputStream dataInputStream = new DataInputStream(new FileInputStream(logFile))) {
      return dataInputStream.readLong();
    }
  }

  private long readLastEntryTimestamp(File logFile) throws IOException {
    try (RandomAccessFile randomAccessFile = new RandomAccessFile(logFile, "r")) {
      long fileSize = randomAccessFile.length();

      if (fileSize <= 0) {
        throw new IOException("File is empty");
      }

      int chunkSize = 64 * 1024;
      long readStart = Math.max(0, fileSize - chunkSize);

      int bytesToRead = (int) (fileSize - readStart);
      byte[] buffer = new byte[bytesToRead];

      randomAccessFile.seek(readStart);
      randomAccessFile.readFully(buffer);

      return MetadataParserUtils.parseLastEntryTimestamp(buffer);
    }
  }
}
