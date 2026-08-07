package io.writeahead.log.models.meta;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConstants;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SegmentHeader Tests — 100% Validation Coverage")
class SegmentHeaderTest {

  @Nested
  @DisplayName("Compact Constructor Validation — magic != 0xAA")
  class ConstructorValidation_Magic {

    @Test
    @DisplayName("constructor rejects magic = 0xBB")
    void rejectsMagic0xBB() {
      byte[] reserved = new byte[22];
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentHeader((byte) 0xBB, (byte) 0x01, 1000L, 1L, reserved, 0L));
      assertTrue(ex.getMessage().contains("magic must be 0xAA"));
    }

    @Test
    @DisplayName("constructor rejects magic = 0x00")
    void rejectsMagic0x00() {
      byte[] reserved = new byte[22];
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentHeader((byte) 0x00, (byte) 0x01, 1000L, 1L, reserved, 0L));
      assertTrue(ex.getMessage().contains("magic must be 0xAA"));
    }

    @Test
    @DisplayName("constructor rejects magic = 0xFF")
    void rejectsMagic0xFF() {
      byte[] reserved = new byte[22];
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentHeader((byte) 0xFF, (byte) 0x01, 1000L, 1L, reserved, 0L));
      assertTrue(ex.getMessage().contains("magic must be 0xAA"));
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — version != 0x01")
  class ConstructorValidation_Version {

    @Test
    @DisplayName("constructor rejects version = 0x00")
    void rejectsVersion0x00() {
      byte[] reserved = new byte[22];
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentHeader((byte) 0xAA, (byte) 0x00, 1000L, 1L, reserved, 0L));
      assertTrue(ex.getMessage().contains("version must be 0x01"));
    }

    @Test
    @DisplayName("constructor rejects version = 0x02")
    void rejectsVersion0x02() {
      byte[] reserved = new byte[22];
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentHeader((byte) 0xAA, (byte) 0x02, 1000L, 1L, reserved, 0L));
      assertTrue(ex.getMessage().contains("version must be 0x01"));
    }

    @Test
    @DisplayName("constructor rejects version = 0xFF")
    void rejectsVersion0xFF() {
      byte[] reserved = new byte[22];
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentHeader((byte) 0xAA, (byte) 0xFF, 1000L, 1L, reserved, 0L));
      assertTrue(ex.getMessage().contains("version must be 0x01"));
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — createdAt < 0")
  class ConstructorValidation_CreatedAt {

    @Test
    @DisplayName("constructor rejects createdAt = -1")
    void rejectsCreatedAtNegativeOne() {
      byte[] reserved = new byte[22];
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentHeader((byte) 0xAA, (byte) 0x01, -1L, 1L, reserved, 0L));
      assertTrue(ex.getMessage().contains("createdAt cannot be negative"));
    }

    @Test
    @DisplayName("constructor rejects createdAt = Long.MIN_VALUE")
    void rejectsCreatedAtMinValue() {
      byte[] reserved = new byte[22];
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentHeader((byte) 0xAA, (byte) 0x01, Long.MIN_VALUE, 1L, reserved, 0L));
      assertTrue(ex.getMessage().contains("createdAt cannot be negative"));
    }

    @Test
    @DisplayName("constructor accepts createdAt = 0")
    void acceptsCreatedAtZero() throws IOException {
      long checksum = SegmentHeader.create(0L, 1L).checksum();
      SegmentHeader header =
          new SegmentHeader((byte) 0xAA, (byte) 0x01, 0L, 1L, new byte[22], checksum);
      assertEquals(0L, header.createdAt());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — segmentSequence < 0")
  class ConstructorValidation_SegmentSequence {

    @Test
    @DisplayName("constructor rejects segmentSequence = -1")
    void rejectsSegmentSequenceNegativeOne() {
      byte[] reserved = new byte[22];
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentHeader((byte) 0xAA, (byte) 0x01, 1000L, -1L, reserved, 0L));
      assertTrue(ex.getMessage().contains("segmentSequence cannot be negative"));
    }

    @Test
    @DisplayName("constructor rejects segmentSequence = Long.MIN_VALUE")
    void rejectsSegmentSequenceMinValue() {
      byte[] reserved = new byte[22];
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new SegmentHeader((byte) 0xAA, (byte) 0x01, 1000L, Long.MIN_VALUE, reserved, 0L));
      assertTrue(ex.getMessage().contains("segmentSequence cannot be negative"));
    }

    @Test
    @DisplayName("constructor accepts segmentSequence = 0")
    void acceptsSegmentSequenceZero() throws IOException {
      long checksum = SegmentHeader.create(1000L, 0L).checksum();
      SegmentHeader header =
          new SegmentHeader((byte) 0xAA, (byte) 0x01, 1000L, 0L, new byte[22], checksum);
      assertEquals(0L, header.segmentSequence());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — reserved == null")
  class ConstructorValidation_ReservedNull {

    @Test
    @DisplayName("constructor rejects reserved = null")
    void rejectsReservedNull() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentHeader((byte) 0xAA, (byte) 0x01, 1000L, 1L, null, 0L));
      assertTrue(ex.getMessage().contains("reserved must be exactly 22 bytes"));
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — reserved.length != 22")
  class ConstructorValidation_ReservedLength {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 21, 23, 100})
    @DisplayName("constructor rejects reserved with wrong length")
    void rejectsWrongLength(int length) {
      byte[] reserved = new byte[length];
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new SegmentHeader((byte) 0xAA, (byte) 0x01, 1000L, 1L, reserved, 0L));
      assertTrue(ex.getMessage().contains("reserved must be exactly 22 bytes"));
    }

    @Test
    @DisplayName("constructor accepts reserved with exactly 22 bytes")
    void acceptsReservedExactly22() throws IOException {
      byte[] reserved = new byte[22];
      long checksum = SegmentHeader.create(1000L, 1L).checksum();
      SegmentHeader header =
          new SegmentHeader((byte) 0xAA, (byte) 0x01, 1000L, 1L, reserved, checksum);
      assertEquals(22, header.reserved().length);
    }
  }

  @Nested
  @DisplayName("Factory Method Tests — create()")
  class FactoryMethod_Create {

    @Test
    @DisplayName("create() produces valid header with magic 0xAA")
    void createProducesMagic0xAA() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      assertEquals((byte) 0xAA, header.magic());
    }

    @Test
    @DisplayName("create() produces valid header with version 0x01")
    void createProducesVersion0x01() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      assertEquals((byte) 0x01, header.version());
    }

    @Test
    @DisplayName("create() sets correct createdAt")
    void createSetsCreatedAt() throws IOException {
      long timestamp = 123456789L;
      SegmentHeader header = SegmentHeader.create(timestamp, 1L);
      assertEquals(timestamp, header.createdAt());
    }

    @Test
    @DisplayName("create() sets correct segmentSequence")
    void createSetsSegmentSequence() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 42L);
      assertEquals(42L, header.segmentSequence());
    }

    @Test
    @DisplayName("create() produces 22-byte reserved array")
    void createReservedArray22Bytes() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      assertEquals(22, header.reserved().length);
    }

    @Test
    @DisplayName("create() sets valid checksum")
    void createSetsValidChecksum() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      assertTrue(header.isValid());
    }
  }

  @Nested
  @DisplayName("Serialization Tests — toBytes() & fromBytes()")
  class Serialization {

    @Test
    @DisplayName("toBytes() produces correct size")
    void toBytesProducesCorrectSize() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      byte[] bytes = header.toBytes();
      assertEquals(WalConstants.SEGMENT_HEADER_SIZE, bytes.length);
    }

    @Test
    @DisplayName("fromBytes() rejects data shorter than header size")
    void fromBytesRejectsShortData() {
      byte[] shortData = new byte[30];
      IOException ex = assertThrows(IOException.class, () -> SegmentHeader.fromBytes(shortData));
      assertTrue(ex.getMessage().contains("Header data too short"));
    }

    @Test
    @DisplayName("fromBytes() rejects unknown version")
    void fromBytesRejectsUnknownVersion() throws IOException {
      byte[] data = new byte[WalConstants.SEGMENT_HEADER_SIZE];
      data[1] = (byte) 0x99;
      IOException ex = assertThrows(IOException.class, () -> SegmentHeader.fromBytes(data));
      assertTrue(ex.getMessage().contains("Unknown segment version"));
    }

    @Test
    @DisplayName("fromBytes() rejects version 0x02 (not yet implemented)")
    void fromBytesRejectsVersion0x02() throws IOException {
      byte[] data = new byte[WalConstants.SEGMENT_HEADER_SIZE];
      data[1] = (byte) 0x02;
      IOException ex = assertThrows(IOException.class, () -> SegmentHeader.fromBytes(data));
      assertTrue(ex.getMessage().contains("Version 0x02 not yet implemented"));
    }

    @Test
    @DisplayName("round-trip: create -> toBytes -> fromBytes preserves data")
    void roundTripPreservesData() throws IOException {
      SegmentHeader original = SegmentHeader.create(999999L, 555L);
      byte[] bytes = original.toBytes();
      SegmentHeader deserialized = SegmentHeader.fromBytes(bytes);

      assertEquals(original.magic(), deserialized.magic());
      assertEquals(original.version(), deserialized.version());
      assertEquals(original.createdAt(), deserialized.createdAt());
      assertEquals(original.segmentSequence(), deserialized.segmentSequence());
      assertArrayEquals(original.reserved(), deserialized.reserved());
      assertEquals(original.checksum(), deserialized.checksum());
    }

    @Test
    @DisplayName("round-trip: multiple cycles produce identical bytes")
    void multipleRoundTripsCycle() throws IOException {
      SegmentHeader header1 = SegmentHeader.create(1000L, 1L);
      byte[] bytes1 = header1.toBytes();

      SegmentHeader header2 = SegmentHeader.fromBytes(bytes1);
      byte[] bytes2 = header2.toBytes();

      SegmentHeader header3 = SegmentHeader.fromBytes(bytes2);
      byte[] bytes3 = header3.toBytes();

      assertArrayEquals(bytes1, bytes2);
      assertArrayEquals(bytes2, bytes3);
    }
  }

  @Nested
  @DisplayName("Validation Tests — isValid()")
  class ValidationMethod {

    @Test
    @DisplayName("isValid() returns true for valid header")
    void isValidTrueForValid() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      assertTrue(header.isValid());
    }

    @Test
    @DisplayName("isValid() returns true for deserialized valid header")
    void isValidTrueForDeserialized() throws IOException {
      SegmentHeader original = SegmentHeader.create(1000L, 1L);
      byte[] bytes = original.toBytes();
      SegmentHeader deserialized = SegmentHeader.fromBytes(bytes);
      assertTrue(deserialized.isValid());
    }

    @Test
    @DisplayName("isValid() returns false when checksum is corrupted")
    void isValidFalseForCorruptedChecksum() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      SegmentHeader corrupted =
          new SegmentHeader(
              header.magic(),
              header.version(),
              header.createdAt(),
              header.segmentSequence(),
              header.reserved(),
              header.checksum() + 1);
      assertFalse(corrupted.isValid());
    }
  }

  @Nested
  @DisplayName("Helper Tests — computedChecksum()")
  class ComputedChecksum {

    @Test
    @DisplayName("computedChecksum() matches stored checksum for valid header")
    void computedChecksumMatches() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      assertEquals(header.checksum(), header.computedChecksum());
    }

    @Test
    @DisplayName("computedChecksum() detects changes to createdAt")
    void computedChecksumDetectsCreatedAtChange() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      long originalChecksum = header.checksum();

      SegmentHeader modified =
          new SegmentHeader(
              header.magic(),
              header.version(),
              1001L,
              header.segmentSequence(),
              header.reserved(),
              originalChecksum);

      assertNotEquals(originalChecksum, modified.computedChecksum());
    }

    @Test
    @DisplayName("computedChecksum() detects changes to segmentSequence")
    void computedChecksumDetectsSequenceChange() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      long originalChecksum = header.checksum();

      SegmentHeader modified =
          new SegmentHeader(
              header.magic(),
              header.version(),
              header.createdAt(),
              2L,
              header.reserved(),
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
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      assertDoesNotThrow(() -> header.toString());
    }

    @Test
    @DisplayName("toString() returns non-empty string")
    void toStringNonEmpty() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      String str = header.toString();
      assertFalse(str.isEmpty());
      assertTrue(str.length() > 0);
    }

    @Test
    @DisplayName("toString() contains class name")
    void toStringContainsClassName() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      String str = header.toString();
      assertTrue(str.contains("SegmentHeader"));
    }

    @Test
    @DisplayName("toString() contains isValid status")
    void toStringContainsValidStatus() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      String str = header.toString();
      assertTrue(str.contains("valid=true"));
    }

    @Test
    @DisplayName("toString() contains magic byte")
    void toStringContainsMagic() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 1L);
      String str = header.toString();
      assertTrue(str.contains("magic=0xAA"));
    }
  }

  @Nested
  @DisplayName("Edge Cases & Boundary Values")
  class EdgeCases {

    @Test
    @DisplayName("handles Long.MAX_VALUE timestamp")
    void handlesLongMaxTimestamp() throws IOException {
      SegmentHeader header = SegmentHeader.create(Long.MAX_VALUE, 1L);
      assertEquals(Long.MAX_VALUE, header.createdAt());
    }

    @Test
    @DisplayName("handles Long.MAX_VALUE sequence")
    void handlesLongMaxSequence() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, Long.MAX_VALUE);
      assertEquals(Long.MAX_VALUE, header.segmentSequence());
    }

    @Test
    @DisplayName("handles zero timestamp")
    void handlesZeroTimestamp() throws IOException {
      SegmentHeader header = SegmentHeader.create(0L, 1L);
      assertEquals(0L, header.createdAt());
    }

    @Test
    @DisplayName("handles zero sequence")
    void handlesZeroSequence() throws IOException {
      SegmentHeader header = SegmentHeader.create(1000L, 0L);
      assertEquals(0L, header.segmentSequence());
    }
  }
}
