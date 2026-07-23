package io.writeahead.log.segments;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.LogEntry;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import io.writeahead.log.serdes.EntrySerdes;
import io.writeahead.log.utils.Crc32Utils;
import org.junit.jupiter.api.Test;

public class SegmentReaderTest {

    private final SegmentReader reader = new SegmentReader();

    @Test
    void testReadSingleEntry() throws IOException {
        byte[] entryRegion = createEntryRegion(
                new LogEntry(5, "hello".getBytes(), 1000L));

        SegmentReader.SegmentReadResult result = reader.readEntriesFromRegion(entryRegion);

        assertEquals(1, result.entriesRead(), "Should read 1 entry");
        assertEquals(1, result.entries().size(), "Should have 1 entry");
        assertFalse(result.hasCorruption(), "Should be valid");
        assertEquals(1000L, result.entries().getFirst().timestamp(), "Timestamp should match");
    }

    @Test
    void testReadMultipleEntries() throws IOException {
        byte[] entryRegion = createEntryRegion(
                new LogEntry(2, "e1".getBytes(), 1000L),
                new LogEntry(2, "e2".getBytes(), 2000L),
                new LogEntry(2, "e3".getBytes(), 3000L));

        SegmentReader.SegmentReadResult result = reader.readEntriesFromRegion(entryRegion);

        assertEquals(3, result.entriesRead(), "Should read 3 entries");
        assertEquals(3, result.entries().size(), "Should have 3 entries");
        assertFalse(result.hasCorruption(), "Should be valid");
    }

    @Test
    void testCorruptedEntryCrc() throws IOException {
        byte[] entryRegion = createEntryRegion(
                new LogEntry(5, "hello".getBytes(), 1000L));

        // Corrupt the CRC (last 8 bytes)
        entryRegion[entryRegion.length - 1] = (byte) ~entryRegion[entryRegion.length - 1];

        SegmentReader.SegmentReadResult result = reader.readEntriesFromRegion(entryRegion);

        assertEquals(0, result.entries().size(), "Should not return corrupted entry");
        assertTrue(result.hasCorruption(), "Should detect corruption");
        assertEquals(0, result.corruptionAtEntry(), "Corruption at entry 0");
    }

    @Test
    void testCorruptionStopsReading() throws IOException {
        byte[] entryRegion = createEntryRegion(
                new LogEntry(2, "e1".getBytes(), 1000L),
                new LogEntry(2, "e2".getBytes(), 2000L),
                new LogEntry(2, "e3".getBytes(), 3000L));

        // Corrupt the second entry's CRC
        // 22 bytes
        int secondEntryStart = 8 + 4 + 2 + 8;
        int secondEntryCrcPos = secondEntryStart + 8 + 4 + 2;
        entryRegion[secondEntryCrcPos + 1] = (byte) ~entryRegion[secondEntryCrcPos + 1];
        entryRegion[secondEntryCrcPos] = (byte) ~entryRegion[secondEntryCrcPos];

        SegmentReader.SegmentReadResult result = reader.readEntriesFromRegion(entryRegion);

        assertEquals(1, result.entries().size(), "Should have read only 1 entry before corruption");
        assertTrue(result.hasCorruption(), "Should detect corruption");
        assertEquals(1, result.corruptionAtEntry(), "Corruption at entry 1");
    }

    @Test
    void testEmptyEntryRegion() throws IOException {
        byte[] entryRegion = new byte[0];

        SegmentReader.SegmentReadResult result = reader.readEntriesFromRegion(entryRegion);

        assertEquals(0, result.entries().size(), "Should return empty list");
        assertFalse(result.hasCorruption(), "Empty is not corruption");
    }

    @Test
    void testLargeEntry() throws IOException {
        byte[] largeData = new byte[1024 * 100];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        byte[] entryRegion = createEntryRegion(
                new LogEntry(largeData.length, largeData, 1000L));

        SegmentReader.SegmentReadResult result = reader.readEntriesFromRegion(entryRegion);

        assertEquals(1, result.entries().size(), "Should read large entry");
        assertEquals(largeData.length, result.entries().getFirst().size(), "Size should match");
        assertFalse(result.hasCorruption(), "Should be valid");
    }

    @Test
    void testComputeEntryCrcConsistency() throws IOException {
        long timestamp = 1000L;
        int size = 5;
        byte[] data = "hello".getBytes();

        long crc1 = SegmentReader.computeEntryCrc(timestamp, size, data);
        long crc2 = SegmentReader.computeEntryCrc(timestamp, size, data);

        assertEquals(crc1, crc2, "CRC should be deterministic");
    }

    // Helper: Create entry region from list of entries
    private byte[] createEntryRegion(LogEntry... entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        for (LogEntry entry : entries) {
            long crc = Crc32Utils.computeEntryCrc(entry.timestamp(), entry.size(), entry.data());

            byte[] entryWithCrc = EntrySerdes.serializeEntryWithCrc(
                    entry.timestamp(),
                    entry.size(),
                    entry.data(),
                    crc);

            dos.write(entryWithCrc);
        }

        dos.flush();  // ← FLUSH FIRST
        byte[] result = baos.toByteArray();

        dos.close();
        baos.close();

        return result;
    }
}