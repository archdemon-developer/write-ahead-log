package io.writeahead.log.models;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;

public class SegmentFooterTest {

    private static final int REFERENCE_ENTRY_COUNT = 1000;
    private static final int SMALL_ENTRY_COUNT = 100;
    private static final int ZERO_ENTRY_COUNT = 0;
    private static final int LARGE_ENTRY_COUNT = Integer.MAX_VALUE;
    private static final long MINIMUM_TIMESTAMP = 1000L;
    private static final long MAXIMUM_TIMESTAMP = 5000L;
    private static final long SMALL_MINIMUM_TIMESTAMP = 2000L;
    private static final long SMALL_MAXIMUM_TIMESTAMP = 8000L;
    private static final int FOOTER_SERIALIZATION_SIZE = 36;
    private static final int UNDERSIZED_BUFFER_SIZE = 10;
    private static final byte INVALID_COMPLETE_MARKER_BYTE = 0x00;

    @Test
    void createFooterProducesValidFooterWithCorrectFields() throws Exception {
        SegmentFooter createdFooter = SegmentFooter.create(REFERENCE_ENTRY_COUNT, MINIMUM_TIMESTAMP, MAXIMUM_TIMESTAMP);

        assertEquals(REFERENCE_ENTRY_COUNT, createdFooter.entryCount());
        assertEquals(MINIMUM_TIMESTAMP, createdFooter.minTimestamp());
        assertEquals(MAXIMUM_TIMESTAMP, createdFooter.maxTimestamp());
        assertTrue(createdFooter.isValid());
    }

    @Test
    void serializationAndDeserializationPreservesAllFooterFields() throws Exception {
        SegmentFooter originalFooter = SegmentFooter.create(REFERENCE_ENTRY_COUNT, SMALL_MINIMUM_TIMESTAMP, SMALL_MAXIMUM_TIMESTAMP);
        byte[] serializedFooterBytes = originalFooter.toBytes();

        assertEquals(FOOTER_SERIALIZATION_SIZE, serializedFooterBytes.length);

        SegmentFooter deserializedFooter = SegmentFooter.fromBytes(serializedFooterBytes);

        assertEquals(originalFooter.entryCount(), deserializedFooter.entryCount());
        assertEquals(originalFooter.minTimestamp(), deserializedFooter.minTimestamp());
        assertEquals(originalFooter.maxTimestamp(), deserializedFooter.maxTimestamp());
        assertEquals(originalFooter.checksum(), deserializedFooter.checksum());
        assertTrue(deserializedFooter.isValid());
    }

    @Test
    void corruptedCompleteMarkerRendersFooterInvalid() throws Exception {
        SegmentFooter validFooter = SegmentFooter.create(SMALL_ENTRY_COUNT, MINIMUM_TIMESTAMP, MAXIMUM_TIMESTAMP);
        byte[] serializedFooterBytes = validFooter.toBytes();

        serializedFooterBytes[20] = (byte) ~serializedFooterBytes[20];

        SegmentFooter corruptedFooter = SegmentFooter.fromBytes(serializedFooterBytes);
        assertFalse(corruptedFooter.isValid());
    }

    @Test
    void corruptedChecksumRendersFooterInvalid() throws Exception {
        SegmentFooter validFooter = SegmentFooter.create(SMALL_ENTRY_COUNT, MINIMUM_TIMESTAMP, MAXIMUM_TIMESTAMP);
        byte[] serializedFooterBytes = validFooter.toBytes();

        serializedFooterBytes[35] = (byte) ~serializedFooterBytes[35];

        SegmentFooter corruptedFooter = SegmentFooter.fromBytes(serializedFooterBytes);
        assertFalse(corruptedFooter.isValid());
    }

    @Test
    void multipleEntryCountsSerializeAndDeserializeCorrectly() throws Exception {
        for (int entryCount = 0; entryCount <= 10; entryCount++) {
            SegmentFooter footerForCount = SegmentFooter.create(entryCount, MINIMUM_TIMESTAMP, MAXIMUM_TIMESTAMP);
            byte[] serializedFooterBytes = footerForCount.toBytes();
            SegmentFooter deserializedFooter = SegmentFooter.fromBytes(serializedFooterBytes);

            assertEquals(entryCount, deserializedFooter.entryCount());
            assertTrue(deserializedFooter.isValid());
        }
    }

    @Test
    void largeEntryCountPreservedThroughSerializationRoundTrip() throws Exception {
        SegmentFooter footerWithLargeCount = SegmentFooter.create(LARGE_ENTRY_COUNT, MINIMUM_TIMESTAMP, MAXIMUM_TIMESTAMP);
        byte[] serializedFooterBytes = footerWithLargeCount.toBytes();
        SegmentFooter deserializedFooter = SegmentFooter.fromBytes(serializedFooterBytes);

        assertEquals(LARGE_ENTRY_COUNT, deserializedFooter.entryCount());
    }

    @Test
    void zeroEntryCountIsValidAndPreserved() throws Exception {
        SegmentFooter footerWithZeroCount = SegmentFooter.create(ZERO_ENTRY_COUNT, MINIMUM_TIMESTAMP, MAXIMUM_TIMESTAMP);
        byte[] serializedFooterBytes = footerWithZeroCount.toBytes();
        SegmentFooter deserializedFooter = SegmentFooter.fromBytes(serializedFooterBytes);

        assertEquals(ZERO_ENTRY_COUNT, deserializedFooter.entryCount());
        assertTrue(deserializedFooter.isValid());
    }

    @Test
    void corruptedEntryCountFieldRendersFooterInvalid() throws Exception {
        SegmentFooter validFooter = SegmentFooter.create(REFERENCE_ENTRY_COUNT, MINIMUM_TIMESTAMP, MAXIMUM_TIMESTAMP);
        byte[] serializedFooterBytes = validFooter.toBytes();

        serializedFooterBytes[2] = (byte) ~serializedFooterBytes[2];

        SegmentFooter corruptedFooter = SegmentFooter.fromBytes(serializedFooterBytes);
        assertFalse(corruptedFooter.isValid());
    }

    @Test
    void corruptedMinTimestampFieldRendersFooterInvalid() throws Exception {
        SegmentFooter validFooter = SegmentFooter.create(SMALL_ENTRY_COUNT, MINIMUM_TIMESTAMP, MAXIMUM_TIMESTAMP);
        byte[] serializedFooterBytes = validFooter.toBytes();

        serializedFooterBytes[12] = (byte) ~serializedFooterBytes[12];

        SegmentFooter corruptedFooter = SegmentFooter.fromBytes(serializedFooterBytes);
        assertFalse(corruptedFooter.isValid());
    }

    @Test
    void corruptedMaxTimestampFieldRendersFooterInvalid() throws Exception {
        SegmentFooter validFooter = SegmentFooter.create(SMALL_ENTRY_COUNT, MINIMUM_TIMESTAMP, MAXIMUM_TIMESTAMP);
        byte[] serializedFooterBytes = validFooter.toBytes();

        serializedFooterBytes[20] = (byte) ~serializedFooterBytes[20];

        SegmentFooter corruptedFooter = SegmentFooter.fromBytes(serializedFooterBytes);
        assertFalse(corruptedFooter.isValid());
    }

    @Test
    void undersizedInputBufferThrowsIOException() {
        byte[] undersizedBuffer = new byte[UNDERSIZED_BUFFER_SIZE];

        assertThrows(IOException.class, () -> SegmentFooter.fromBytes(undersizedBuffer));
    }

    @Test
    void toStringContainsAllRelevantFooterFields() throws Exception {
        SegmentFooter footer = SegmentFooter.create(SMALL_ENTRY_COUNT, MINIMUM_TIMESTAMP, MAXIMUM_TIMESTAMP);
        String footerStringRepresentation = footer.toString();

        assertTrue(footerStringRepresentation.contains("entryCount"));
        assertTrue(footerStringRepresentation.contains("minTimestamp"));
        assertTrue(footerStringRepresentation.contains("maxTimestamp"));
        assertTrue(footerStringRepresentation.contains("complete"));
        assertTrue(footerStringRepresentation.contains("valid"));
    }

    @Test
    void multipleDistinctFootersSerializeIndependently() throws Exception {
        SegmentFooter firstFooter = SegmentFooter.create(100, 1000L, 2000L);
        SegmentFooter secondFooter = SegmentFooter.create(200, 2000L, 3000L);
        SegmentFooter thirdFooter = SegmentFooter.create(300, 3000L, 4000L);

        byte[] firstFooterSerialized = firstFooter.toBytes();
        byte[] secondFooterSerialized = secondFooter.toBytes();
        byte[] thirdFooterSerialized = thirdFooter.toBytes();

        SegmentFooter firstFooterDeserialized = SegmentFooter.fromBytes(firstFooterSerialized);
        SegmentFooter secondFooterDeserialized = SegmentFooter.fromBytes(secondFooterSerialized);
        SegmentFooter thirdFooterDeserialized = SegmentFooter.fromBytes(thirdFooterSerialized);

        assertEquals(100, firstFooterDeserialized.entryCount());
        assertEquals(200, secondFooterDeserialized.entryCount());
        assertEquals(300, thirdFooterDeserialized.entryCount());

        assertTrue(firstFooterDeserialized.isValid() && secondFooterDeserialized.isValid() && thirdFooterDeserialized.isValid());
    }

    @Test
    void minMaxTimestampsPreservedAccuratelyThroughRoundTrip() throws Exception {
        long minValue = Long.MIN_VALUE;
        long maxValue = Long.MAX_VALUE;

        SegmentFooter footerWithExtremeTimestamps = SegmentFooter.create(SMALL_ENTRY_COUNT, minValue, maxValue);
        byte[] serializedFooterBytes = footerWithExtremeTimestamps.toBytes();
        SegmentFooter deserializedFooter = SegmentFooter.fromBytes(serializedFooterBytes);

        assertEquals(minValue, deserializedFooter.minTimestamp());
        assertEquals(maxValue, deserializedFooter.maxTimestamp());
    }
}