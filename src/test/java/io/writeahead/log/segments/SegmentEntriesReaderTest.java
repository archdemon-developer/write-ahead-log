package io.writeahead.log.segments;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.exceptions.CorruptionException;
import io.writeahead.log.exceptions.CorruptionType;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.serdes.EntrySerdes;
import io.writeahead.log.utils.Crc32Utils;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class SegmentEntriesReaderTest {

    private static final long FIRST_ENTRY_TIMESTAMP = 1000L;
    private static final long SECOND_ENTRY_TIMESTAMP = 2000L;
    private static final long THIRD_ENTRY_TIMESTAMP = 3000L;
    private static final String FIRST_ENTRY_PAYLOAD = "e1";
    private static final String SECOND_ENTRY_PAYLOAD = "e2";
    private static final String THIRD_ENTRY_PAYLOAD = "e3";
    private static final String HELLO_PAYLOAD = "hello";
    private static final int SMALL_PAYLOAD_SIZE = 2;
    private static final int HELLO_PAYLOAD_SIZE = 5;
    private static final int LARGE_PAYLOAD_SIZE = 1024 * 100;
    private static final int EXPECTED_SINGLE_ENTRY = 1;
    private static final int EXPECTED_THREE_ENTRIES = 3;
    private static final int ZERO_ENTRIES = 0;

    private final SegmentEntriesReader entriesReader = new SegmentEntriesReader();

    @Test
    void readSingleEntryFromRegion() throws IOException {
        byte[] serializedRegion = createSerializedEntryRegion(
                new LogEntry(HELLO_PAYLOAD_SIZE, HELLO_PAYLOAD.getBytes(), FIRST_ENTRY_TIMESTAMP)
        );

        SegmentEntriesReader.SegmentReadResult readResult = entriesReader.readEntriesFromRegion(serializedRegion);

        assertEquals(EXPECTED_SINGLE_ENTRY, readResult.entriesRead());
        assertEquals(EXPECTED_SINGLE_ENTRY, readResult.entries().size());
        assertFalse(readResult.hasCorruption());
        assertEquals(FIRST_ENTRY_TIMESTAMP, readResult.entries().getFirst().timestamp());
    }

    @Test
    void readMultipleEntriesFromRegion() throws IOException {
        byte[] serializedRegion = createSerializedEntryRegion(
                new LogEntry(SMALL_PAYLOAD_SIZE, FIRST_ENTRY_PAYLOAD.getBytes(), FIRST_ENTRY_TIMESTAMP),
                new LogEntry(SMALL_PAYLOAD_SIZE, SECOND_ENTRY_PAYLOAD.getBytes(), SECOND_ENTRY_TIMESTAMP),
                new LogEntry(SMALL_PAYLOAD_SIZE, THIRD_ENTRY_PAYLOAD.getBytes(), THIRD_ENTRY_TIMESTAMP)
        );

        SegmentEntriesReader.SegmentReadResult readResult = entriesReader.readEntriesFromRegion(serializedRegion);

        assertEquals(EXPECTED_THREE_ENTRIES, readResult.entriesRead());
        assertEquals(EXPECTED_THREE_ENTRIES, readResult.entries().size());
        assertFalse(readResult.hasCorruption());
    }

    @Test
    void corruptedEntryCrcThrowsCorruptionException() throws IOException {
        byte[] serializedRegion = createSerializedEntryRegion(
                new LogEntry(HELLO_PAYLOAD_SIZE, HELLO_PAYLOAD.getBytes(), FIRST_ENTRY_TIMESTAMP)
        );

        serializedRegion[serializedRegion.length - 1] = (byte) ~serializedRegion[serializedRegion.length - 1];

        CorruptionException caughtException = assertThrows(
                CorruptionException.class,
                () -> entriesReader.readEntriesFromRegion(serializedRegion)
        );

        assertEquals(CorruptionType.ENTRY_CRC_MISMATCH, caughtException.corruptionType());
        assertTrue(caughtException.getMessage().contains("Entry CRC mismatch"));
    }

    @Test
    void corruptionInMiddleEntryStopsReading() throws IOException {
        byte[] serializedRegion = createSerializedEntryRegion(
                new LogEntry(SMALL_PAYLOAD_SIZE, FIRST_ENTRY_PAYLOAD.getBytes(), FIRST_ENTRY_TIMESTAMP),
                new LogEntry(SMALL_PAYLOAD_SIZE, SECOND_ENTRY_PAYLOAD.getBytes(), SECOND_ENTRY_TIMESTAMP),
                new LogEntry(SMALL_PAYLOAD_SIZE, THIRD_ENTRY_PAYLOAD.getBytes(), THIRD_ENTRY_TIMESTAMP)
        );

        int secondEntryStartOffset = 8 + 4 + SMALL_PAYLOAD_SIZE;
        int secondEntryCrcPositionInBuffer = secondEntryStartOffset + 8 + 4 + SMALL_PAYLOAD_SIZE;
        serializedRegion[secondEntryCrcPositionInBuffer + 1] = (byte) ~serializedRegion[secondEntryCrcPositionInBuffer + 1];
        serializedRegion[secondEntryCrcPositionInBuffer] = (byte) ~serializedRegion[secondEntryCrcPositionInBuffer];

        CorruptionException caughtException = assertThrows(
                CorruptionException.class,
                () -> entriesReader.readEntriesFromRegion(serializedRegion)
        );

        assertEquals(CorruptionType.ENTRY_CRC_MISMATCH, caughtException.corruptionType());
        assertTrue(caughtException.getMessage().contains("entry 1"));
    }

    @Test
    void emptyEntryRegionReturnsEmptyList() throws IOException {
        byte[] emptyRegion = new byte[0];

        SegmentEntriesReader.SegmentReadResult readResult = entriesReader.readEntriesFromRegion(emptyRegion);

        assertEquals(ZERO_ENTRIES, readResult.entries().size());
        assertFalse(readResult.hasCorruption());
    }

    @Test
    void largeEntryIsReadCorrectly() throws IOException {
        byte[] largePayloadData = new byte[LARGE_PAYLOAD_SIZE];
        for (int index = 0; index < largePayloadData.length; index++) {
            largePayloadData[index] = (byte) (index % 256);
        }

        byte[] serializedRegion = createSerializedEntryRegion(
                new LogEntry(LARGE_PAYLOAD_SIZE, largePayloadData, FIRST_ENTRY_TIMESTAMP)
        );

        SegmentEntriesReader.SegmentReadResult readResult = entriesReader.readEntriesFromRegion(serializedRegion);

        assertEquals(EXPECTED_SINGLE_ENTRY, readResult.entries().size());
        assertEquals(LARGE_PAYLOAD_SIZE, readResult.entries().getFirst().size());
        assertFalse(readResult.hasCorruption());
    }

    private byte[] createSerializedEntryRegion(LogEntry... entriesToSerialize) throws IOException {
        ByteArrayOutputStream byteArrayBuffer = new ByteArrayOutputStream();
        DataOutputStream dataStreamWriter = new DataOutputStream(byteArrayBuffer);

        for (LogEntry entry : entriesToSerialize) {
            long entryCrc = Crc32Utils.computeEntryCrc(entry.timestamp(), entry.size(), entry.data());

            byte[] serializedEntryWithCrc = EntrySerdes.serializeEntryWithCrc(
                    entry.timestamp(),
                    entry.size(),
                    entry.data(),
                    entryCrc
            );

            dataStreamWriter.write(serializedEntryWithCrc);
        }

        dataStreamWriter.flush();
        byte[] serializedEntryRegionBytes = byteArrayBuffer.toByteArray();

        dataStreamWriter.close();
        byteArrayBuffer.close();

        return serializedEntryRegionBytes;
    }
}