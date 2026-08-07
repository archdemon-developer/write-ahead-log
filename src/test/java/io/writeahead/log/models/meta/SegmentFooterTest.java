package io.writeahead.log.models.meta;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConstants;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SegmentFooter Tests — 100% Validation Coverage")
class SegmentFooterTest {

  private static final long COMPLETE_MARKER = 0xDEADBEEFL;

  @Nested
  @DisplayName("Compact Constructor Validation — entryCount <= 0")
  class ConstructorValidation_EntryCount {

    @Test
    @DisplayName("constructor rejects entryCount = 0")
    void rejectsEntryCountZero() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentFooter(0, 1000L, 2000L, COMPLETE_MARKER, 0L));
      assertTrue(ex.getMessage().contains("entryCount must be > 0"));
    }

    @Test
    @DisplayName("constructor rejects entryCount = -1")
    void rejectsEntryCountNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentFooter(-1, 1000L, 2000L, COMPLETE_MARKER, 0L));
      assertTrue(ex.getMessage().contains("entryCount must be > 0"));
    }

    @Test
    @DisplayName("constructor rejects entryCount = Integer.MIN_VALUE")
    void rejectsEntryCountMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentFooter(Integer.MIN_VALUE, 1000L, 2000L, COMPLETE_MARKER, 0L));
      assertTrue(ex.getMessage().contains("entryCount must be > 0"));
    }

    @Test
    @DisplayName("constructor accepts entryCount = 1")
    void acceptsEntryCountOne() throws IOException {
      long checksum = SegmentFooter.create(1, 1000L, 2000L).checksum();
      SegmentFooter footer = new SegmentFooter(1, 1000L, 2000L, COMPLETE_MARKER, checksum);
      assertEquals(1, footer.entryCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100, Integer.MAX_VALUE})
    @DisplayName("constructor accepts various positive entryCount values")
    void acceptsPositiveEntryCounts(int entryCount) throws IOException {
      long checksum = SegmentFooter.create(entryCount, 1000L, 2000L).checksum();
      SegmentFooter footer = new SegmentFooter(entryCount, 1000L, 2000L, COMPLETE_MARKER, checksum);
      assertEquals(entryCount, footer.entryCount());
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
              () -> new SegmentFooter(1, 2000L, 1000L, COMPLETE_MARKER, 0L));
      assertTrue(
          ex.getMessage().contains("minTimestamp")
              && ex.getMessage().contains("cannot be > maxTimestamp"));
    }

    @Test
    @DisplayName("constructor rejects minTimestamp >> maxTimestamp")
    void rejectsMinMuchGreaterThanMax() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentFooter(1, Long.MAX_VALUE, Long.MIN_VALUE, COMPLETE_MARKER, 0L));
      assertTrue(
          ex.getMessage().contains("minTimestamp")
              && ex.getMessage().contains("cannot be > maxTimestamp"));
    }

    @Test
    @DisplayName("constructor accepts minTimestamp == maxTimestamp")
    void acceptsEqualTimestamps() throws IOException {
      long checksum = SegmentFooter.create(1, 1000L, 1000L).checksum();
      SegmentFooter footer = new SegmentFooter(1, 1000L, 1000L, COMPLETE_MARKER, checksum);
      assertEquals(1000L, footer.minTimestamp());
      assertEquals(1000L, footer.maxTimestamp());
    }

    @Test
    @DisplayName("constructor accepts minTimestamp < maxTimestamp")
    void acceptsMinLessThanMax() throws IOException {
      long checksum = SegmentFooter.create(1, 1000L, 2000L).checksum();
      SegmentFooter footer = new SegmentFooter(1, 1000L, 2000L, COMPLETE_MARKER, checksum);
      assertEquals(1000L, footer.minTimestamp());
      assertEquals(2000L, footer.maxTimestamp());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — completeMarker != 0xDEADBEEFL")
  class ConstructorValidation_CompleteMarker {

    @Test
    @DisplayName("constructor rejects completeMarker = 0x00000000L")
    void rejectsMarkerZero() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentFooter(1, 1000L, 2000L, 0x00000000L, 0L));
      assertTrue(ex.getMessage().contains("Complete marker must be: 0xDEADBEEFL"));
    }

    @Test
    @DisplayName("constructor rejects completeMarker = 0xDEADBEEEL (off by 1)")
    void rejectsMarkerOffByOne() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentFooter(1, 1000L, 2000L, 0xDEADBEEEL, 0L));
      assertTrue(ex.getMessage().contains("Complete marker must be: 0xDEADBEEFL"));
    }

    @Test
    @DisplayName("constructor rejects completeMarker = Long.MAX_VALUE")
    void rejectsMarkerMaxValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentFooter(1, 1000L, 2000L, Long.MAX_VALUE, 0L));
      assertTrue(ex.getMessage().contains("Complete marker must be: 0xDEADBEEFL"));
    }

    @Test
    @DisplayName("constructor accepts completeMarker = 0xDEADBEEFL")
    void acceptsCorrectMarker() throws IOException {
      long checksum = SegmentFooter.create(1, 1000L, 2000L).checksum();
      SegmentFooter footer = new SegmentFooter(1, 1000L, 2000L, COMPLETE_MARKER, checksum);
      assertEquals(COMPLETE_MARKER, footer.completeMarker());
    }
  }

  @Nested
  @DisplayName("Factory Method Tests — create()")
  class FactoryMethod_Create {

    @Test
    @DisplayName("create() sets correct entryCount")
    void createSetsEntryCount() throws IOException {
      SegmentFooter footer = SegmentFooter.create(42, 1000L, 2000L);
      assertEquals(42, footer.entryCount());
    }

    @Test
    @DisplayName("create() sets correct minTimestamp")
    void createSetsMinTimestamp() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 111111L, 222222L);
      assertEquals(111111L, footer.minTimestamp());
    }

    @Test
    @DisplayName("create() sets correct maxTimestamp")
    void createSetsMaxTimestamp() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 111111L, 222222L);
      assertEquals(222222L, footer.maxTimestamp());
    }

    @Test
    @DisplayName("create() sets complete marker to 0xDEADBEEFL")
    void createSetsCompleteMarker() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 1000L, 2000L);
      assertEquals(COMPLETE_MARKER, footer.completeMarker());
    }

    @Test
    @DisplayName("create() produces valid checksum")
    void createProducesValidChecksum() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 1000L, 2000L);
      assertTrue(footer.isValid());
    }
  }

  @Nested
  @DisplayName("Serialization Tests — toBytes() & fromBytes()")
  class Serialization {

    @Test
    @DisplayName("toBytes() produces correct size")
    void toBytesProducesCorrectSize() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 1000L, 2000L);
      byte[] bytes = footer.toBytes();
      assertEquals(WalConstants.SEGMENT_FOOTER_SIZE, bytes.length);
    }

    @Test
    @DisplayName("fromBytes() rejects data shorter than footer size")
    void fromBytesRejectsShortData() {
      byte[] shortData = new byte[20];
      IOException ex = assertThrows(IOException.class, () -> SegmentFooter.fromBytes(shortData));
      assertTrue(ex.getMessage().contains("Footer data too short"));
    }

    @Test
    @DisplayName("round-trip: create -> toBytes -> fromBytes preserves data")
    void roundTripPreservesData() throws IOException {
      SegmentFooter original = SegmentFooter.create(42, 1000L, 2000L);
      byte[] bytes = original.toBytes();
      SegmentFooter deserialized = SegmentFooter.fromBytes(bytes);

      assertEquals(original.entryCount(), deserialized.entryCount());
      assertEquals(original.minTimestamp(), deserialized.minTimestamp());
      assertEquals(original.maxTimestamp(), deserialized.maxTimestamp());
      assertEquals(original.completeMarker(), deserialized.completeMarker());
      assertEquals(original.checksum(), deserialized.checksum());
    }

    @Test
    @DisplayName("round-trip: multiple cycles produce identical bytes")
    void multipleRoundTripsCycle() throws IOException {
      SegmentFooter footer1 = SegmentFooter.create(10, 1000L, 2000L);
      byte[] bytes1 = footer1.toBytes();

      SegmentFooter footer2 = SegmentFooter.fromBytes(bytes1);
      byte[] bytes2 = footer2.toBytes();

      SegmentFooter footer3 = SegmentFooter.fromBytes(bytes2);
      byte[] bytes3 = footer3.toBytes();

      assertArrayEquals(bytes1, bytes2);
      assertArrayEquals(bytes2, bytes3);
    }

    @Test
    @DisplayName("fromBytes() correctly parses all fields")
    void fromBytesCorrectlyParsesAllFields() throws IOException {
      SegmentFooter original = SegmentFooter.create(100, 500000L, 600000L);
      byte[] bytes = original.toBytes();
      SegmentFooter parsed = SegmentFooter.fromBytes(bytes);

      assertEquals(100, parsed.entryCount());
      assertEquals(500000L, parsed.minTimestamp());
      assertEquals(600000L, parsed.maxTimestamp());
      assertEquals(COMPLETE_MARKER, parsed.completeMarker());
    }
  }

  @Nested
  @DisplayName("Validation Tests — isValid()")
  class ValidationMethod {

    @Test
    @DisplayName("isValid() returns true for valid footer")
    void isValidTrueForValid() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 1000L, 2000L);
      assertTrue(footer.isValid());
    }

    @Test
    @DisplayName("isValid() returns true for deserialized valid footer")
    void isValidTrueForDeserialized() throws IOException {
      SegmentFooter original = SegmentFooter.create(1, 1000L, 2000L);
      byte[] bytes = original.toBytes();
      SegmentFooter deserialized = SegmentFooter.fromBytes(bytes);
      assertTrue(deserialized.isValid());
    }

    @Test
    @DisplayName("isValid() returns false when checksum is corrupted")
    void isValidFalseForCorruptedChecksum() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 1000L, 2000L);
      SegmentFooter corrupted =
          new SegmentFooter(
              footer.entryCount(),
              footer.minTimestamp(),
              footer.maxTimestamp(),
              footer.completeMarker(),
              footer.checksum() + 1);
      assertFalse(corrupted.isValid());
    }

    @Test
    @DisplayName("isValid() returns false when entryCount is modified")
    void isValidFalseWhenEntryCountModified() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 1000L, 2000L);
      SegmentFooter modified =
          new SegmentFooter(
              2,
              footer.minTimestamp(),
              footer.maxTimestamp(),
              footer.completeMarker(),
              footer.checksum());
      assertFalse(modified.isValid());
    }
  }

  @Nested
  @DisplayName("Helper Tests — computedChecksum()")
  class ComputedChecksum {

    @Test
    @DisplayName("computedChecksum() matches stored checksum for valid footer")
    void computedChecksumMatches() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 1000L, 2000L);
      assertEquals(footer.checksum(), footer.computedChecksum());
    }

    @Test
    @DisplayName("computedChecksum() detects changes to entryCount")
    void computedChecksumDetectsEntryCountChange() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 1000L, 2000L);
      long originalChecksum = footer.checksum();

      SegmentFooter modified =
          new SegmentFooter(
              2,
              footer.minTimestamp(),
              footer.maxTimestamp(),
              footer.completeMarker(),
              originalChecksum);

      assertNotEquals(originalChecksum, modified.computedChecksum());
    }

    @Test
    @DisplayName("computedChecksum() detects changes to minTimestamp")
    void computedChecksumDetectsMinTimestampChange() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 1000L, 2000L);
      long originalChecksum = footer.checksum();

      SegmentFooter modified =
          new SegmentFooter(
              footer.entryCount(),
              1001L,
              footer.maxTimestamp(),
              footer.completeMarker(),
              originalChecksum);

      assertNotEquals(originalChecksum, modified.computedChecksum());
    }

    @Test
    @DisplayName("computedChecksum() detects changes to maxTimestamp")
    void computedChecksumDetectsMaxTimestampChange() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 1000L, 2000L);
      long originalChecksum = footer.checksum();

      SegmentFooter modified =
          new SegmentFooter(
              footer.entryCount(),
              footer.minTimestamp(),
              2001L,
              footer.completeMarker(),
              originalChecksum);

      assertNotEquals(originalChecksum, modified.computedChecksum());
    }
  }

  @Nested
  @DisplayName("Utility Tests — toString()")
  class ToStringTests {

    @Test
    @DisplayName("toString() does not throw")
    void toStringDoesNotThrow() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 1000L, 2000L);
      assertDoesNotThrow(() -> footer.toString());
    }

    @Test
    @DisplayName("toString() returns non-empty string")
    void toStringNonEmpty() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 1000L, 2000L);
      String str = footer.toString();
      assertFalse(str.isEmpty());
      assertTrue(str.length() > 0);
    }

    @Test
    @DisplayName("toString() contains class name")
    void toStringContainsClassName() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 1000L, 2000L);
      String str = footer.toString();
      assertTrue(str.contains("SegmentFooter"));
    }

    @Test
    @DisplayName("toString() contains isValid status")
    void toStringContainsValidStatus() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 1000L, 2000L);
      String str = footer.toString();
      assertTrue(str.contains("valid=true"));
    }

    @Test
    @DisplayName("toString() contains entryCount")
    void toStringContainsEntryCount() throws IOException {
      SegmentFooter footer = SegmentFooter.create(42, 1000L, 2000L);
      String str = footer.toString();
      assertTrue(str.contains("entryCount=42"));
    }
  }

  @Nested
  @DisplayName("Edge Cases & Boundary Values")
  class EdgeCases {

    @Test
    @DisplayName("handles Integer.MAX_VALUE entryCount")
    void handlesMaxEntryCount() throws IOException {
      SegmentFooter footer = SegmentFooter.create(Integer.MAX_VALUE, 1000L, 2000L);
      assertEquals(Integer.MAX_VALUE, footer.entryCount());
    }

    @Test
    @DisplayName("handles Long.MIN_VALUE timestamps")
    void handlesLongMinTimestamps() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, Long.MIN_VALUE, Long.MIN_VALUE);
      assertEquals(Long.MIN_VALUE, footer.minTimestamp());
      assertEquals(Long.MIN_VALUE, footer.maxTimestamp());
    }

    @Test
    @DisplayName("handles Long.MAX_VALUE timestamps")
    void handlesLongMaxTimestamps() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, Long.MAX_VALUE, Long.MAX_VALUE);
      assertEquals(Long.MAX_VALUE, footer.minTimestamp());
      assertEquals(Long.MAX_VALUE, footer.maxTimestamp());
    }

    @Test
    @DisplayName("handles zero timestamps")
    void handlesZeroTimestamps() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 0L, 0L);
      assertEquals(0L, footer.minTimestamp());
      assertEquals(0L, footer.maxTimestamp());
    }

    @Test
    @DisplayName("handles large timestamp ranges")
    void handlesLargeTimestampRanges() throws IOException {
      SegmentFooter footer = SegmentFooter.create(1, 0L, Long.MAX_VALUE);
      assertEquals(0L, footer.minTimestamp());
      assertEquals(Long.MAX_VALUE, footer.maxTimestamp());
    }
  }
}
