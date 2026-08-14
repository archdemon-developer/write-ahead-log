package io.writeahead.log.segments.management;

import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.models.FileStream;
import io.writeahead.log.models.meta.SegmentFooter;
import io.writeahead.log.models.meta.SegmentHeader;
import io.writeahead.log.models.states.SegmentFinalizationData;
import io.writeahead.log.utils.FileUtils;
import io.writeahead.log.utils.WalErrorClassifier;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class SegmentLifecycleManager {

  private static final Logger log = LoggerFactory.getLogger(SegmentLifecycleManager.class);
  private final String logDir;

  public SegmentLifecycleManager(String logDir) throws IOException {
    File dir = new File(logDir);
    if (!dir.exists() || !dir.isDirectory()) {
      throw new IOException("Log directory does not exist or is not a directory: " + logDir);
    }
    this.logDir = logDir;
  }

  public FileStream createNewSegment(long sequence) throws IOException {
    String filename = generateSegmentFilename(sequence);
    File segmentFile = new File(logDir, filename);

    try {
      SegmentHeader header = SegmentHeader.create(System.currentTimeMillis(), sequence);
      byte[] headerBytes = header.toBytes();
      Files.write(segmentFile.toPath(), headerBytes);

      log.info("Created new segment: {} (sequence: {})", filename, sequence);

      return FileUtils.openAppendStream(segmentFile);
    } catch (IOException e) {
      deleteFile(segmentFile, filename);
      throw WalErrorClassifier.classifyIOException(e, "create new segment file");
    }
  }

  public void finalizeSegment(FileStream currentStream, SegmentFinalizationData finalizationData)
      throws IOException {
    try {
      SegmentFooter footer =
          SegmentFooter.create(
              finalizationData.entryCount(),
              finalizationData.minTimestamp(),
              finalizationData.maxTimestamp());
      byte[] footerBytes = footer.toBytes();
      try {
        FileUtils.writeToStream(currentStream, footerBytes);
      } catch (IOException ex) {
        throw WalErrorClassifier.classifyIOException(ex, "write footer to segment");
      }
      try {
        FileUtils.fsyncStream(currentStream);
      } catch (IOException ex) {
        throw WalErrorClassifier.classifyIOException(ex, "fsync footer to disk");
      }
    } finally {
      FileUtils.closeStream(currentStream);
    }

    log.debug("Finalized segment: {}", finalizationData);
  }

  public static String generateSegmentFilename(long sequence) {
    return String.format("wal-%06d.log", sequence);
  }

  private static void deleteFile(File segmentFile, String filename) {
    try {
      FileUtils.deleteFile(segmentFile);
      log.warn("Cleaned up incomplete segment file: {}", filename);
    } catch (IOException deleteError) {
      log.warn("Failed to cleanup segment file on create failure: {}", filename);
    }
  }
}
