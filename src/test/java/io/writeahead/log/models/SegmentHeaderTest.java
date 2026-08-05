package io.writeahead.log.models;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.meta.SegmentHeader;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class SegmentHeaderTest {

  private static final long REFERENCE_TIMESTAMP = 1000000L;
  private static final long REFERENCE_SEQUENCE = 5L;
  private static final long LARGE_TIMESTAMP = 1234567890123L;
  private static final long ZERO_SEQUENCE = 0L;
  private static final long SEQUENCE_RANGE_MAX = 100L;
  private static final byte INVALID_MAGIC_BYTE = (byte) 0xBB;
  private static final int HEADER_SERIALIZATION_SIZE = 48;
  private static final int UNDERSIZED_BUFFER_SIZE = 10;

  @Test
  void createHeaderProducesValidHeaderWithCorrectMagicAndVersion() throws Exception {
    long currentTimestamp = System.currentTimeMillis();
    long sequenceNumber = REFERENCE_SEQUENCE;

    SegmentHeader createdHeader = SegmentHeader.create(currentTimestamp, sequenceNumber);

    assertEquals((byte) 0xAA, createdHeader.magic());
    assertEquals(0x01, createdHeader.version());
    assertEquals(currentTimestamp, createdHeader.createdAt());
    assertEquals(sequenceNumber, createdHeader.segmentSequence());
    assertTrue(createdHeader.isValid());
  }

  @Test
  void serializationAndDeserializationPreservesAllHeaderFields() throws Exception {
    SegmentHeader originalHeader = SegmentHeader.create(REFERENCE_TIMESTAMP, REFERENCE_SEQUENCE);
    byte[] serializedHeaderBytes = originalHeader.toBytes();

    assertEquals(HEADER_SERIALIZATION_SIZE, serializedHeaderBytes.length);

    SegmentHeader deserializedHeader = SegmentHeader.fromBytes(serializedHeaderBytes);

    assertEquals(originalHeader.magic(), deserializedHeader.magic());
    assertEquals(originalHeader.version(), deserializedHeader.version());
    assertEquals(originalHeader.createdAt(), deserializedHeader.createdAt());
    assertEquals(originalHeader.segmentSequence(), deserializedHeader.segmentSequence());
    assertEquals(originalHeader.checksum(), deserializedHeader.checksum());
    assertTrue(deserializedHeader.isValid());
  }

  @Test
  void corruptedMagicByteRendersHeaderInvalid() throws Exception {
    SegmentHeader validHeader = SegmentHeader.create(System.currentTimeMillis(), 1);
    byte[] serializedHeaderBytes = validHeader.toBytes();

    serializedHeaderBytes[0] = INVALID_MAGIC_BYTE;

    SegmentHeader corruptedHeader = SegmentHeader.fromBytes(serializedHeaderBytes);
    assertFalse(corruptedHeader.isValid());
  }

  @Test
  void corruptedChecksumRendersHeaderInvalid() throws Exception {
    SegmentHeader validHeader = SegmentHeader.create(System.currentTimeMillis(), 1);
    byte[] serializedHeaderBytes = validHeader.toBytes();

    serializedHeaderBytes[47] = (byte) ~serializedHeaderBytes[47];

    SegmentHeader corruptedHeader = SegmentHeader.fromBytes(serializedHeaderBytes);
    assertFalse(corruptedHeader.isValid());
  }

  @Test
  void multipleSequenceNumbersSerializeAndDeserializeCorrectly() throws Exception {
    for (long sequenceNumber = 1; sequenceNumber <= SEQUENCE_RANGE_MAX; sequenceNumber++) {
      SegmentHeader headerForSequence =
          SegmentHeader.create(System.currentTimeMillis(), sequenceNumber);
      byte[] serializedHeaderBytes = headerForSequence.toBytes();
      SegmentHeader deserializedHeader = SegmentHeader.fromBytes(serializedHeaderBytes);

      assertEquals(sequenceNumber, deserializedHeader.segmentSequence());
      assertTrue(deserializedHeader.isValid());
    }
  }

  @Test
  void largeTimestampPreservedThroughSerializationRoundTrip() throws Exception {
    SegmentHeader headerWithLargeTimestamp = SegmentHeader.create(LARGE_TIMESTAMP, 1);
    byte[] serializedHeaderBytes = headerWithLargeTimestamp.toBytes();
    SegmentHeader deserializedHeader = SegmentHeader.fromBytes(serializedHeaderBytes);

    assertEquals(LARGE_TIMESTAMP, deserializedHeader.createdAt());
  }

  @Test
  void corruptedTimestampFieldRendersHeaderInvalid() throws Exception {
    SegmentHeader validHeader = SegmentHeader.create(REFERENCE_TIMESTAMP, 1);
    byte[] serializedHeaderBytes = validHeader.toBytes();

    serializedHeaderBytes[5] = (byte) ~serializedHeaderBytes[5];

    SegmentHeader corruptedHeader = SegmentHeader.fromBytes(serializedHeaderBytes);
    assertFalse(corruptedHeader.isValid());
  }

  @Test
  void corruptedSequenceFieldRendersHeaderInvalid() throws Exception {
    SegmentHeader validHeader = SegmentHeader.create(System.currentTimeMillis(), 42);
    byte[] serializedHeaderBytes = validHeader.toBytes();

    serializedHeaderBytes[15] = (byte) ~serializedHeaderBytes[15];

    SegmentHeader corruptedHeader = SegmentHeader.fromBytes(serializedHeaderBytes);
    assertFalse(corruptedHeader.isValid());
  }

  @Test
  void zeroSequenceNumberIsValidAndPreserved() throws Exception {
    SegmentHeader headerWithZeroSequence =
        SegmentHeader.create(System.currentTimeMillis(), ZERO_SEQUENCE);
    byte[] serializedHeaderBytes = headerWithZeroSequence.toBytes();
    SegmentHeader deserializedHeader = SegmentHeader.fromBytes(serializedHeaderBytes);

    assertEquals(ZERO_SEQUENCE, deserializedHeader.segmentSequence());
    assertTrue(deserializedHeader.isValid());
  }

  @Test
  void maximumSequenceNumberIsValidAndPreserved() throws Exception {
    long maximumSequenceNumber = Long.MAX_VALUE;
    SegmentHeader headerWithMaxSequence =
        SegmentHeader.create(System.currentTimeMillis(), maximumSequenceNumber);
    byte[] serializedHeaderBytes = headerWithMaxSequence.toBytes();
    SegmentHeader deserializedHeader = SegmentHeader.fromBytes(serializedHeaderBytes);

    assertEquals(maximumSequenceNumber, deserializedHeader.segmentSequence());
    assertTrue(deserializedHeader.isValid());
  }

  @Test
  void undersizedInputBufferThrowsIOException() {
    byte[] undersizedBuffer = new byte[UNDERSIZED_BUFFER_SIZE];

    assertThrows(IOException.class, () -> SegmentHeader.fromBytes(undersizedBuffer));
  }

  @Test
  void toStringContainsAllRelevantHeaderFields() throws Exception {
    SegmentHeader header = SegmentHeader.create(REFERENCE_TIMESTAMP, REFERENCE_SEQUENCE);
    String headerStringRepresentation = header.toString();

    assertTrue(headerStringRepresentation.contains("magic"));
    assertTrue(headerStringRepresentation.contains("version"));
    assertTrue(headerStringRepresentation.contains("createdAt"));
    assertTrue(headerStringRepresentation.contains("segmentSequence"));
    assertTrue(headerStringRepresentation.contains("valid"));
  }

  @Test
  void multipleDistinctHeadersSerializeIndependently() throws Exception {
    SegmentHeader firstHeader = SegmentHeader.create(1000L, 1);
    SegmentHeader secondHeader = SegmentHeader.create(2000L, 2);
    SegmentHeader thirdHeader = SegmentHeader.create(3000L, 3);

    byte[] firstHeaderSerialized = firstHeader.toBytes();
    byte[] secondHeaderSerialized = secondHeader.toBytes();
    byte[] thirdHeaderSerialized = thirdHeader.toBytes();

    SegmentHeader firstHeaderDeserialized = SegmentHeader.fromBytes(firstHeaderSerialized);
    SegmentHeader secondHeaderDeserialized = SegmentHeader.fromBytes(secondHeaderSerialized);
    SegmentHeader thirdHeaderDeserialized = SegmentHeader.fromBytes(thirdHeaderSerialized);

    assertEquals(1, firstHeaderDeserialized.segmentSequence());
    assertEquals(2, secondHeaderDeserialized.segmentSequence());
    assertEquals(3, thirdHeaderDeserialized.segmentSequence());

    assertTrue(
        firstHeaderDeserialized.isValid()
            && secondHeaderDeserialized.isValid()
            && thirdHeaderDeserialized.isValid());
  }
}
