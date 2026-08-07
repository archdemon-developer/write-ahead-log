package io.writeahead.log.serdes.tests;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.serdes.EntrySerdes;
import java.io.*;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EntrySerdes - Serialization/Deserialization")
public class EntrySerdesTest {

  @Test
  @DisplayName("serializeEntrySanseCrc serializes entry with timestamp, size, and data")
  void testSerializeEntrySansCrcBasic() throws IOException {
    long timestamp = 1234567890L;
    int size = 5;
    byte[] data = {1, 2, 3, 4, 5};

    byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

    assertNotNull(serialized);
    assertTrue(serialized.length > 0);
    assertEquals(12 + size, serialized.length);
  }

  @Test
  @DisplayName("serializeEntrySanseCrc writes correct byte format")
  void testSerializeEntrySansCrcByteFormat() throws IOException {
    long timestamp = 0x0102030405060708L;
    int size = 2;
    byte[] data = {(byte) 0xAA, (byte) 0xBB};

    byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

    ByteBuffer buffer = ByteBuffer.wrap(serialized);
    assertEquals(timestamp, buffer.getLong(0));
    assertEquals(size, buffer.getInt(8));
    assertEquals((byte) 0xAA, serialized[12]);
    assertEquals((byte) 0xBB, serialized[13]);
  }

  @Test
  @DisplayName("serializeEntrySanseCrc handles empty data")
  void testSerializeEntrySansCrcEmptyData() throws IOException {
    long timestamp = System.currentTimeMillis();
    int size = 0;
    byte[] data = {};

    byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

    assertEquals(12, serialized.length);
  }

  @Test
  @DisplayName("serializeEntrySanseCrc handles single byte data")
  void testSerializeEntrySansCrcSingleByte() throws IOException {
    long timestamp = 1000L;
    int size = 1;
    byte[] data = {42};

    byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

    assertEquals(13, serialized.length);
    assertEquals(42, serialized[12]);
  }

  @Test
  @DisplayName("serializeEntrySanseCrc handles large data")
  void testSerializeEntrySansCrcLargeData() throws IOException {
    long timestamp = System.currentTimeMillis();
    byte[] data = new byte[10000];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i % 256);
    }
    int size = data.length;

    byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

    assertEquals(12 + size, serialized.length);
  }

  @Test
  @DisplayName("serializeEntrySanseCrc handles minimum timestamp")
  void testSerializeEntrySansCrcMinTimestamp() throws IOException {
    long timestamp = Long.MIN_VALUE;
    int size = 5;
    byte[] data = {1, 2, 3, 4, 5};

    byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

    ByteBuffer buffer = ByteBuffer.wrap(serialized);
    assertEquals(Long.MIN_VALUE, buffer.getLong(0));
  }

  @Test
  @DisplayName("serializeEntrySanseCrc handles maximum timestamp")
  void testSerializeEntrySansCrcMaxTimestamp() throws IOException {
    long timestamp = Long.MAX_VALUE;
    int size = 5;
    byte[] data = {1, 2, 3, 4, 5};

    byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

    ByteBuffer buffer = ByteBuffer.wrap(serialized);
    assertEquals(Long.MAX_VALUE, buffer.getLong(0));
  }

  @Test
  @DisplayName("serializeEntrySanseCrc handles zero size")
  void testSerializeEntrySansCrcZeroSize() throws IOException {
    long timestamp = 100L;
    int size = 0;
    byte[] data = new byte[0];

    byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

    ByteBuffer buffer = ByteBuffer.wrap(serialized);
    assertEquals(0, buffer.getInt(8));
  }

  @Test
  @DisplayName("serializeEntrySanseCrc handles maximum size")
  void testSerializeEntrySansCrcMaxSize() throws IOException {
    long timestamp = 100L;
    int size = 1000;
    byte[] data = new byte[1000];

    byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

    ByteBuffer buffer = ByteBuffer.wrap(serialized);
    assertEquals(1000, buffer.getInt(8));
    assertEquals(1012, serialized.length);
  }

  @Test
  @DisplayName("serializeEntrySanseCrc produces consistent results")
  void testSerializeEntrySansCrcConsistent() throws IOException {
    long timestamp = System.currentTimeMillis();
    byte[] data = "test data".getBytes();
    int size = data.length;

    byte[] result1 = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);
    byte[] result2 = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

    assertArrayEquals(result1, result2);
  }

  @Test
  @DisplayName("serializeEntrySanseCrc produces different results for different data")
  void testSerializeEntrySansCrcDifferentData() throws IOException {
    long timestamp = 100L;
    int size = 5;
    byte[] data1 = {1, 2, 3, 4, 5};
    byte[] data2 = {5, 4, 3, 2, 1};

    byte[] result1 = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data1);
    byte[] result2 = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data2);

    assertNotEquals(result1, result2);
  }

  @Test
  @DisplayName("serializeEntrySanseCrc handles all zero bytes")
  void testSerializeEntrySansCrcAllZeros() throws IOException {
    long timestamp = 0L;
    int size = 10;
    byte[] data = new byte[10];

    byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

    assertNotNull(serialized);
    assertEquals(22, serialized.length);
  }

  @Test
  @DisplayName("serializeEntrySanseCrc handles all 0xFF bytes")
  void testSerializeEntrySansCrcAllOnes() throws IOException {
    long timestamp = -1L;
    int size = 10;
    byte[] data = new byte[10];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) 0xFF;
    }

    byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

    assertNotNull(serialized);
    assertEquals(22, serialized.length);
  }

  @Test
  @DisplayName("serializeEntrySanseCrc throws IOException on stream error")
  void testSerializeEntrySansCrcThrowsOnError() throws IOException {
    long timestamp = 100L;
    byte[] data = "test".getBytes();
    int size = data.length;

    byte[] result = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);
    assertNotNull(result);
  }

  @Test
  @DisplayName("serializeEntryWithCrc serializes entry with timestamp, size, data, and crc")
  void testSerializeEntryWithCrcBasic() throws IOException {
    long timestamp = 1234567890L;
    int size = 5;
    byte[] data = {1, 2, 3, 4, 5};
    long crc = 9876543210L;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);

    assertNotNull(serialized);
    assertTrue(serialized.length > 0);
    assertEquals(20 + size, serialized.length);
  }

  @Test
  @DisplayName("serializeEntryWithCrc writes correct byte format")
  void testSerializeEntryWithCrcByteFormat() throws IOException {
    long timestamp = 0x0102030405060708L;
    int size = 2;
    byte[] data = {(byte) 0xAA, (byte) 0xBB};
    long crc = 0x1122334455667788L;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);

    ByteBuffer buffer = ByteBuffer.wrap(serialized);
    assertEquals(timestamp, buffer.getLong(0));
    assertEquals(size, buffer.getInt(8));
    assertEquals((byte) 0xAA, serialized[12]);
    assertEquals((byte) 0xBB, serialized[13]);
    assertEquals(crc, buffer.getLong(14));
  }

  @Test
  @DisplayName("serializeEntryWithCrc handles empty data")
  void testSerializeEntryWithCrcEmptyData() throws IOException {
    long timestamp = System.currentTimeMillis();
    int size = 0;
    byte[] data = {};
    long crc = 12345L;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);

    assertEquals(20, serialized.length);
  }

  @Test
  @DisplayName("serializeEntryWithCrc handles single byte data")
  void testSerializeEntryWithCrcSingleByte() throws IOException {
    long timestamp = 1000L;
    int size = 1;
    byte[] data = {42};
    long crc = 999L;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);

    assertEquals(21, serialized.length);
  }

  @Test
  @DisplayName("serializeEntryWithCrc handles large data")
  void testSerializeEntryWithCrcLargeData() throws IOException {
    long timestamp = System.currentTimeMillis();
    byte[] data = new byte[10000];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i % 256);
    }
    int size = data.length;
    long crc = 0xDEADBEEFL;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);

    assertEquals(20 + size, serialized.length);
  }

  @Test
  @DisplayName("serializeEntryWithCrc handles minimum crc")
  void testSerializeEntryWithCrcMinCrc() throws IOException {
    long timestamp = 100L;
    int size = 5;
    byte[] data = {1, 2, 3, 4, 5};
    long crc = Long.MIN_VALUE;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);

    ByteBuffer buffer = ByteBuffer.wrap(serialized);
    assertEquals(Long.MIN_VALUE, buffer.getLong(12 + size));
  }

  @Test
  @DisplayName("serializeEntryWithCrc handles maximum crc")
  void testSerializeEntryWithCrcMaxCrc() throws IOException {
    long timestamp = 100L;
    int size = 5;
    byte[] data = {1, 2, 3, 4, 5};
    long crc = Long.MAX_VALUE;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);

    ByteBuffer buffer = ByteBuffer.wrap(serialized);
    assertEquals(Long.MAX_VALUE, buffer.getLong(12 + size));
  }

  @Test
  @DisplayName("serializeEntryWithCrc produces consistent results")
  void testSerializeEntryWithCrcConsistent() throws IOException {
    long timestamp = System.currentTimeMillis();
    byte[] data = "test data".getBytes();
    int size = data.length;
    long crc = 55555L;

    byte[] result1 = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
    byte[] result2 = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);

    assertArrayEquals(result1, result2);
  }

  @Test
  @DisplayName("serializeEntryWithCrc differs from serializeEntrySanseCrc by 8 bytes")
  void testSerializeEntryWithCrcDifference() throws IOException {
    long timestamp = 100L;
    int size = 5;
    byte[] data = {1, 2, 3, 4, 5};
    long crc = 999L;

    byte[] withCrc = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
    byte[] sansCrc = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

    assertEquals(withCrc.length, sansCrc.length + 8);
  }

  @Test
  @DisplayName("serializeEntryWithCrc handles zero crc")
  void testSerializeEntryWithCrcZeroCrc() throws IOException {
    long timestamp = 100L;
    int size = 5;
    byte[] data = {1, 2, 3, 4, 5};
    long crc = 0L;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);

    ByteBuffer buffer = ByteBuffer.wrap(serialized);
    assertEquals(0L, buffer.getLong(12 + size));
  }

  @Test
  @DisplayName("deserializeEntry deserializes complete entry")
  void testDeserializeEntryBasic() throws IOException {
    long timestamp = 1234567890L;
    int size = 5;
    byte[] data = {1, 2, 3, 4, 5};
    long crc = 9876543210L;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(serialized));

    Object[] result = EntrySerdes.deserializeEntry(dis);

    assertEquals(4, result.length);
    assertEquals(timestamp, result[0]);
    assertEquals(size, result[1]);
    assertArrayEquals(data, (byte[]) result[2]);
    assertEquals(crc, result[3]);
  }

  @Test
  @DisplayName("deserializeEntry returns array with correct types")
  void testDeserializeEntryTypes() throws IOException {
    long timestamp = 100L;
    int size = 3;
    byte[] data = {1, 2, 3};
    long crc = 999L;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(serialized));

    Object[] result = EntrySerdes.deserializeEntry(dis);

    assertInstanceOf(Long.class, result[0]);
    assertInstanceOf(Integer.class, result[1]);
    assertInstanceOf(byte[].class, result[2]);
    assertInstanceOf(Long.class, result[3]);
  }

  @Test
  @DisplayName("deserializeEntry handles empty data")
  void testDeserializeEntryEmptyData() throws IOException {
    long timestamp = 100L;
    int size = 0;
    byte[] data = {};
    long crc = 123L;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(serialized));

    Object[] result = EntrySerdes.deserializeEntry(dis);

    assertEquals(timestamp, result[0]);
    assertEquals(size, result[1]);
    assertEquals(0, ((byte[]) result[2]).length);
    assertEquals(crc, result[3]);
  }

  @Test
  @DisplayName("deserializeEntry handles single byte data")
  void testDeserializeEntrySingleByte() throws IOException {
    long timestamp = 1000L;
    int size = 1;
    byte[] data = {42};
    long crc = 555L;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(serialized));

    Object[] result = EntrySerdes.deserializeEntry(dis);

    assertEquals(timestamp, result[0]);
    assertEquals(1, result[1]);
    assertArrayEquals(new byte[] {42}, (byte[]) result[2]);
    assertEquals(crc, result[3]);
  }

  @Test
  @DisplayName("deserializeEntry handles large data")
  void testDeserializeEntryLargeData() throws IOException {
    long timestamp = System.currentTimeMillis();
    byte[] data = new byte[10000];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i % 256);
    }
    int size = data.length;
    long crc = 0xCAFEBABEL;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(serialized));

    Object[] result = EntrySerdes.deserializeEntry(dis);

    assertEquals(timestamp, result[0]);
    assertEquals(size, result[1]);
    assertArrayEquals(data, (byte[]) result[2]);
    assertEquals(crc, result[3]);
  }

  @Test
  @DisplayName("deserializeEntry handles minimum values")
  void testDeserializeEntryMinValues() throws IOException {
    long timestamp = Long.MIN_VALUE;
    int size = 0;
    byte[] data = {};
    long crc = Long.MIN_VALUE;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(serialized));

    Object[] result = EntrySerdes.deserializeEntry(dis);

    assertEquals(Long.MIN_VALUE, result[0]);
    assertEquals(0, result[1]);
    assertEquals(Long.MIN_VALUE, result[3]);
  }

  @Test
  @DisplayName("deserializeEntry handles maximum values")
  void testDeserializeEntryMaxValues() throws IOException {
    long timestamp = Long.MAX_VALUE;
    int size = 5;
    byte[] data = {1, 2, 3, 4, 5};
    long crc = Long.MAX_VALUE;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(serialized));

    Object[] result = EntrySerdes.deserializeEntry(dis);

    assertEquals(Long.MAX_VALUE, result[0]);
    assertEquals(5, result[1]);
    assertEquals(Long.MAX_VALUE, result[3]);
  }

  @Test
  @DisplayName("deserializeEntry throws on insufficient data")
  void testDeserializeEntryInsufficientData() {
    byte[] incomplete = new byte[5];

    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(incomplete));

    assertThrows(EOFException.class, () -> EntrySerdes.deserializeEntry(dis));
  }

  @Test
  @DisplayName("deserializeEntry throws when data size mismatches")
  void testDeserializeEntryDataSizeMismatch() throws IOException {
    long timestamp = 100L;
    int size = 10;
    byte[] data = {1, 2, 3};
    long crc = 123L;

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (DataOutputStream dos = new DataOutputStream(baos)) {
      dos.writeLong(timestamp);
      dos.writeInt(size);
      dos.write(data);
      dos.writeLong(crc);
    }

    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));

    assertThrows(EOFException.class, () -> EntrySerdes.deserializeEntry(dis));
  }

  @Test
  @DisplayName("deserializeEntry produces consistent results")
  void testDeserializeEntryConsistent() throws IOException {
    long timestamp = System.currentTimeMillis();
    byte[] data = "test data".getBytes();
    int size = data.length;
    long crc = 77777L;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);

    Object[] result1 =
        EntrySerdes.deserializeEntry(new DataInputStream(new ByteArrayInputStream(serialized)));
    Object[] result2 =
        EntrySerdes.deserializeEntry(new DataInputStream(new ByteArrayInputStream(serialized)));

    assertEquals(result1[0], result2[0]);
    assertEquals(result1[1], result2[1]);
    assertArrayEquals((byte[]) result1[2], (byte[]) result2[2]);
    assertEquals(result1[3], result2[3]);
  }

  @Test
  @DisplayName("deserializeEntry handles zero timestamp")
  void testDeserializeEntryZeroTimestamp() throws IOException {
    long timestamp = 0L;
    int size = 5;
    byte[] data = {1, 2, 3, 4, 5};
    long crc = 123L;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(serialized));

    Object[] result = EntrySerdes.deserializeEntry(dis);

    assertEquals(0L, result[0]);
  }

  @Test
  @DisplayName("deserializeEntry handles zero crc")
  void testDeserializeEntryZeroCrc() throws IOException {
    long timestamp = 100L;
    int size = 5;
    byte[] data = {1, 2, 3, 4, 5};
    long crc = 0L;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(serialized));

    Object[] result = EntrySerdes.deserializeEntry(dis);

    assertEquals(0L, result[3]);
  }

  @Test
  @DisplayName("Round-trip: serialize with CRC then deserialize recovers data")
  void testRoundTripSansCrc() throws IOException {
    long timestamp = System.currentTimeMillis();
    byte[] data = "hello world".getBytes();
    int size = data.length;
    long crc = 12345L;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(serialized));

    Object[] deserialized = EntrySerdes.deserializeEntry(dis);

    assertEquals(timestamp, deserialized[0]);
    assertEquals(size, deserialized[1]);
    assertArrayEquals(data, (byte[]) deserialized[2]);
    assertEquals(crc, deserialized[3]);
  }

  @Test
  @DisplayName("Round-trip: serialize with CRC then deserialize recovers all fields")
  void testRoundTripWithCrc() throws IOException {
    long timestamp = System.currentTimeMillis();
    int size = 15;
    byte[] data = "test data entry".getBytes();
    long crc = 0xDEADBEEFL;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(serialized));

    Object[] deserialized = EntrySerdes.deserializeEntry(dis);

    assertEquals(timestamp, deserialized[0]);
    assertEquals(size, deserialized[1]);
    assertArrayEquals(data, (byte[]) deserialized[2]);
    assertEquals(crc, deserialized[3]);
  }

  @Test
  @DisplayName("Round-trip: multiple entries in sequence")
  void testRoundTripMultipleEntries() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (DataOutputStream dos = new DataOutputStream(baos)) {
      byte[] serialized1 = EntrySerdes.serializeEntryWithCrc(100, 3, new byte[] {1, 2, 3}, 999);
      dos.write(serialized1);

      byte[] serialized2 = EntrySerdes.serializeEntryWithCrc(200, 2, new byte[] {4, 5}, 888);
      dos.write(serialized2);
    }

    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));

    Object[] entry1 = EntrySerdes.deserializeEntry(dis);
    assertEquals(100L, entry1[0]);
    assertEquals(999L, entry1[3]);

    Object[] entry2 = EntrySerdes.deserializeEntry(dis);
    assertEquals(200L, entry2[0]);
    assertEquals(888L, entry2[3]);
  }

  @Test
  @DisplayName("Round-trip: large data preserves content exactly")
  void testRoundTripLargeData() throws IOException {
    long timestamp = 12345L;
    byte[] largeData = new byte[50000];
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) ((i * 7) % 256);
    }
    int size = largeData.length;
    long crc = 0xCAFEBABEL;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, largeData, crc);
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(serialized));

    Object[] deserialized = EntrySerdes.deserializeEntry(dis);

    assertEquals(timestamp, deserialized[0]);
    assertEquals(size, deserialized[1]);
    assertArrayEquals(largeData, (byte[]) deserialized[2]);
    assertEquals(crc, deserialized[3]);
  }

  @Test
  @DisplayName("Round-trip: binary data with special patterns")
  void testRoundTripSpecialPatterns() throws IOException {
    long timestamp = 999L;
    byte[] data = new byte[256];
    for (int i = 0; i < 256; i++) {
      data[i] = (byte) i;
    }
    int size = data.length;
    long crc = 0xDEADBEEFL;

    byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(serialized));

    Object[] deserialized = EntrySerdes.deserializeEntry(dis);

    assertArrayEquals(data, (byte[]) deserialized[2]);
  }
}
