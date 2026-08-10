package io.writeahead.log.segments.management;

import io.writeahead.log.config.WalConstants;
import io.writeahead.log.models.meta.SegmentFooter;
import io.writeahead.log.models.meta.SegmentHeader;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ManagementTestUtils {

  private ManagementTestUtils() {
    throw new AssertionError("Cannot instantiate utility class");
  }

  public static File createTempLogDirectory() throws IOException {
    Path tempDir = Files.createTempDirectory("wal-test-");
    return tempDir.toFile();
  }

  public static void deleteDirectory(File dir) throws IOException {
    if (!dir.exists()) {
      return;
    }

    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          deleteDirectory(file);
        } else {
          Files.deleteIfExists(file.toPath());
        }
      }
    }
    Files.deleteIfExists(dir.toPath());
  }

  public static File createValidSegmentFile(
      File directory,
      long sequence,
      long createdAt,
      int entryCount,
      long minTimestamp,
      long maxTimestamp)
      throws IOException {
    String filename = String.format("wal-%d-%06d.log", createdAt, sequence);
    File segmentFile = new File(directory, filename);

    SegmentHeader header = SegmentHeader.create(createdAt, sequence);
    byte[] headerBytes = header.toBytes();

    SegmentFooter footer = SegmentFooter.create(entryCount, minTimestamp, maxTimestamp);
    byte[] footerBytes = footer.toBytes();

    int totalSize = WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100;
    byte[] content = new byte[totalSize];

    System.arraycopy(headerBytes, 0, content, 0, WalConstants.SEGMENT_HEADER_SIZE);
    System.arraycopy(
        footerBytes,
        0,
        content,
        totalSize - WalConstants.SEGMENT_FOOTER_SIZE,
        WalConstants.SEGMENT_FOOTER_SIZE);

    Files.write(segmentFile.toPath(), content);
    return segmentFile;
  }

  public static File createSegmentFileWithInvalidMagic(
      File directory, long sequence, long createdAt) throws IOException {
    String filename = String.format("wal-%d-%06d.log", createdAt, sequence);
    File segmentFile = new File(directory, filename);

    SegmentHeader validHeader = SegmentHeader.create(createdAt, sequence);
    byte[] headerBytes = validHeader.toBytes();

    headerBytes[0] = (byte) 0xBB;

    SegmentFooter footer = SegmentFooter.create(1, 100L, 200L);
    byte[] footerBytes = footer.toBytes();

    int totalSize = WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100;
    byte[] content = new byte[totalSize];

    System.arraycopy(headerBytes, 0, content, 0, WalConstants.SEGMENT_HEADER_SIZE);
    System.arraycopy(
        footerBytes,
        0,
        content,
        totalSize - WalConstants.SEGMENT_FOOTER_SIZE,
        WalConstants.SEGMENT_FOOTER_SIZE);

    Files.write(segmentFile.toPath(), content);
    return segmentFile;
  }

  public static File createSegmentFileWithInvalidHeaderCrc(
      File directory, long sequence, long createdAt) throws IOException {
    String filename = String.format("wal-%d-%06d.log", createdAt, sequence);
    File segmentFile = new File(directory, filename);

    SegmentHeader validHeader = SegmentHeader.create(createdAt, sequence);
    byte[] headerBytes = validHeader.toBytes();

    headerBytes[38] = (byte) 0xFF;

    SegmentFooter footer = SegmentFooter.create(1, 100L, 200L);
    byte[] footerBytes = footer.toBytes();

    int totalSize = WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100;
    byte[] content = new byte[totalSize];

    System.arraycopy(headerBytes, 0, content, 0, WalConstants.SEGMENT_HEADER_SIZE);
    System.arraycopy(
        footerBytes,
        0,
        content,
        totalSize - WalConstants.SEGMENT_FOOTER_SIZE,
        WalConstants.SEGMENT_FOOTER_SIZE);

    Files.write(segmentFile.toPath(), content);
    return segmentFile;
  }

  public static File createSegmentFileTooSmall(File directory, long sequence, long createdAt)
      throws IOException {
    String filename = String.format("wal-%d-%06d.log", createdAt, sequence);
    File segmentFile = new File(directory, filename);

    byte[] content = new byte[40];
    Files.write(segmentFile.toPath(), content);
    return segmentFile;
  }

  public static File createSegmentFileWithInvalidFooterMarker(
      File directory, long sequence, long createdAt, int entryCount) throws IOException {
    String filename = String.format("wal-%d-%06d.log", createdAt, sequence);
    File segmentFile = new File(directory, filename);

    SegmentHeader header = SegmentHeader.create(createdAt, sequence);
    byte[] headerBytes = header.toBytes();

    SegmentFooter validFooter = SegmentFooter.create(entryCount, 100L, 200L);
    byte[] footerBytes = validFooter.toBytes();

    footerBytes[16] = (byte) 0xAA;

    int totalSize = WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100;
    byte[] content = new byte[totalSize];

    System.arraycopy(headerBytes, 0, content, 0, WalConstants.SEGMENT_HEADER_SIZE);
    System.arraycopy(
        footerBytes,
        0,
        content,
        totalSize - WalConstants.SEGMENT_FOOTER_SIZE,
        WalConstants.SEGMENT_FOOTER_SIZE);

    Files.write(segmentFile.toPath(), content);
    return segmentFile;
  }

  public static SegmentMetadata createExpectedMetadata(
      File segmentFile,
      long sequence,
      long createdAt,
      int entryCount,
      long minTimestamp,
      long maxTimestamp)
      throws IOException {
    long fileSize = FileUtils.getFileSize(segmentFile);
    return new SegmentMetadata(
        segmentFile.getName(),
        sequence,
        createdAt,
        fileSize,
        entryCount,
        minTimestamp,
        maxTimestamp);
  }

  public static void assertDirectoryExists(File directory) {
    if (!directory.exists() || !directory.isDirectory()) {
      throw new AssertionError("Directory does not exist: " + directory.getAbsolutePath());
    }
  }

  public static void assertFileExists(File file) {
    if (!file.exists() || !file.isFile()) {
      throw new AssertionError("File does not exist: " + file.getAbsolutePath());
    }
  }

  public static String generateSegmentFilename(long sequence) {
    long now = System.currentTimeMillis();
    return String.format("wal-%d-%06d.log", now, sequence);
  }
}
