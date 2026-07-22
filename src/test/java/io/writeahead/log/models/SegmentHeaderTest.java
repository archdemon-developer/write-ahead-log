package io.writeahead.log.models;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.constants.WalConstants;
import io.writeahead.log.models.segment.SegmentHeader;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class SegmentHeaderTest {

    @Test
    void testCreateHeader() throws Exception {
        long createdAt = System.currentTimeMillis();
        long sequence = 5;

        SegmentHeader header = SegmentHeader.create(createdAt, sequence);

        assertEquals((byte) 0xAA, header.magic(), "Magic byte should be 0xAA");
        assertEquals(0x01, header.version(), "Version should be 0x01");
        assertEquals(createdAt, header.createdAt(), "Created timestamp should match");
        assertEquals(sequence, header.segmentSequence(), "Sequence should match");
        assertTrue(header.isValid(), "Header should be valid");
    }

    @Test
    void testSerializationRoundTrip() throws Exception {
        long createdAt = 1000000L;
        long sequence = 10;

        SegmentHeader original = SegmentHeader.create(createdAt, sequence);
        byte[] bytes = original.toBytes();

        assertEquals(WalConstants.SEGMENT_HEADER_SIZE, bytes.length, "Header should be 48 bytes");

        SegmentHeader deserialized = SegmentHeader.fromBytes(bytes);

        assertEquals(original.magic(), deserialized.magic(), "Magic should match");
        assertEquals(original.version(), deserialized.version(), "Version should match");
        assertEquals(original.createdAt(), deserialized.createdAt(), "Created timestamp should match");
        assertEquals(original.segmentSequence(), deserialized.segmentSequence(), "Sequence should match");
        assertEquals(original.checksum(), deserialized.checksum(), "Checksum should match");
        assertTrue(deserialized.isValid(), "Deserialized header should be valid");
    }

    @Test
    void testInvalidMagicByte() throws Exception {
        SegmentHeader header = SegmentHeader.create(System.currentTimeMillis(), 1);
        byte[] bytes = header.toBytes();

        // Corrupt magic byte
        bytes[0] = (byte) 0xBB;

        SegmentHeader corrupted = SegmentHeader.fromBytes(bytes);
        assertFalse(corrupted.isValid(), "Header with wrong magic byte should be invalid");
    }

    @Test
    void testChecksumValidation() throws Exception {
        SegmentHeader header = SegmentHeader.create(System.currentTimeMillis(), 1);
        byte[] bytes = header.toBytes();

        // Corrupt checksum (last 8 bytes)
        bytes[47] = (byte) ~bytes[47];

        SegmentHeader corrupted = SegmentHeader.fromBytes(bytes);
        assertFalse(corrupted.isValid(), "Header with corrupted checksum should be invalid");
    }

    @Test
    void testMultipleSequenceNumbers() throws Exception {
        for (long seq = 1; seq <= 100; seq++) {
            SegmentHeader header = SegmentHeader.create(System.currentTimeMillis(), seq);
            byte[] bytes = header.toBytes();
            SegmentHeader deserialized = SegmentHeader.fromBytes(bytes);

            assertEquals(seq, deserialized.segmentSequence(), "Sequence " + seq + " should be preserved");
            assertTrue(deserialized.isValid(), "Header with sequence " + seq + " should be valid");
        }
    }

    @Test
    void testTimestampPreservation() throws Exception {
        long timestamp = 1234567890123L;
        SegmentHeader header = SegmentHeader.create(timestamp, 1);
        byte[] bytes = header.toBytes();
        SegmentHeader deserialized = SegmentHeader.fromBytes(bytes);

        assertEquals(timestamp, deserialized.createdAt(), "Timestamp should be preserved exactly");
    }

    @Test
    void testCorruptedTimestamp() throws Exception {
        SegmentHeader header = SegmentHeader.create(1000000L, 1);
        byte[] bytes = header.toBytes();

        // Corrupt timestamp field (bytes 2-9)
        bytes[5] = (byte) ~bytes[5];

        SegmentHeader corrupted = SegmentHeader.fromBytes(bytes);
        assertFalse(corrupted.isValid(), "Header with corrupted timestamp should fail checksum");
    }

    @Test
    void testCorruptedSequence() throws Exception {
        SegmentHeader header = SegmentHeader.create(System.currentTimeMillis(), 42);
        byte[] bytes = header.toBytes();

        // Corrupt sequence field (bytes 10-17)
        bytes[15] = (byte) ~bytes[15];

        SegmentHeader corrupted = SegmentHeader.fromBytes(bytes);
        assertFalse(corrupted.isValid(), "Header with corrupted sequence should fail checksum");
    }

    @Test
    void testZeroSequence() throws Exception {
        SegmentHeader header = SegmentHeader.create(System.currentTimeMillis(), 0);
        byte[] bytes = header.toBytes();
        SegmentHeader deserialized = SegmentHeader.fromBytes(bytes);

        assertEquals(0, deserialized.segmentSequence(), "Sequence 0 should be valid");
        assertTrue(deserialized.isValid(), "Header with sequence 0 should be valid");
    }

    @Test
    void testLargeSequence() throws Exception {
        long largeSeq = Long.MAX_VALUE;
        SegmentHeader header = SegmentHeader.create(System.currentTimeMillis(), largeSeq);
        byte[] bytes = header.toBytes();
        SegmentHeader deserialized = SegmentHeader.fromBytes(bytes);

        assertEquals(largeSeq, deserialized.segmentSequence(), "Large sequence should be preserved");
        assertTrue(deserialized.isValid(), "Header with large sequence should be valid");
    }

    @Test
    void testHeaderTooSmall() {
        byte[] tooSmall = new byte[10];
        assertThrows(IOException.class, () -> SegmentHeader.fromBytes(tooSmall),
                "Should throw IOException for data smaller than header size");
    }

    @Test
    void testToString() throws Exception {
        SegmentHeader header = SegmentHeader.create(1000000L, 5);
        String str = header.toString();

        assertTrue(str.contains("magic"), "toString should contain magic");
        assertTrue(str.contains("version"), "toString should contain version");
        assertTrue(str.contains("createdAt"), "toString should contain createdAt");
        assertTrue(str.contains("segmentSequence"), "toString should contain segmentSequence");
        assertTrue(str.contains("valid"), "toString should contain validation status");
    }

    @Test
    void testMultipleHeadersWithDifferentSequences() throws Exception {
        SegmentHeader h1 = SegmentHeader.create(1000L, 1);
        SegmentHeader h2 = SegmentHeader.create(2000L, 2);
        SegmentHeader h3 = SegmentHeader.create(3000L, 3);

        byte[] b1 = h1.toBytes();
        byte[] b2 = h2.toBytes();
        byte[] b3 = h3.toBytes();

        SegmentHeader d1 = SegmentHeader.fromBytes(b1);
        SegmentHeader d2 = SegmentHeader.fromBytes(b2);
        SegmentHeader d3 = SegmentHeader.fromBytes(b3);

        assertEquals(1, d1.segmentSequence());
        assertEquals(2, d2.segmentSequence());
        assertEquals(3, d3.segmentSequence());

        assertTrue(d1.isValid() && d2.isValid() && d3.isValid(),
                "All headers should be valid");
    }
}