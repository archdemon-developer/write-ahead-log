package io.writeahead.log.integration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public class IntegrationTestFixtures {

  private static final Random random = new Random();

  public static byte[] smallPayload() {
    byte[] payload = new byte[50];
    fillPayload(payload);
    return payload;
  }

  public static byte[] mediumPayload() {
    byte[] payload = new byte[500];
    fillPayload(payload);
    return payload;
  }

  public static byte[] largePayload() {
    byte[] payload = new byte[5000];
    fillPayload(payload);
    return payload;
  }

  public static byte[] massivePayload() {
    byte[] payload = new byte[50 * 1024];
    fillPayload(payload);
    return payload;
  }

  public static byte[] variablePayload(int size) {
    byte[] payload = new byte[size];
    fillPayload(payload);
    return payload;
  }

  public static long[] uniformTimestamps(int count, long baseTimestamp) {
    long[] timestamps = new long[count];
    for (int i = 0; i < count; i++) {
      timestamps[i] = baseTimestamp + (i * 1_000_000);
    }
    return timestamps;
  }

  public static long[] skewedTimestamps(int count, long baseTimestamp) {
    long[] timestamps = new long[count];
    for (int i = 0; i < count; i++) {
      if (i % 10 == 0) {

        timestamps[i] = baseTimestamp + (long) (Math.random() * 10_000_000_000L);
      } else {

        timestamps[i] = baseTimestamp + (long) (Math.random() * 100_000);
      }
    }
    return timestamps;
  }

  public static long[] reverseTimestamps(int count, long baseTimestamp) {
    long[] timestamps = new long[count];
    for (int i = 0; i < count; i++) {
      timestamps[i] = baseTimestamp - (i * 1_000_000);
    }
    return timestamps;
  }

  public static long[] randomTimestamps(int count, long baseTimestamp) {
    long[] timestamps = new long[count];
    for (int i = 0; i < count; i++) {
      timestamps[i] = baseTimestamp + random.nextLong(1_000_000_000L);
    }
    return timestamps;
  }

  public static void corruptSegmentFile(Path segmentFile, int byteOffset, byte newValue)
      throws IOException {
    if (!Files.exists(segmentFile)) {
      throw new IOException("Segment file does not exist: " + segmentFile);
    }

    byte[] data = Files.readAllBytes(segmentFile);

    if (byteOffset < 0 || byteOffset >= data.length) {
      throw new IOException(
          "Byte offset " + byteOffset + " out of range for file size " + data.length);
    }

    data[byteOffset] = newValue;
    Files.write(segmentFile, data);
  }

  public static void corruptSegmentHeader(Path segmentFile) throws IOException {
    corruptSegmentFile(segmentFile, 0, (byte) 0xFF);
  }

  public static void corruptSegmentFooter(Path segmentFile) throws IOException {
    byte[] data = Files.readAllBytes(segmentFile);
    int footerStart = data.length - 36;
    int completeMarkerOffset = footerStart + 24;

    corruptSegmentFile(segmentFile, completeMarkerOffset, (byte) 0xFF);
  }

  public static File findFirstSegmentFile(String logDir) {
    File directory = new File(logDir);
    File[] files =
        directory.listFiles((d, name) -> name.startsWith("wal-") && name.endsWith(".log"));

    if (files == null || files.length == 0) {
      return null;
    }

    return files[0];
  }

  public static File[] findAllSegmentFiles(String logDir) {
    File directory = new File(logDir);
    return directory.listFiles((d, name) -> name.startsWith("wal-") && name.endsWith(".log"));
  }

  public static int getSegmentCount(String logDir) {
    File[] files = findAllSegmentFiles(logDir);
    return files == null ? 0 : files.length;
  }

  public static long getTotalSegmentSize(String logDir) {
    File[] files = findAllSegmentFiles(logDir);
    if (files == null) return 0;

    long totalSize = 0;
    for (File file : files) {
      totalSize += file.length();
    }
    return totalSize;
  }

  public static void deleteAllSegments(String logDir) throws IOException {
    File[] files = findAllSegmentFiles(logDir);
    if (files != null) {
      for (File file : files) {
        if (!file.delete()) {
          throw new IOException("Failed to delete: " + file.getAbsolutePath());
        }
      }
    }
  }

  private static void fillPayload(byte[] payload) {
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) (i % 256);
    }
  }

  public static String generateUniqueFilename(String prefix) {
    return prefix + "_" + System.nanoTime() + ".dat";
  }

  public static void assertSegmentExists(String logDir, String filename) throws IOException {
    File segmentFile = new File(logDir, filename);
    if (!segmentFile.exists()) {
      throw new AssertionError("Segment file does not exist: " + segmentFile.getAbsolutePath());
    }
  }

  public static void assertSegmentNotExists(String logDir, String filename) throws IOException {
    File segmentFile = new File(logDir, filename);
    if (segmentFile.exists()) {
      throw new AssertionError("Segment file should not exist: " + segmentFile.getAbsolutePath());
    }
  }
}
