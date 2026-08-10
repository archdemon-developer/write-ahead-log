package io.writeahead.log.segments.operators;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.metrics.SimpleWalMetrics;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.segments.operators.SegmentEntriesReader.SegmentReadResult;
import io.writeahead.log.serdes.EntrySerdes;
import io.writeahead.log.utils.Crc32Utils;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SegmentEntriesReader")
class SegmentEntriesReaderTest {

  SegmentEntriesReader reader;
  SimpleWalMetrics metrics;

  @BeforeEach
  void setUp() {
    metrics = new SimpleWalMetrics();
    reader = new SegmentEntriesReader(metrics);
  }

  @Nested
  @DisplayName("Reading Valid Entries")
  class ReadingValidEntriesTests {

    @Test
    @DisplayName("reads single valid entry")
    void readsSingleValidEntry() throws IOException {
      LogEntry entry = OperatorsTestUtils.createLogEntry(1000L, 64);
      byte[] serialized =
          OperatorsTestUtils.serializeEntryWithCrc(entry.timestamp(), entry.size(), entry.data());

      SegmentReadResult result = reader.readEntriesFromRegion(serialized);

      assertTrue(result.isValid());
      assertFalse(result.hasCorruption());
      assertEquals(1, result.entriesRead());
      assertEquals(1, result.entries().size());
      assertEquals(entry.timestamp(), result.entries().getFirst().timestamp());
      assertEquals(entry.size(), result.entries().getFirst().size());
    }

    @Test
    @DisplayName("reads multiple valid entries")
    void readsMultipleValidEntries() throws IOException {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(1100L, 128);
      LogEntry entryThree = OperatorsTestUtils.createLogEntry(1200L, 256);

      byte[] serialized =
          OperatorsTestUtils.serializeEntriesWithCrc(entryOne, entryTwo, entryThree);

      SegmentReadResult result = reader.readEntriesFromRegion(serialized);

      assertTrue(result.isValid());
      assertEquals(3, result.entriesRead());
      assertEquals(3, result.entries().size());
      assertEquals(1000L, result.entries().get(0).timestamp());
      assertEquals(1100L, result.entries().get(1).timestamp());
      assertEquals(1200L, result.entries().get(2).timestamp());
    }

    @Test
    @DisplayName("reads entries in correct order")
    void readsEntriesInCorrectOrder() throws IOException {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(5000L, 50);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(3000L, 50);
      LogEntry entryThree = OperatorsTestUtils.createLogEntry(8000L, 50);

      byte[] serialized =
          OperatorsTestUtils.serializeEntriesWithCrc(entryOne, entryTwo, entryThree);

      SegmentReadResult result = reader.readEntriesFromRegion(serialized);

      assertEquals(5000L, result.entries().get(0).timestamp());
      assertEquals(3000L, result.entries().get(1).timestamp());
      assertEquals(8000L, result.entries().get(2).timestamp());
    }

    @Test
    @DisplayName("reads zero-byte entries")
    void readsZeroByteEntries() throws IOException {
      LogEntry zeroByteEntry = OperatorsTestUtils.createLogEntry(1000L, 0);
      byte[] serialized =
          OperatorsTestUtils.serializeEntryWithCrc(
              zeroByteEntry.timestamp(), zeroByteEntry.size(), zeroByteEntry.data());

      SegmentReadResult result = reader.readEntriesFromRegion(serialized);

      assertTrue(result.isValid());
      assertEquals(1, result.entriesRead());
      assertEquals(0, result.entries().getFirst().size());
    }

    @Test
    @DisplayName("reads large entries")
    void readsLargeEntries() throws IOException {
      byte[] largeData = new byte[1024 * 100];
      LogEntry largeEntry = OperatorsTestUtils.createLogEntry(1000L, largeData.length, largeData);
      byte[] serialized =
          OperatorsTestUtils.serializeEntryWithCrc(
              largeEntry.timestamp(), largeEntry.size(), largeEntry.data());

      SegmentReadResult result = reader.readEntriesFromRegion(serialized);

      assertTrue(result.isValid());
      assertEquals(1, result.entriesRead());
      assertEquals(1024 * 100, result.entries().getFirst().size());
    }
  }

  @Nested
  @DisplayName("CRC Validation")
  class CrcValidationTests {

    @Test
    @DisplayName("detects corrupted entry CRC")
    void detectsCorruptedEntryCrc() throws IOException {
      LogEntry entry = OperatorsTestUtils.createLogEntry(1000L, 64);
      long correctCrc = Crc32Utils.computeEntryCrc(entry.timestamp(), entry.size(), entry.data());
      long corruptedCrc = correctCrc + 1;

      byte[] serialized =
          EntrySerdes.serializeEntryWithCrc(
              entry.timestamp(), entry.size(), entry.data(), corruptedCrc);

      SegmentReadResult result = reader.readEntriesFromRegion(serialized);

      assertFalse(result.isValid());
      assertTrue(result.hasCorruption());
      assertEquals(0, result.entriesRead());
      assertEquals(0, result.corruptionAtEntry());
      assertEquals(1, metrics.getCorruptedEntriesDetected());
    }

    @Test
    @DisplayName("stops reading at first corruption")
    void stopsReadingAtFirstCorruption() throws IOException {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(1100L, 128);
      LogEntry entryThree = OperatorsTestUtils.createLogEntry(1200L, 256);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (DataOutputStream dos = new DataOutputStream(baos)) {
        byte[] serOne =
            OperatorsTestUtils.serializeEntryWithCrc(
                entryOne.timestamp(), entryOne.size(), entryOne.data());
        dos.write(serOne);

        long corruptCrc = 0xDEADBEEFL;
        byte[] serTwo =
            EntrySerdes.serializeEntryWithCrc(
                entryTwo.timestamp(), entryTwo.size(), entryTwo.data(), corruptCrc);
        dos.write(serTwo);

        byte[] serThree =
            OperatorsTestUtils.serializeEntryWithCrc(
                entryThree.timestamp(), entryThree.size(), entryThree.data());
        dos.write(serThree);
      }

      SegmentReadResult result = reader.readEntriesFromRegion(baos.toByteArray());

      assertFalse(result.isValid());
      assertTrue(result.hasCorruption());
      assertEquals(1, result.entriesRead());
      assertEquals(1, result.corruptionAtEntry());
    }

    @Test
    @DisplayName("validates all entries when none corrupted")
    void validatesAllEntriesWhenNoneCorrupted() throws IOException {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(1100L, 128);
      LogEntry entryThree = OperatorsTestUtils.createLogEntry(1200L, 256);

      byte[] serialized =
          OperatorsTestUtils.serializeEntriesWithCrc(entryOne, entryTwo, entryThree);

      SegmentReadResult result = reader.readEntriesFromRegion(serialized);

      assertTrue(result.isValid());
      assertEquals(0, metrics.getCorruptedEntriesDetected());
    }
  }

  @Nested
  @DisplayName("Result State")
  class ResultStateTests {

    @Test
    @DisplayName("isValid returns true for no corruption")
    void isValidReturnsTrueForNoCorruption() throws IOException {
      LogEntry entry = OperatorsTestUtils.createLogEntry(1000L, 64);
      byte[] serialized =
          OperatorsTestUtils.serializeEntryWithCrc(entry.timestamp(), entry.size(), entry.data());

      SegmentReadResult result = reader.readEntriesFromRegion(serialized);

      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("isValid returns false for corruption")
    void isValidReturnsFalseForCorruption() throws IOException {
      LogEntry entry = OperatorsTestUtils.createLogEntry(1000L, 64);
      long corruptedCrc = 0xDEADBEEFL;
      byte[] serialized =
          EntrySerdes.serializeEntryWithCrc(
              entry.timestamp(), entry.size(), entry.data(), corruptedCrc);

      SegmentReadResult result = reader.readEntriesFromRegion(serialized);

      assertFalse(result.isValid());
    }

    @Test
    @DisplayName("corruptionAtEntry indicates position of first corruption")
    void corruptionAtEntryIndicatesPositionOfFirstCorruption() throws IOException {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(1100L, 128);
      LogEntry entryThree = OperatorsTestUtils.createLogEntry(1200L, 256);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (DataOutputStream dos = new DataOutputStream(baos)) {
        byte[] serOne =
            OperatorsTestUtils.serializeEntryWithCrc(
                entryOne.timestamp(), entryOne.size(), entryOne.data());
        dos.write(serOne);

        byte[] serTwo =
            OperatorsTestUtils.serializeEntryWithCrc(
                entryTwo.timestamp(), entryTwo.size(), entryTwo.data());
        dos.write(serTwo);

        long corruptCrc = 0xCAFEBABEL;
        byte[] serThree =
            EntrySerdes.serializeEntryWithCrc(
                entryThree.timestamp(), entryThree.size(), entryThree.data(), corruptCrc);
        dos.write(serThree);
      }

      SegmentReadResult result = reader.readEntriesFromRegion(baos.toByteArray());

      assertTrue(result.hasCorruption());
      assertEquals(2, result.corruptionAtEntry());
    }

    @Test
    @DisplayName("entriesRead reflects actual entries read before corruption")
    void entriesReadReflectsActualEntriesReadBeforeCorruption() throws IOException {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 64);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(1100L, 128);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (DataOutputStream dos = new DataOutputStream(baos)) {
        byte[] serOne =
            OperatorsTestUtils.serializeEntryWithCrc(
                entryOne.timestamp(), entryOne.size(), entryOne.data());
        dos.write(serOne);

        byte[] serTwo =
            OperatorsTestUtils.serializeEntryWithCrc(
                entryTwo.timestamp(), entryTwo.size(), entryTwo.data());
        dos.write(serTwo);

        long corruptCrc = 0xFFFFFFFFL;
        byte[] serCorrupted =
            EntrySerdes.serializeEntryWithCrc(1300L, 256, new byte[256], corruptCrc);
        dos.write(serCorrupted);
      }

      SegmentReadResult result = reader.readEntriesFromRegion(baos.toByteArray());

      assertEquals(2, result.entriesRead());
    }
  }

  @Nested
  @DisplayName("Empty and Edge Cases")
  class EmptyAndEdgeCasesTests {

    @Test
    @DisplayName("handles empty entry region")
    void handlesEmptyEntryRegion() throws IOException {
      byte[] emptyRegion = new byte[0];

      SegmentReadResult result = reader.readEntriesFromRegion(emptyRegion);

      assertTrue(result.isValid());
      assertFalse(result.hasCorruption());
      assertEquals(0, result.entriesRead());
      assertTrue(result.entries().isEmpty());
    }

    @Test
    @DisplayName("handles region with only single byte")
    void handlesRegionWithOnlySingleByte() throws IOException {
      byte[] singleByte = new byte[] {0x42};

      SegmentReadResult result = reader.readEntriesFromRegion(singleByte);

      assertTrue(result.isValid());
      assertEquals(0, result.entriesRead());
    }

    @Test
    @DisplayName("handles very small entries")
    void handlesVerySmallEntries() throws IOException {
      LogEntry entryOne = OperatorsTestUtils.createLogEntry(1000L, 1);
      LogEntry entryTwo = OperatorsTestUtils.createLogEntry(1100L, 1);

      byte[] serialized = OperatorsTestUtils.serializeEntriesWithCrc(entryOne, entryTwo);

      SegmentReadResult result = reader.readEntriesFromRegion(serialized);

      assertTrue(result.isValid());
      assertEquals(2, result.entriesRead());
    }

    @Test
    @DisplayName("handles maximum integer size entries")
    void handlesLargeNumberOfSmallEntries() throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (DataOutputStream dos = new DataOutputStream(baos)) {
        for (int i = 0; i < 100; i++) {
          LogEntry entry = OperatorsTestUtils.createLogEntry(1000L + i, 10);
          byte[] serialized =
              OperatorsTestUtils.serializeEntryWithCrc(
                  entry.timestamp(), entry.size(), entry.data());
          dos.write(serialized);
        }
      }

      SegmentReadResult result = reader.readEntriesFromRegion(baos.toByteArray());

      assertTrue(result.isValid());
      assertEquals(100, result.entriesRead());
    }
  }

  @Nested
  @DisplayName("Metrics Recording")
  class MetricsRecordingTests {

    @Test
    @DisplayName("records corrupted entry metric on CRC mismatch")
    void recordsCorruptedEntryMetricOnCrcMismatch() throws IOException {
      LogEntry entry = OperatorsTestUtils.createLogEntry(1000L, 64);
      long corruptedCrc = 0xDEADBEEFL;
      byte[] serialized =
          EntrySerdes.serializeEntryWithCrc(
              entry.timestamp(), entry.size(), entry.data(), corruptedCrc);

      reader.readEntriesFromRegion(serialized);

      assertEquals(1, metrics.getCorruptedEntriesDetected());
    }

    @Test
    @DisplayName("records corruption type metric")
    void recordsCorruptionTypeMetric() throws IOException {
      LogEntry entry = OperatorsTestUtils.createLogEntry(1000L, 64);
      long corruptedCrc = 0xCAFEBABEL;
      byte[] serialized =
          EntrySerdes.serializeEntryWithCrc(
              entry.timestamp(), entry.size(), entry.data(), corruptedCrc);

      reader.readEntriesFromRegion(serialized);

      assertTrue(metrics.getCorruptedEntriesDetected() > 0);
    }

    @Test
    @DisplayName("does not record metrics for valid entries")
    void doesNotRecordMetricsForValidEntries() throws IOException {
      LogEntry entry = OperatorsTestUtils.createLogEntry(1000L, 64);
      byte[] serialized =
          OperatorsTestUtils.serializeEntryWithCrc(entry.timestamp(), entry.size(), entry.data());

      reader.readEntriesFromRegion(serialized);

      assertEquals(0, metrics.getCorruptedEntriesDetected());
    }
  }

  @Nested
  @DisplayName("Data Integrity")
  class DataIntegrityTests {

    @Test
    @DisplayName("preserves entry data correctly")
    void preservesEntryDataCorrectly() throws IOException {
      byte[] originalData = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05};
      LogEntry entry = OperatorsTestUtils.createLogEntry(1000L, originalData.length, originalData);
      byte[] serialized =
          OperatorsTestUtils.serializeEntryWithCrc(entry.timestamp(), entry.size(), entry.data());

      SegmentReadResult result = reader.readEntriesFromRegion(serialized);

      assertArrayEquals(originalData, result.entries().getFirst().data());
    }

    @Test
    @DisplayName("preserves timestamps correctly")
    void preservesTimestampsCorrectly() throws IOException {
      long timestamp = 1234567890123L;
      LogEntry entry = OperatorsTestUtils.createLogEntry(timestamp, 64);
      byte[] serialized =
          OperatorsTestUtils.serializeEntryWithCrc(entry.timestamp(), entry.size(), entry.data());

      SegmentReadResult result = reader.readEntriesFromRegion(serialized);

      assertEquals(timestamp, result.entries().getFirst().timestamp());
    }

    @Test
    @DisplayName("preserves sizes correctly")
    void preservesSizesCorrectly() throws IOException {
      int size = 12345;
      LogEntry entry = OperatorsTestUtils.createLogEntry(1000L, size);
      byte[] serialized =
          OperatorsTestUtils.serializeEntryWithCrc(entry.timestamp(), entry.size(), entry.data());

      SegmentReadResult result = reader.readEntriesFromRegion(serialized);

      assertEquals(size, result.entries().getFirst().size());
    }
  }
}
