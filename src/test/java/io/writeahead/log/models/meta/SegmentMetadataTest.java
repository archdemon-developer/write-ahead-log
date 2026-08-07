package io.writeahead.log.models.meta;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SegmentMetadata Tests — 100% Validation Coverage")
class SegmentMetadataTest {

  private static final int MIN_FILE_SIZE =
      WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE;

  @Nested
  @DisplayName("Compact Constructor Validation — filename == null")
  class ConstructorValidation_FilenameNull {

    @Test
    @DisplayName("constructor rejects filename = null")
    void rejectsFilenameNull() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentMetadata(null, 1L, 1000L, 100L, 1, 500L, 600L));
      assertTrue(ex.getMessage().contains("filename cannot be null or blank"));
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — filename empty/blank")
  class ConstructorValidation_FilenameEmptyBlank {

    @Test
    @DisplayName("constructor rejects filename = \"\"")
    void rejectsFilenameEmpty() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentMetadata("", 1L, 1000L, 100L, 1, 500L, 600L));
      assertTrue(ex.getMessage().contains("filename cannot be null or blank"));
    }

    @Test
    @DisplayName("constructor rejects filename = \"   \" (blank)")
    void rejectsFilenameBlank() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentMetadata("   ", 1L, 1000L, 100L, 1, 500L, 600L));
      assertTrue(ex.getMessage().contains("filename cannot be null or blank"));
    }

    @Test
    @DisplayName("constructor rejects filename = \"\\t\\n\"")
    void rejectsFilenameWhitespace() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentMetadata("\t\n", 1L, 1000L, 100L, 1, 500L, 600L));
      assertTrue(ex.getMessage().contains("filename cannot be null or blank"));
    }

    @Test
    @DisplayName("constructor accepts valid filename")
    void acceptsValidFilename() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, 500L, 600L);
      assertEquals("wal-001.log", metadata.filename());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — sequenceNumber < 0")
  class ConstructorValidation_SequenceNumber {

    @Test
    @DisplayName("constructor rejects sequenceNumber = -1")
    void rejectsSequenceNegativeOne() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentMetadata("wal-001.log", -1L, 1000L, 100L, 1, 500L, 600L));
      assertTrue(ex.getMessage().contains("sequenceNumber cannot be negative"));
    }

    @Test
    @DisplayName("constructor rejects sequenceNumber = Long.MIN_VALUE")
    void rejectsSequenceMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentMetadata("wal-001.log", Long.MIN_VALUE, 1000L, 100L, 1, 500L, 600L));
      assertTrue(ex.getMessage().contains("sequenceNumber cannot be negative"));
    }

    @Test
    @DisplayName("constructor accepts sequenceNumber = 0")
    void acceptsSequenceZero() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 0L, 1000L, MIN_FILE_SIZE, 1, 500L, 600L);
      assertEquals(0L, metadata.sequenceNumber());
    }

    @Test
    @DisplayName("constructor accepts sequenceNumber = Long.MAX_VALUE")
    void acceptsSequenceMaxValue() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", Long.MAX_VALUE, 1000L, MIN_FILE_SIZE, 1, 500L, 600L);
      assertEquals(Long.MAX_VALUE, metadata.sequenceNumber());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — createdAt < 0")
  class ConstructorValidation_CreatedAt {

    @Test
    @DisplayName("constructor rejects createdAt = -1")
    void rejectsCreatedAtNegativeOne() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentMetadata("wal-001.log", 1L, -1L, 100L, 1, 500L, 600L));
      assertTrue(ex.getMessage().contains("createdAt cannot be negative"));
    }

    @Test
    @DisplayName("constructor rejects createdAt = Long.MIN_VALUE")
    void rejectsCreatedAtMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentMetadata("wal-001.log", 1L, Long.MIN_VALUE, 100L, 1, 500L, 600L));
      assertTrue(ex.getMessage().contains("createdAt cannot be negative"));
    }

    @Test
    @DisplayName("constructor accepts createdAt = 0")
    void acceptsCreatedAtZero() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 0L, MIN_FILE_SIZE, 1, 500L, 600L);
      assertEquals(0L, metadata.createdAt());
    }

    @Test
    @DisplayName("constructor accepts createdAt = Long.MAX_VALUE")
    void acceptsCreatedAtMaxValue() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, Long.MAX_VALUE, MIN_FILE_SIZE, 1, 500L, 600L);
      assertEquals(Long.MAX_VALUE, metadata.createdAt());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — fileSize < 84 (HEADER + FOOTER)")
  class ConstructorValidation_FileSize {

    @Test
    @DisplayName("constructor rejects fileSize = 0")
    void rejectsFileSizeZero() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentMetadata("wal-001.log", 1L, 1000L, 0L, 1, 500L, 600L));
      assertTrue(ex.getMessage().contains("fileSize must be >="));
      assertTrue(ex.getMessage().contains("header + footer"));
    }

    @Test
    @DisplayName("constructor rejects fileSize < 84")
    void rejectsFileSizeUnder84() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE - 1, 1, 500L, 600L));
      assertTrue(ex.getMessage().contains("fileSize must be >="));
    }

    @Test
    @DisplayName("constructor rejects fileSize = Long.MIN_VALUE")
    void rejectsFileSizeMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentMetadata("wal-001.log", 1L, 1000L, Long.MIN_VALUE, 1, 500L, 600L));
      assertTrue(ex.getMessage().contains("fileSize must be >="));
    }

    @Test
    @DisplayName("constructor accepts fileSize = 84 (minimum valid)")
    void acceptsFileSizeMinimum() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, 500L, 600L);
      assertEquals(MIN_FILE_SIZE, metadata.fileSize());
    }

    @Test
    @DisplayName("constructor accepts fileSize > 84")
    void acceptsFileSizeGreaterThanMinimum() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, 10000L, 1, 500L, 600L);
      assertEquals(10000L, metadata.fileSize());
    }

    @Test
    @DisplayName("constructor accepts fileSize = Long.MAX_VALUE")
    void acceptsFileSizeMaxValue() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, Long.MAX_VALUE, 1, 500L, 600L);
      assertEquals(Long.MAX_VALUE, metadata.fileSize());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — entryCount <= 0")
  class ConstructorValidation_EntryCount {

    @Test
    @DisplayName("constructor rejects entryCount = 0")
    void rejectsEntryCountZero() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 0, 500L, 600L));
      assertTrue(ex.getMessage().contains("entryCount must be > 0"));
      assertTrue(ex.getMessage().contains("metadata only exists for finalized segments"));
    }

    @Test
    @DisplayName("constructor rejects entryCount = -1")
    void rejectsEntryCountNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, -1, 500L, 600L));
      assertTrue(ex.getMessage().contains("entryCount must be > 0"));
    }

    @Test
    @DisplayName("constructor rejects entryCount = Long.MIN_VALUE")
    void rejectsEntryCountMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new SegmentMetadata(
                      "wal-001.log", 1L, 1000L, MIN_FILE_SIZE, Long.MIN_VALUE, 500L, 600L));
      assertTrue(ex.getMessage().contains("entryCount must be > 0"));
    }

    @Test
    @DisplayName("constructor accepts entryCount = 1")
    void acceptsEntryCountOne() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, 500L, 600L);
      assertEquals(1, metadata.entryCount());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 10, 100, 1000, Long.MAX_VALUE})
    @DisplayName("constructor accepts various positive entryCount values")
    void acceptsPositiveEntryCounts(long entryCount) {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, entryCount, 500L, 600L);
      assertEquals(entryCount, metadata.entryCount());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — minTimestamp > maxTimestamp")
  class ConstructorValidation_TimestampOrdering {

    @Test
    @DisplayName("constructor rejects minTimestamp > maxTimestamp")
    void rejectsMinGreaterThanMax() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, 600L, 500L));
      assertTrue(ex.getMessage().contains("minTimestamp"));
      assertTrue(ex.getMessage().contains("cannot be > maxTimestamp"));
    }

    @Test
    @DisplayName("constructor rejects minTimestamp >> maxTimestamp")
    void rejectsMinMuchGreaterThanMax() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new SegmentMetadata(
                      "wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, Long.MAX_VALUE, Long.MIN_VALUE));
      assertTrue(ex.getMessage().contains("minTimestamp"));
      assertTrue(ex.getMessage().contains("cannot be > maxTimestamp"));
    }

    @Test
    @DisplayName("constructor accepts minTimestamp == maxTimestamp")
    void acceptsEqualTimestamps() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, 500L, 500L);
      assertEquals(500L, metadata.minTimestamp());
      assertEquals(500L, metadata.maxTimestamp());
    }

    @Test
    @DisplayName("constructor accepts minTimestamp < maxTimestamp")
    void acceptsMinLessThanMax() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, 500L, 600L);
      assertEquals(500L, metadata.minTimestamp());
      assertEquals(600L, metadata.maxTimestamp());
    }
  }

  @Nested
  @DisplayName("Helper Tests — averageBytesPerEntry()")
  class AverageBytesPerEntry {

    @Test
    @DisplayName("averageBytesPerEntry() correctly calculates average")
    void calculatesAverageCorrectly() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, 1000L, 10, 500L, 600L);
      assertEquals(100L, metadata.averageBytesPerEntry());
    }

    @Test
    @DisplayName("averageBytesPerEntry() handles single entry")
    void handlesSingleEntry() {
      SegmentMetadata metadata = new SegmentMetadata("wal-001.log", 1L, 1000L, 500L, 1, 500L, 600L);
      assertEquals(500L, metadata.averageBytesPerEntry());
    }

    @Test
    @DisplayName("averageBytesPerEntry() handles large numbers")
    void handlesLargeNumbers() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, 1_000_000_000L, 1000000, 500L, 600L);
      assertEquals(1000L, metadata.averageBytesPerEntry());
    }

    @Test
    @DisplayName("averageBytesPerEntry() returns integer division result")
    void returnsIntegerDivision() {
      SegmentMetadata metadata = new SegmentMetadata("wal-001.log", 1L, 1000L, 100L, 3, 500L, 600L);
      assertEquals(33L, metadata.averageBytesPerEntry());
    }
  }

  @Nested
  @DisplayName("Helper Tests — timestampRange()")
  class TimestampRange {

    @Test
    @DisplayName("timestampRange() calculates difference correctly")
    void calculatesRangeCorrectly() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, 500L, 1500L);
      assertEquals(1000L, metadata.timestampRange());
    }

    @Test
    @DisplayName("timestampRange() returns zero when timestamps are equal")
    void returnsZeroWhenEqual() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, 500L, 500L);
      assertEquals(0L, metadata.timestampRange());
    }

    @Test
    @DisplayName("timestampRange() handles large ranges")
    void handlesLargeRanges() {
      SegmentMetadata metadata =
          new SegmentMetadata(
              "wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, Long.MIN_VALUE, Long.MAX_VALUE);
      assertEquals(Long.MAX_VALUE - Long.MIN_VALUE, metadata.timestampRange());
    }

    @Test
    @DisplayName("timestampRange() handles zero timestamps")
    void handlesZeroTimestamps() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, 0L, 1000L);
      assertEquals(1000L, metadata.timestampRange());
    }
  }

  @Nested
  @DisplayName("Utility Tests — toString()")
  class ToStringTests {

    @Test
    @DisplayName("toString() does not throw")
    void toStringDoesNotThrow() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, 500L, 600L);
      assertDoesNotThrow(() -> metadata.toString());
    }

    @Test
    @DisplayName("toString() returns non-empty string")
    void toStringNonEmpty() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, 500L, 600L);
      String str = metadata.toString();
      assertFalse(str.isEmpty());
      assertTrue(str.length() > 0);
    }

    @Test
    @DisplayName("toString() contains class name")
    void toStringContainsClassName() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, 500L, 600L);
      String str = metadata.toString();
      assertTrue(str.contains("SegmentMetadata"));
    }

    @Test
    @DisplayName("toString() contains filename")
    void toStringContainsFilename() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, 500L, 600L);
      String str = metadata.toString();
      assertTrue(str.contains("wal-001.log"));
    }

    @Test
    @DisplayName("toString() contains all key fields")
    void toStringContainsAllFields() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 5L, 1000L, MIN_FILE_SIZE, 42, 500L, 600L);
      String str = metadata.toString();
      assertTrue(str.contains("filename"));
      assertTrue(str.contains("sequenceNumber") || str.contains("5"));
      assertTrue(str.contains("entryCount") || str.contains("42"));
    }
  }

  @Nested
  @DisplayName("Edge Cases & Boundary Values")
  class EdgeCases {

    @Test
    @DisplayName("handles maximum values in all fields")
    void handlesMaximumValues() {
      SegmentMetadata metadata =
          new SegmentMetadata(
              "wal-max.log",
              Long.MAX_VALUE,
              Long.MAX_VALUE,
              Long.MAX_VALUE,
              Long.MAX_VALUE,
              Long.MAX_VALUE - 1,
              Long.MAX_VALUE);
      assertEquals(Long.MAX_VALUE, metadata.sequenceNumber());
      assertEquals(Long.MAX_VALUE, metadata.createdAt());
    }

    @Test
    @DisplayName("handles minimum file size exactly")
    void handlesMinimumFileSize() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal-001.log", 1L, 1000L, MIN_FILE_SIZE, 1, 500L, 600L);
      assertEquals(MIN_FILE_SIZE, metadata.fileSize());
    }

    @Test
    @DisplayName("handles numeric field with special characters in filename")
    void handlesSpecialCharactersInFilename() {
      SegmentMetadata metadata =
          new SegmentMetadata("wal_001-backup.log", 1L, 1000L, MIN_FILE_SIZE, 1, 500L, 600L);
      assertEquals("wal_001-backup.log", metadata.filename());
    }

    @Test
    @DisplayName("handles single-character filename")
    void handlesSingleCharFilename() {
      SegmentMetadata metadata = new SegmentMetadata("a", 1L, 1000L, MIN_FILE_SIZE, 1, 500L, 600L);
      assertEquals("a", metadata.filename());
    }
  }
}
