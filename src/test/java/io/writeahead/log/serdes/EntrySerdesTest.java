package io.writeahead.log.serdes;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * MILITARY-GRADE SERIALIZATION TESTS FOR EntrySerdes
 *
 * <p>Serialization/deserialization is foundational. If wrong:
 * - Data loss on recovery
 * - Corruption on read
 * - Silent data modification
 *
 * <p>These tests verify round-trip integrity for production reliability.
 */
public class EntrySerdesTest {

    @Test
    void testSerializeEntrySanseCrcSimple() throws IOException {
        long timestamp = 1000L;
        int size = 5;
        byte[] data = "hello".getBytes();

        byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

        // VERIFY: Serialized bytes contain timestamp + size + data
        // 8 bytes (timestamp) + 4 bytes (size) + 5 bytes (data) = 17 bytes
        assertEquals(17, serialized.length, "Serialized size incorrect (8+4+5=17 bytes)");
    }

    @Test
    void testSerializeEntrySanseCrcEmptyData() throws IOException {
        long timestamp = 1000L;
        int size = 0;
        byte[] data = new byte[0];

        byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

        // 8 bytes (timestamp) + 4 bytes (size) + 0 bytes (data) = 12 bytes
        assertEquals(12, serialized.length, "Empty data serialization incorrect (8+4=12 bytes)");
    }

    @Test
    void testSerializeEntrySanseCrcLargeData() throws IOException {
        long timestamp = 1000L;
        byte[] largeData = new byte[1000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }
        int size = largeData.length;

        byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, largeData);

        // 8 + 4 + 1000 = 1012 bytes
        assertEquals(1012, serialized.length, "Large data serialization incorrect (8+4+1000=1012 bytes)");
    }

    @Test
    void testSerializeEntryWithCrcAddsEightBytes() throws IOException {
        long timestamp = 1000L;
        int size = 5;
        byte[] data = "hello".getBytes();
        long crc = 12345L;

        byte[] sanseCrc = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);
        byte[] withCrc = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);

        // serializeEntryWithCrc should have 8 more bytes (the CRC)
        assertEquals(
                sanseCrc.length + 8,
                withCrc.length,
                "serializeEntryWithCrc should be 8 bytes larger (8-byte CRC)");
    }

    @Test
    void testDeserializeEntryRoundTrip() throws IOException {
        long timestamp = 1000L;
        int size = 5;
        byte[] data = "hello".getBytes();
        long crc = 12345L;

        // Serialize WITH CRC
        byte[] serialized = EntrySerdes.serializeEntryWithCrc(timestamp, size, data, crc);

        // Deserialize
        ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
        DataInputStream dis = new DataInputStream(bais);
        Object[] result = EntrySerdes.deserializeEntry(dis);

        // VERIFY: Deserialization returns correct tuple
        assertEquals(4, result.length, "Should return 4-element array [timestamp, size, data, crc]");
        assertEquals(timestamp, (Long) result[0], "Timestamp mismatch");
        assertEquals(size, (Integer) result[1], "Size mismatch");
        assertArrayEquals(data, (byte[]) result[2], "Data mismatch");
        assertEquals(crc, (Long) result[3], "CRC mismatch");
    }

    @Test
    void testDeserializeMultipleEntriesSequential() throws IOException {
        long[] timestamps = {1000L, 2000L, 3000L};
        byte[][] datas = {"entry1".getBytes(), "entry2".getBytes(), "entry3".getBytes()};
        long[] crcs = {111L, 222L, 333L};

        // Serialize all entries to bytes
        byte[] allBytes = new byte[0];
        for (int i = 0; i < 3; i++) {
            byte[] entryBytes = EntrySerdes.serializeEntryWithCrc(
                    timestamps[i], datas[i].length, datas[i], crcs[i]);
            byte[] combined = new byte[allBytes.length + entryBytes.length];
            System.arraycopy(allBytes, 0, combined, 0, allBytes.length);
            System.arraycopy(entryBytes, 0, combined, allBytes.length, entryBytes.length);
            allBytes = combined;
        }

        // Deserialize all entries from stream
        ByteArrayInputStream bais = new ByteArrayInputStream(allBytes);
        DataInputStream dis = new DataInputStream(bais);

        for (int i = 0; i < 3; i++) {
            Object[] result = EntrySerdes.deserializeEntry(dis);
            assertEquals(timestamps[i], (Long) result[0], "Entry " + i + " timestamp mismatch");
            assertEquals(datas[i].length, (Integer) result[1], "Entry " + i + " size mismatch");
            assertArrayEquals(datas[i], (byte[]) result[2], "Entry " + i + " data mismatch");
            assertEquals(crcs[i], (Long) result[3], "Entry " + i + " CRC mismatch");
        }
    }

    @Test
    void testSerializationDeterministic() throws IOException {
        long timestamp = 1000L;
        int size = 5;
        byte[] data = "hello".getBytes();

        byte[] bytes1 = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);
        byte[] bytes2 = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);
        byte[] bytes3 = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);

        // All serializations must be identical
        assertArrayEquals(bytes1, bytes2, "Serialization not deterministic (1 vs 2)");
        assertArrayEquals(bytes2, bytes3, "Serialization not deterministic (2 vs 3)");
    }

    @Test
    void testSerializeExtremeTimestamps() throws IOException {
        long[] timestamps = {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE};

        for (long ts : timestamps) {
            byte[] data = "test".getBytes();
            byte[] serialized = EntrySerdes.serializeEntrySanseCrc(ts, data.length, data);

            // Should deserialize correctly
            ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
            DataInputStream dis = new DataInputStream(bais);

            // Read manually to verify
            long readTs = dis.readLong();
            assertEquals(ts, readTs, "Timestamp " + ts + " not preserved exactly");
        }
    }

    @Test
    void testSerializeExtremeSize() throws IOException {
        int[] sizes = {0, 1, 255, 256, 65535, 1000000};

        for (int size : sizes) {
            byte[] data = new byte[Math.min(size, 1000)]; // Don't actually allocate huge arrays
            byte[] serialized = EntrySerdes.serializeEntrySanseCrc(1000L, size, data.length > 0 ? data : new byte[0]);

            ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
            DataInputStream dis = new DataInputStream(bais);

            long ts = dis.readLong();
            int readSize = dis.readInt();
            assertEquals(size, readSize, "Size " + size + " not preserved exactly");
        }
    }

    @Test
    void testDeserializeWithoutCrcThrowsException() throws IOException {
        // Serialize WITHOUT CRC (only 8+4+5=17 bytes, no 8-byte CRC at end)
        byte[] serialized = EntrySerdes.serializeEntrySanseCrc(1000L, 5, "hello".getBytes());

        ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
        DataInputStream dis = new DataInputStream(bais);

        // Deserialize expects to read CRC, but stream ends
        assertThrows(
                IOException.class,
                () -> EntrySerdes.deserializeEntry(dis),
                "Should throw IOException when trying to read CRC from incomplete stream");
    }

    @Test
    void testSerializePreservesDataBytes() throws IOException {
        byte[] originalData = "hello".getBytes();
        byte[] dataCopy = originalData.clone();

        byte[] serialized = EntrySerdes.serializeEntrySanseCrc(1000L, originalData.length, originalData);

        // Verify original array not modified
        assertArrayEquals(dataCopy, originalData, "Serialization modified input data array");
    }

    @Test
    void testSerializedBytesContainCorrectValues() throws IOException {
        byte[] data = "AB".getBytes(); // 2 bytes: 0x41, 0x42
        byte[] serialized = EntrySerdes.serializeEntrySanseCrc(1000L, 2, data);

        // Verify structure: 8 bytes TS + 4 bytes size + 2 bytes data = 14 bytes total
        assertEquals(14, serialized.length, "Total length should be 14");

        // Read back manually
        ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
        DataInputStream dis = new DataInputStream(bais);

        long ts = dis.readLong();
        int size = dis.readInt();
        byte[] readData = new byte[size];
        dis.readFully(readData);

        assertEquals(1000L, ts, "Timestamp incorrect");
        assertEquals(2, size, "Size incorrect");
        assertArrayEquals(data, readData, "Data incorrect");
    }
}