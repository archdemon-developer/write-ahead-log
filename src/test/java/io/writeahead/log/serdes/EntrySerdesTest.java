package io.writeahead.log.serdes;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class EntrySerdesTest {

  private static final long REFERENCE_TIMESTAMP = 1000L;
  private static final String HELLO_PAYLOAD = "hello";
  private static final int HELLO_SIZE = 5;
  private static final int EMPTY_SIZE = 0;
  private static final int LARGE_PAYLOAD_SIZE = 1000;
  private static final int TIMESTAMP_SERIALIZED_BYTES = 8;
  private static final int SIZE_FIELD_SERIALIZED_BYTES = 4;
  private static final int CRC_SERIALIZED_BYTES = 8;
  private static final int EXPECTED_SANSE_CRC_EMPTY = 12;
  private static final int EXPECTED_SANSE_CRC_HELLO = 17;
  private static final int EXPECTED_WITH_CRC_HELLO = 25;
  private static final int EXPECTED_LARGE_SANSE_CRC = 1012;
  private static final long REFERENCE_CRC = 12345L;
  private static final int DESERIALIZED_RESULT_ARRAY_LENGTH = 4;

  @Test
  void serializeEntrySanseCrcWithSimplePayloadProducesExpectedByteLength() throws IOException {
    byte[] helloPayloadBytes = HELLO_PAYLOAD.getBytes();

    byte[] serializedEntryBytes =
        EntrySerdes.serializeEntrySanseCrc(REFERENCE_TIMESTAMP, HELLO_SIZE, helloPayloadBytes);

    assertEquals(EXPECTED_SANSE_CRC_HELLO, serializedEntryBytes.length);
  }

  @Test
  void serializeEntrySanseCrcWithEmptyPayloadProducesTimestampAndSizeOnly() throws IOException {
    byte[] emptyPayloadBytes = new byte[0];

    byte[] serializedEntryBytes =
        EntrySerdes.serializeEntrySanseCrc(REFERENCE_TIMESTAMP, EMPTY_SIZE, emptyPayloadBytes);

    assertEquals(EXPECTED_SANSE_CRC_EMPTY, serializedEntryBytes.length);
  }

  @Test
  void serializeEntrySanseCrcWithLargePayloadProducesExpectedByteLength() throws IOException {
    byte[] largePayloadBytes = new byte[LARGE_PAYLOAD_SIZE];
    for (int index = 0; index < largePayloadBytes.length; index++) {
      largePayloadBytes[index] = (byte) (index % 256);
    }

    byte[] serializedEntryBytes =
        EntrySerdes.serializeEntrySanseCrc(
            REFERENCE_TIMESTAMP, LARGE_PAYLOAD_SIZE, largePayloadBytes);

    assertEquals(EXPECTED_LARGE_SANSE_CRC, serializedEntryBytes.length);
  }

  @Test
  void serializeEntryWithCrcAddsExactlyEightBytesForCrc() throws IOException {
    byte[] helloPayloadBytes = HELLO_PAYLOAD.getBytes();

    byte[] serializedWithoutCrc =
        EntrySerdes.serializeEntrySanseCrc(REFERENCE_TIMESTAMP, HELLO_SIZE, helloPayloadBytes);
    byte[] serializedWithCrc =
        EntrySerdes.serializeEntryWithCrc(
            REFERENCE_TIMESTAMP, HELLO_SIZE, helloPayloadBytes, REFERENCE_CRC);

    assertEquals(serializedWithoutCrc.length + CRC_SERIALIZED_BYTES, serializedWithCrc.length);
  }

  @Test
  void deserializeEntryRoundTripPreservesAllFieldsAccurately() throws IOException {
    byte[] helloPayloadBytes = HELLO_PAYLOAD.getBytes();

    byte[] serializedEntryBytes =
        EntrySerdes.serializeEntryWithCrc(
            REFERENCE_TIMESTAMP, HELLO_SIZE, helloPayloadBytes, REFERENCE_CRC);

    ByteArrayInputStream byteArrayInputBuffer = new ByteArrayInputStream(serializedEntryBytes);
    DataInputStream dataInputStreamReader = new DataInputStream(byteArrayInputBuffer);
    Object[] deserializationResultArray = EntrySerdes.deserializeEntry(dataInputStreamReader);

    assertEquals(DESERIALIZED_RESULT_ARRAY_LENGTH, deserializationResultArray.length);
    assertEquals(REFERENCE_TIMESTAMP, (Long) deserializationResultArray[0]);
    assertEquals(HELLO_SIZE, (Integer) deserializationResultArray[1]);
    assertArrayEquals(helloPayloadBytes, (byte[]) deserializationResultArray[2]);
    assertEquals(REFERENCE_CRC, (Long) deserializationResultArray[3]);
  }

  @Test
  void serializeAndDeserializeMultipleTimestampsPreserveIndependence() throws IOException {
    long firstTimestamp = 1000L;
    long secondTimestamp = 2000L;
    long thirdTimestamp = 3000L;
    byte[] helloPayloadBytes = HELLO_PAYLOAD.getBytes();

    byte[] firstSerialized =
        EntrySerdes.serializeEntryWithCrc(
            firstTimestamp, HELLO_SIZE, helloPayloadBytes, REFERENCE_CRC);
    byte[] secondSerialized =
        EntrySerdes.serializeEntryWithCrc(
            secondTimestamp, HELLO_SIZE, helloPayloadBytes, REFERENCE_CRC);
    byte[] thirdSerialized =
        EntrySerdes.serializeEntryWithCrc(
            thirdTimestamp, HELLO_SIZE, helloPayloadBytes, REFERENCE_CRC);

    Object[] firstDeserialized = deserializeFromBytes(firstSerialized);
    Object[] secondDeserialized = deserializeFromBytes(secondSerialized);
    Object[] thirdDeserialized = deserializeFromBytes(thirdSerialized);

    assertEquals(firstTimestamp, (Long) firstDeserialized[0]);
    assertEquals(secondTimestamp, (Long) secondDeserialized[0]);
    assertEquals(thirdTimestamp, (Long) thirdDeserialized[0]);
  }

  @Test
  void serializeAndDeserializeMultipleDifferentPayloadsPreserveIndependence() throws IOException {
    byte[] payloadAlpha = "alpha".getBytes();
    byte[] payloadBeta = "beta".getBytes();
    byte[] payloadGamma = "gamma".getBytes();

    byte[] alphaSerializedBytes =
        EntrySerdes.serializeEntryWithCrc(
            REFERENCE_TIMESTAMP, payloadAlpha.length, payloadAlpha, REFERENCE_CRC);
    byte[] betaSerializedBytes =
        EntrySerdes.serializeEntryWithCrc(
            REFERENCE_TIMESTAMP, payloadBeta.length, payloadBeta, REFERENCE_CRC);
    byte[] gammaSerializedBytes =
        EntrySerdes.serializeEntryWithCrc(
            REFERENCE_TIMESTAMP, payloadGamma.length, payloadGamma, REFERENCE_CRC);

    Object[] alphaDeserialized = deserializeFromBytes(alphaSerializedBytes);
    Object[] betaDeserialized = deserializeFromBytes(betaSerializedBytes);
    Object[] gammaDeserialized = deserializeFromBytes(gammaSerializedBytes);

    assertArrayEquals(payloadAlpha, (byte[]) alphaDeserialized[2]);
    assertArrayEquals(payloadBeta, (byte[]) betaDeserialized[2]);
    assertArrayEquals(payloadGamma, (byte[]) gammaDeserialized[2]);
  }

  @Test
  void serializeEntrySanseCrcWithDifferentTimestampsProducesDifferentSerialization()
      throws IOException {
    byte[] helloPayloadBytes = HELLO_PAYLOAD.getBytes();
    long firstTimestamp = 1000L;
    long secondTimestamp = 2000L;

    byte[] firstSerializedBytes =
        EntrySerdes.serializeEntrySanseCrc(firstTimestamp, HELLO_SIZE, helloPayloadBytes);
    byte[] secondSerializedBytes =
        EntrySerdes.serializeEntrySanseCrc(secondTimestamp, HELLO_SIZE, helloPayloadBytes);

    assertFalse(java.util.Arrays.equals(firstSerializedBytes, secondSerializedBytes));
  }

  @Test
  void serializeEntrySanseCrcWithDifferentSizesProducesDifferentSerialization() throws IOException {
    byte[] helloPayloadBytes = HELLO_PAYLOAD.getBytes();

    byte[] serializedWithCorrectSize =
        EntrySerdes.serializeEntrySanseCrc(REFERENCE_TIMESTAMP, HELLO_SIZE, helloPayloadBytes);
    byte[] serializedWithIncorrectSize =
        EntrySerdes.serializeEntrySanseCrc(REFERENCE_TIMESTAMP, HELLO_SIZE + 1, helloPayloadBytes);

    assertFalse(java.util.Arrays.equals(serializedWithCorrectSize, serializedWithIncorrectSize));
  }

  @Test
  void deserializeEntryFromEmptyPayloadSerialization() throws IOException {
    byte[] emptyPayloadBytes = new byte[0];

    byte[] serializedEntryBytes =
        EntrySerdes.serializeEntryWithCrc(
            REFERENCE_TIMESTAMP, EMPTY_SIZE, emptyPayloadBytes, REFERENCE_CRC);

    Object[] deserializationResultArray = deserializeFromBytes(serializedEntryBytes);

    assertEquals(REFERENCE_TIMESTAMP, (Long) deserializationResultArray[0]);
    assertEquals(EMPTY_SIZE, (Integer) deserializationResultArray[1]);
    assertArrayEquals(emptyPayloadBytes, (byte[]) deserializationResultArray[2]);
    assertEquals(REFERENCE_CRC, (Long) deserializationResultArray[3]);
  }

  @Test
  void deserializeEntryFromLargePayloadSerialization() throws IOException {
    byte[] largePayloadBytes = new byte[LARGE_PAYLOAD_SIZE];
    for (int index = 0; index < largePayloadBytes.length; index++) {
      largePayloadBytes[index] = (byte) (index % 256);
    }

    byte[] serializedEntryBytes =
        EntrySerdes.serializeEntryWithCrc(
            REFERENCE_TIMESTAMP, LARGE_PAYLOAD_SIZE, largePayloadBytes, REFERENCE_CRC);

    Object[] deserializationResultArray = deserializeFromBytes(serializedEntryBytes);

    assertEquals(REFERENCE_TIMESTAMP, (Long) deserializationResultArray[0]);
    assertEquals(LARGE_PAYLOAD_SIZE, (Integer) deserializationResultArray[1]);
    assertArrayEquals(largePayloadBytes, (byte[]) deserializationResultArray[2]);
    assertEquals(REFERENCE_CRC, (Long) deserializationResultArray[3]);
  }

  private Object[] deserializeFromBytes(byte[] serializedEntryBytes) throws IOException {
    ByteArrayInputStream byteArrayInputBuffer = new ByteArrayInputStream(serializedEntryBytes);
    DataInputStream dataInputStreamReader = new DataInputStream(byteArrayInputBuffer);
    return EntrySerdes.deserializeEntry(dataInputStreamReader);
  }
}
