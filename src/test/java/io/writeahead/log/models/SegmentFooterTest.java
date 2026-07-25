package io.writeahead.log.models;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.constants.WalConstants;
import io.writeahead.log.models.segment.SegmentFooter;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class SegmentFooterTest {

    @Test
    void testCreateFooter() throws Exception {
        int entryCount = 1000;
        long minTs = 1000L;
        long maxTs = 5000L;

        SegmentFooter footer = SegmentFooter.create(entryCount, minTs, maxTs);

        assertEquals(entryCount, footer.entryCount(), "Entry count should match");
        assertEquals(minTs, footer.minTimestamp(), "Min timestamp should match");
        assertEquals(maxTs, footer.maxTimestamp(), "Max timestamp should match");
        assertTrue(footer.isValid(), "Footer should be valid");
    }

    @Test
    void testSerializationRoundTrip() throws Exception {
        int entryCount = 500;
        long minTs = 2000L;
        long maxTs = 8000L;

        SegmentFooter original = SegmentFooter.create(entryCount, minTs, maxTs);
        byte[] bytes = original.toBytes();

        assertEquals(WalConstants.SEGMENT_FOOTER_SIZE, bytes.length, "Footer should be 36 bytes");

        SegmentFooter deserialized = SegmentFooter.fromBytes(bytes);

        assertEquals(original.entryCount(), deserialized.entryCount(), "Entry count should match");
        assertEquals(original.minTimestamp(), deserialized.minTimestamp(), "Min timestamp should match");
        assertEquals(original.maxTimestamp(), deserialized.maxTimestamp(), "Max timestamp should match");
        assertEquals(original.checksum(), deserialized.checksum(), "Checksum should match");
        assertTrue(deserialized.isValid(), "Deserialized footer should be valid");
    }

    @Test
    void testCorruptedCompleteMarker() throws Exception {
        SegmentFooter footer = SegmentFooter.create(100, 1000L, 2000L);
        byte[] bytes = footer.toBytes();

        // Corrupt complete marker (bytes 20-27)
        bytes[20] = (byte) ~bytes[20];

        SegmentFooter corrupted = SegmentFooter.fromBytes(bytes);
        assertFalse(corrupted.isValid(), "Footer with corrupted marker should fail checksum");
    }

    @Test
    void testChecksumValidation() throws Exception {
        SegmentFooter footer = SegmentFooter.create(100, 1000L, 2000L);
        byte[] bytes = footer.toBytes();

        // Corrupt checksum (last 8 bytes)
        bytes[27] = (byte) ~bytes[27];

        SegmentFooter corrupted = SegmentFooter.fromBytes(bytes);
        assertFalse(corrupted.isValid(), "Footer with corrupted checksum should be invalid");
    }

    @Test
    void testZeroEntries() throws Exception {
        SegmentFooter footer = SegmentFooter.create(0, 0L, 0L);
        byte[] bytes = footer.toBytes();
        SegmentFooter deserialized = SegmentFooter.fromBytes(bytes);

        assertEquals(0, deserialized.entryCount(), "Zero entries should be valid");
        assertTrue(deserialized.isValid(), "Footer with 0 entries should be valid");
    }

    @Test
    void testLargeEntryCount() throws Exception {
        int largeCount = Integer.MAX_VALUE;
        SegmentFooter footer = SegmentFooter.create(largeCount, 1000L, 2000L);
        byte[] bytes = footer.toBytes();
        SegmentFooter deserialized = SegmentFooter.fromBytes(bytes);

        assertEquals(largeCount, deserialized.entryCount(), "Large entry count should be preserved");
        assertTrue(deserialized.isValid(), "Footer with large count should be valid");
    }

    @Test
    void testSameMinMaxTimestamp() throws Exception {
        long ts = 5000L;
        SegmentFooter footer = SegmentFooter.create(100, ts, ts);
        byte[] bytes = footer.toBytes();
        SegmentFooter deserialized = SegmentFooter.fromBytes(bytes);

        assertEquals(ts, deserialized.minTimestamp(), "Min timestamp should match");
        assertEquals(ts, deserialized.maxTimestamp(), "Max timestamp should match");
        assertTrue(deserialized.isValid(), "Footer with same min/max should be valid");
    }

    @Test
    void testCorruptedEntryCount() throws Exception {
        SegmentFooter footer = SegmentFooter.create(1000, 1000L, 2000L);
        byte[] bytes = footer.toBytes();

        // Corrupt entry count (bytes 0-3)
        bytes[2] = (byte) ~bytes[2];

        SegmentFooter corrupted = SegmentFooter.fromBytes(bytes);
        assertFalse(corrupted.isValid(), "Footer with corrupted entry count should fail checksum");
    }

    @Test
    void testCorruptedMinTimestamp() throws Exception {
        SegmentFooter footer = SegmentFooter.create(100, 1000L, 2000L);
        byte[] bytes = footer.toBytes();

        // Corrupt min timestamp (bytes 4-11)
        bytes[7] = (byte) ~bytes[7];

        SegmentFooter corrupted = SegmentFooter.fromBytes(bytes);
        assertFalse(corrupted.isValid(), "Footer with corrupted min timestamp should fail checksum");
    }

    @Test
    void testCorruptedMaxTimestamp() throws Exception {
        SegmentFooter footer = SegmentFooter.create(100, 1000L, 2000L);
        byte[] bytes = footer.toBytes();

        // Corrupt max timestamp (bytes 12-19)
        bytes[15] = (byte) ~bytes[15];

        SegmentFooter corrupted = SegmentFooter.fromBytes(bytes);
        assertFalse(corrupted.isValid(), "Footer with corrupted max timestamp should fail checksum");
    }

    @Test
    void testFooterTooSmall() {
        byte[] tooSmall = new byte[10];
        assertThrows(IOException.class, () -> SegmentFooter.fromBytes(tooSmall),
                "Should throw IOException for data smaller than footer size");
    }

    @Test
    void testToString() throws Exception {
        SegmentFooter footer = SegmentFooter.create(500, 1000L, 5000L);
        String str = footer.toString();

        assertTrue(str.contains("entryCount"), "toString should contain entryCount");
        assertTrue(str.contains("minTimestamp"), "toString should contain minTimestamp");
        assertTrue(str.contains("maxTimestamp"), "toString should contain maxTimestamp");
        assertTrue(str.contains("valid"), "toString should contain validation status");
    }

    @Test
    void testCompleteMarkerSerialization() throws IOException {
        SegmentFooter footer = SegmentFooter.create(10, 1000L, 5000L);

        byte[] bytes = footer.toBytes();

        // Should be 36 bytes: entryCount(4) + minTs(8) + maxTs(8) + marker(8) + checksum(8)
        assertEquals(36, bytes.length, "Footer should be 36 bytes with completeMarker");
    }

    @Test
    void testCompleteMarkerDeserialization() throws IOException {
        SegmentFooter original = SegmentFooter.create(5, 2000L, 8000L);
        byte[] bytes = original.toBytes();

        SegmentFooter deserialized = SegmentFooter.fromBytes(bytes);

        assertEquals(original.entryCount(), deserialized.entryCount());
        assertEquals(original.minTimestamp(), deserialized.minTimestamp());
        assertEquals(original.maxTimestamp(), deserialized.maxTimestamp());
        assertEquals(0xDEADBEEFL, deserialized.completeMarker(),
                "completeMarker should be 0xDEADBEEF");
    }

    @Test
    void testCompleteMarkerRoundTrip() throws IOException {
        SegmentFooter footer = SegmentFooter.create(100, 1500L, 9500L);

        byte[] serialized = footer.toBytes();
        SegmentFooter deserialized = SegmentFooter.fromBytes(serialized);
        byte[] reserialized = deserialized.toBytes();

        // Bytes should match exactly
        assertArrayEquals(serialized, reserialized,
                "Round-trip serialization should produce identical bytes");
    }

    @Test
    void testCompleteMarkerInChecksum() throws IOException {
        SegmentFooter footer1 = SegmentFooter.create(5, 1000L, 5000L);

        // Manually create a footer with different marker (shouldn't happen, but test it)
        // The checksum is computed with the marker, so if marker changes, checksum should differ
        byte[] bytes = footer1.toBytes();

        // Corrupt the marker bytes (positions 20-27, after entry count + timestamps)
        // This will break the checksum
        bytes[20] = (byte) (bytes[20] ^ 0xFF);  // Flip bits in marker

        SegmentFooter corrupted = SegmentFooter.fromBytes(bytes);

        assertFalse(corrupted.isValid(),
                "Footer with corrupted marker should fail checksum validation");
    }

    @Test
    void testCompleteMarkerValidation() throws IOException {
        SegmentFooter footer = SegmentFooter.create(15, 3000L, 7000L);

        assertTrue(footer.isValid(), "Valid footer should pass validation");

        // Verify the marker is what we expect
        assertEquals(0xDEADBEEFL, footer.completeMarker(),
                "completeMarker should be 0xDEADBEEF");
    }

    @Test
    void testCompleteMarkerConsistentAcrossCreates() throws IOException {
        SegmentFooter footer1 = SegmentFooter.create(5, 1000L, 5000L);
        SegmentFooter footer2 = SegmentFooter.create(10, 2000L, 8000L);
        SegmentFooter footer3 = SegmentFooter.create(100, 5000L, 10000L);

        // All footers should have the same marker
        assertEquals(footer1.completeMarker(), footer2.completeMarker());
        assertEquals(footer2.completeMarker(), footer3.completeMarker());

        // All should be the magic value
        assertEquals(0xDEADBEEFL, footer1.completeMarker());
    }

    @Test
    void testCompleteMarkerSurvivesSerializationRoundTrip() throws IOException {
        long originalMarker = 0xDEADBEEFL;

        SegmentFooter footer = SegmentFooter.create(20, 1500L, 9500L);

        byte[] bytes = footer.toBytes();
        SegmentFooter deserialized = SegmentFooter.fromBytes(bytes);

        assertEquals(originalMarker, deserialized.completeMarker(),
                "completeMarker should survive serialization/deserialization");
    }

    @Test
    void testCorruptedCompleteMarkerFails() throws IOException {
        SegmentFooter validFooter = SegmentFooter.create(5, 1000L, 5000L);
        byte[] bytes = validFooter.toBytes();

        // Corrupt marker field (8 bytes at position 20)
        bytes[20] = 0x00;
        bytes[21] = 0x00;
        bytes[22] = 0x00;
        bytes[23] = 0x00;
        bytes[24] = 0x00;
        bytes[25] = 0x00;
        bytes[26] = 0x00;
        bytes[27] = 0x00;

        SegmentFooter corrupted = SegmentFooter.fromBytes(bytes);

        assertFalse(corrupted.isValid(),
                "Footer with corrupted marker should fail checksum validation");
        assertNotEquals(0xDEADBEEFL, corrupted.completeMarker(),
                "Corrupted marker should not equal expected value");
    }

    @Test
    void testCompleteMarkerByteOrder() throws IOException {
        SegmentFooter footer = SegmentFooter.create(1, 100L, 200L);
        byte[] bytes = footer.toBytes();

        // Deserialize and verify marker comes back correctly
        SegmentFooter deserialized = SegmentFooter.fromBytes(bytes);

        assertEquals(0xDEADBEEFL, deserialized.completeMarker(),
                "Marker should deserialize correctly (byte order validated)");
        assertTrue(deserialized.isValid(), "Valid footer should pass checksum");
    }

    @Test
    void testMultipleFooters() throws Exception {
        SegmentFooter f1 = SegmentFooter.create(100, 1000L, 2000L);
        SegmentFooter f2 = SegmentFooter.create(200, 2000L, 3000L);
        SegmentFooter f3 = SegmentFooter.create(300, 3000L, 4000L);

        byte[] b1 = f1.toBytes();
        byte[] b2 = f2.toBytes();
        byte[] b3 = f3.toBytes();

        SegmentFooter d1 = SegmentFooter.fromBytes(b1);
        SegmentFooter d2 = SegmentFooter.fromBytes(b2);
        SegmentFooter d3 = SegmentFooter.fromBytes(b3);

        assertEquals(100, d1.entryCount());
        assertEquals(200, d2.entryCount());
        assertEquals(300, d3.entryCount());

        assertTrue(d1.isValid() && d2.isValid() && d3.isValid(),
                "All footers should be valid");
    }

    @Test
    void testExtremeLongValues() throws Exception {
        long minTs = Long.MIN_VALUE;
        long maxTs = Long.MAX_VALUE;

        SegmentFooter footer = SegmentFooter.create(999, minTs, maxTs);
        byte[] bytes = footer.toBytes();
        SegmentFooter deserialized = SegmentFooter.fromBytes(bytes);

        assertEquals(minTs, deserialized.minTimestamp(), "MIN_VALUE timestamp should be preserved");
        assertEquals(maxTs, deserialized.maxTimestamp(), "MAX_VALUE timestamp should be preserved");
        assertTrue(deserialized.isValid(), "Footer with extreme values should be valid");
    }
}