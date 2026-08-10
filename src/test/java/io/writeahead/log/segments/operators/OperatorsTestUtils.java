package io.writeahead.log.segments.operators;

import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.serdes.EntrySerdes;
import io.writeahead.log.utils.Crc32Utils;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

public class OperatorsTestUtils {

  private static final Random random = new Random(42L);

  private OperatorsTestUtils() {}

  public static LogEntry createLogEntry(long timestamp, int size) {
    byte[] data = new byte[size];
    random.nextBytes(data);
    return new LogEntry(size, data, timestamp);
  }

  public static LogEntry createLogEntry(long timestamp, int size, byte[] data) {
    return new LogEntry(size, data, timestamp);
  }

  public static byte[] serializeEntryWithCrc(long timestamp, int size, byte[] data)
      throws IOException {
    long crc = Crc32Utils.computeEntryCrc(timestamp, size, data);
    return EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
  }

  public static byte[] serializeEntriesWithCrc(LogEntry... entries) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (DataOutputStream dos = new DataOutputStream(baos)) {
      for (LogEntry entry : entries) {
        byte[] serialized = serializeEntryWithCrc(entry.timestamp(), entry.size(), entry.data());
        dos.write(serialized);
      }
    }
    return baos.toByteArray();
  }

  public static byte[] serializeEntriesWithCorruptedCrc(LogEntry entry, long corruptedCrc)
      throws IOException {
    return EntrySerdes.serializeEntryWithCrc(
        entry.timestamp(), entry.size(), entry.data(), corruptedCrc);
  }

  public static SegmentMetadata createSegmentMetadata(
      long sequence, long createdAt, long minTs, long maxTs, long entryCount, long fileSize) {
    return new SegmentMetadata(
        String.format("wal-%d-%06d.log", createdAt, sequence),
        sequence,
        createdAt,
        fileSize,
        entryCount,
        minTs,
        maxTs);
  }

  public static SegmentMetadata createSegmentMetadata(long sequence, long createdAt) {
    return createSegmentMetadata(sequence, createdAt, 1000L, 2000L, 10L, 500L);
  }
}
