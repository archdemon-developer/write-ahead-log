package io.writeahead.log.utils;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.serdes.EntrySerdes;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * MILITARY-GRADE CRC TESTS FOR Crc32Utils
 *
 * <p>CRC corruption detection is foundational. If CRC is wrong:
 * - Silent data corruption possible
 * - Corrupted entries not detected on recovery
 * - System continues with bad data
 *
 * <p>These tests verify CRC correctness for production reliability.
 */
public class Crc32UtilsTest {

    @Test
    void testComputeSimple() {
        byte[] data = "hello".getBytes();

        long crc = Crc32Utils.compute(data);

        // CRC should be non-zero for non-empty data
        assertNotEquals(0, crc, "CRC should not be zero");
        assertTrue(crc > 0, "CRC should be positive");
    }

    @Test
    void testComputeDeterministic() {
        byte[] data = "hello".getBytes();

        long crc1 = Crc32Utils.compute(data);
        long crc2 = Crc32Utils.compute(data);
        long crc3 = Crc32Utils.compute(data);

        // CRC must be deterministic: same input = same output every time
        assertEquals(crc1, crc2, "CRC must be deterministic (same data = same CRC)");
        assertEquals(crc2, crc3, "CRC must be deterministic (consistent across calls)");
    }

    @Test
    void testComputeDifferentData() {
        byte[] data1 = "hello".getBytes();
        byte[] data2 = "world".getBytes();
        byte[] data3 = "hallo".getBytes(); // Changed one byte

        long crc1 = Crc32Utils.compute(data1);
        long crc2 = Crc32Utils.compute(data2);
        long crc3 = Crc32Utils.compute(data3);

        // Different data must produce different CRC
        assertNotEquals(crc1, crc2, "Different data must produce different CRC");
        assertNotEquals(crc1, crc3, "Single byte change must produce different CRC");
    }

    @Test
    void testComputeSensitiveToSingleByte() {
        byte[] data1 = "hello".getBytes();
        byte[] data2 = "hello".getBytes();
        data2[0] = (byte) ~data2[0]; // Flip all bits in first byte

        long crc1 = Crc32Utils.compute(data1);
        long crc2 = Crc32Utils.compute(data2);

        assertNotEquals(crc1, crc2, "CRC must detect single byte change (corruption detection)");
    }

    @Test
    void testComputeEmptyData() {
        byte[] emptyData = new byte[0];

        long crc = Crc32Utils.compute(emptyData);

        // Empty data has specific CRC (typically 0 for CRC32)
        assertNotNull(crc, "CRC of empty data should be valid");
        // Don't assume it's zero, just verify it's consistent
        long crc2 = Crc32Utils.compute(emptyData);
        assertEquals(crc, crc2, "CRC of empty data must be deterministic");
    }

    @Test
    void testComputeLargeData() {
        byte[] largeData = new byte[10000]; // 10KB
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        long crc = Crc32Utils.compute(largeData);
        long crc2 = Crc32Utils.compute(largeData);

        // Must handle large data without crashing
        assertEquals(crc, crc2, "Large data CRC must be deterministic");
        assertNotEquals(0, crc, "Large data CRC should not be zero");
    }

    @Test
    void testComputeNullThrowsNpe() {
        assertThrows(
                NullPointerException.class,
                () -> Crc32Utils.compute(null),
                "Should throw NullPointerException for null data");
    }

    @Test
    void testComputeAllZeroBytes() {
        byte[] allZeros = new byte[100];
        // All zeros in array

        long crc = Crc32Utils.compute(allZeros);

        // All zeros should have consistent, non-zero CRC (depends on implementation)
        long crc2 = Crc32Utils.compute(allZeros);
        assertEquals(crc, crc2, "All-zero data CRC must be deterministic");
    }

    @Test
    void testComputeAllOneBytes() {
        byte[] allOnes = new byte[100];
        for (int i = 0; i < allOnes.length; i++) {
            allOnes[i] = (byte) 0xFF;
        }

        long crc = Crc32Utils.compute(allOnes);
        long crc2 = Crc32Utils.compute(allOnes);

        assertEquals(crc, crc2, "All-ones data CRC must be deterministic");
    }

    @Test
    void testComputeByteOrderMatters() {
        byte[] data1 = {1, 2, 3, 4, 5};
        byte[] data2 = {5, 4, 3, 2, 1};

        long crc1 = Crc32Utils.compute(data1);
        long crc2 = Crc32Utils.compute(data2);

        // Different byte order must produce different CRC
        assertNotEquals(crc1, crc2, "Byte order affects CRC (detects reordering)");
    }

    @Test
    void testComputeNoAccidentalCollisions() {
        // Generate 100 different byte arrays, all should have different CRCs
        long[] crcs = new long[100];
        for (int i = 0; i < 100; i++) {
            byte[] data = new byte[4];
            data[0] = (byte) ((i >>> 24) & 0xFF);
            data[1] = (byte) ((i >>> 16) & 0xFF);
            data[2] = (byte) ((i >>> 8) & 0xFF);
            data[3] = (byte) (i & 0xFF);
            crcs[i] = Crc32Utils.compute(data);
        }

        // Check all unique (or mostly unique - CRC32 is 32-bit so collisions possible but rare)
        java.util.Set<Long> uniqueCrcs = new java.util.HashSet<>();
        for (long crc : crcs) {
            uniqueCrcs.add(crc);
        }

        // Should have at least 95 unique out of 100 (CRC32 is 32-bit, collisions possible)
        assertTrue(uniqueCrcs.size() >= 95, "CRC should mostly avoid collisions (got " + uniqueCrcs.size() + "/100)");
    }

    @Test
    void testComputeEntryCrcSimple() throws IOException {
        long timestamp = 1000L;
        int size = 5;
        byte[] data = "hello".getBytes();

        long crc = Crc32Utils.computeEntryCrc(timestamp, size, data);

        // Should compute CRC of serialized entry
        assertNotEquals(0, crc, "Entry CRC should not be zero");
        assertTrue(crc > 0, "Entry CRC should be positive");
    }

    @Test
    void testComputeEntryCrcDeterministic() throws IOException {
        long timestamp = 1000L;
        int size = 5;
        byte[] data = "hello".getBytes();

        long crc1 = Crc32Utils.computeEntryCrc(timestamp, size, data);
        long crc2 = Crc32Utils.computeEntryCrc(timestamp, size, data);
        long crc3 = Crc32Utils.computeEntryCrc(timestamp, size, data);

        // CRC must be deterministic
        assertEquals(crc1, crc2, "Entry CRC must be deterministic");
        assertEquals(crc2, crc3, "Entry CRC must be consistent");
    }

    @Test
    void testComputeEntryCrcSensitiveToTimestamp() throws IOException {
        int size = 5;
        byte[] data = "hello".getBytes();

        long crc1 = Crc32Utils.computeEntryCrc(1000L, size, data);
        long crc2 = Crc32Utils.computeEntryCrc(1001L, size, data);
        long crc3 = Crc32Utils.computeEntryCrc(2000L, size, data);

        // Different timestamps must produce different CRCs
        assertNotEquals(crc1, crc2, "Different timestamp should produce different CRC");
        assertNotEquals(crc1, crc3, "Different timestamp should produce different CRC");
    }

    @Test
    void testComputeEntryCrcSensitiveToData() throws IOException {
        long timestamp = 1000L;
        int size1 = 5;
        byte[] data1 = "hello".getBytes();
        int size2 = 5;
        byte[] data2 = "hallo".getBytes();

        long crc1 = Crc32Utils.computeEntryCrc(timestamp, size1, data1);
        long crc2 = Crc32Utils.computeEntryCrc(timestamp, size2, data2);

        // Different data must produce different CRC
        assertNotEquals(crc1, crc2, "Different data should produce different CRC");
    }

    @Test
    void testComputeEntryCrcSensitiveToSize() throws IOException {
        long timestamp = 1000L;
        byte[] data = "hello".getBytes();

        long crc1 = Crc32Utils.computeEntryCrc(timestamp, 5, data);
        long crc2 = Crc32Utils.computeEntryCrc(timestamp, 6, data); // Lie about size

        // Different size should produce different CRC
        assertNotEquals(crc1, crc2, "Different size should produce different CRC");
    }

    @Test
    void testComputeEntryCrcEmptyData() throws IOException {
        long timestamp = 1000L;
        int size = 0;
        byte[] data = new byte[0];

        long crc = Crc32Utils.computeEntryCrc(timestamp, size, data);

        // Should handle empty data
        assertNotNull(crc, "CRC of empty entry should be valid");
        long crc2 = Crc32Utils.computeEntryCrc(timestamp, size, data);
        assertEquals(crc, crc2, "Empty entry CRC must be deterministic");
    }

    @Test
    void testComputeEntryCrcLargeData() throws IOException {
        long timestamp = 1000L;
        byte[] largeData = new byte[1000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }
        int size = largeData.length;

        long crc = Crc32Utils.computeEntryCrc(timestamp, size, largeData);

        // Should handle large data
        assertNotNull(crc, "CRC of large entry should be valid");
        assertNotEquals(0, crc, "CRC of large entry should not be zero");
    }

    @Test
    void testComputeEntryCrcExtremeTimestamps() throws IOException {
        int size = 5;
        byte[] data = "hello".getBytes();

        long[] timestamps = {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE};
        long[] crcs = new long[timestamps.length];

        for (int i = 0; i < timestamps.length; i++) {
            crcs[i] = Crc32Utils.computeEntryCrc(timestamps[i], size, data);
        }

        // All different timestamps should produce different CRCs
        java.util.Set<Long> uniqueCrcs = new java.util.HashSet<>();
        for (long crc : crcs) {
            uniqueCrcs.add(crc);
        }

        // Should have all 5 unique (or very likely)
        assertTrue(uniqueCrcs.size() >= 4, "Extreme timestamps should produce mostly different CRCs");
    }

    @Test
    void testComputeMatchesSerializedCrc() throws IOException {
        long timestamp = 1000L;
        int size = 5;
        byte[] data = "hello".getBytes();

        // CRC of entry should match CRC of serialized entry
        byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);
        long crcDirect = Crc32Utils.compute(serialized);
        long crcEntry = Crc32Utils.computeEntryCrc(timestamp, size, data);

        assertEquals(
                crcDirect,
                crcEntry,
                "computeEntryCrc should match compute() of serialized entry");
    }

    @Test
    void testComputeEntryCrcNotZero() throws IOException {
        long timestamp = 1000L;
        int size = 4;
        byte[] data = "test".getBytes();

        long crc = Crc32Utils.computeEntryCrc(timestamp, size, data);

        // CRC of 0 would be suspicious (might indicate uninitialized)
        assertNotEquals(0, crc, "Entry CRC should not be 0 (suspicious value)");
    }
}