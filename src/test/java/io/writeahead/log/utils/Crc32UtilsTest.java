package io.writeahead.log.utils;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.serdes.EntrySerdes;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class Crc32UtilsTest {

    private static final String SIMPLE_PAYLOAD = "hello";
    private static final String ALTERNATIVE_PAYLOAD = "world";
    private static final String SINGLE_BYTE_VARIANT = "hallo";
    private static final long ARBITRARY_TIMESTAMP = 1000L;
    private static final int SMALL_PAYLOAD_SIZE = 5;
    private static final int LARGE_PAYLOAD_SIZE = 10000;
    private static final int ZERO_FILLED_ARRAY_SIZE = 100;
    private static final int MINIMUM_UNIQUE_CRC_COUNT_FOR_100_VALUES = 95;

    @Test
    void computeSimplePayloadProducesNonZeroPositiveCrc() {
        byte[] simplePayloadBytes = SIMPLE_PAYLOAD.getBytes();

        long computedCrc = Crc32Utils.compute(simplePayloadBytes);

        assertNotEquals(0, computedCrc);
        assertTrue(computedCrc > 0);
    }

    @Test
    void computeIdenticalInputProducesIdenticalCrcMultipleTimes() {
        byte[] simplePayloadBytes = SIMPLE_PAYLOAD.getBytes();

        long crcFirstComputation = Crc32Utils.compute(simplePayloadBytes);
        long crcSecondComputation = Crc32Utils.compute(simplePayloadBytes);
        long crcThirdComputation = Crc32Utils.compute(simplePayloadBytes);

        assertEquals(crcFirstComputation, crcSecondComputation);
        assertEquals(crcSecondComputation, crcThirdComputation);
    }

    @Test
    void computeDifferentPaylodsProduceDifferentCrcs() {
        byte[] simplePayloadBytes = SIMPLE_PAYLOAD.getBytes();
        byte[] alternativePayloadBytes = ALTERNATIVE_PAYLOAD.getBytes();
        byte[] singleByteVariantBytes = SINGLE_BYTE_VARIANT.getBytes();

        long crcSimplePayload = Crc32Utils.compute(simplePayloadBytes);
        long crcAlternativePayload = Crc32Utils.compute(alternativePayloadBytes);
        long crcSingleByteVariant = Crc32Utils.compute(singleByteVariantBytes);

        assertNotEquals(crcSimplePayload, crcAlternativePayload);
        assertNotEquals(crcSimplePayload, crcSingleByteVariant);
    }

    @Test
    void computeDetectsSingleBitFlip() {
        byte[] originalPayloadBytes = SIMPLE_PAYLOAD.getBytes();
        byte[] corruptedPayloadBytes = SIMPLE_PAYLOAD.getBytes();
        corruptedPayloadBytes[0] = (byte) ~corruptedPayloadBytes[0];

        long crcOriginal = Crc32Utils.compute(originalPayloadBytes);
        long crcCorrupted = Crc32Utils.compute(corruptedPayloadBytes);

        assertNotEquals(crcOriginal, crcCorrupted);
    }

    @Test
    void computeEmptyByteArrayProducesConsistentCrc() {
        byte[] emptyPayloadBytes = new byte[0];

        long crcFirstComputation = Crc32Utils.compute(emptyPayloadBytes);
        long crcSecondComputation = Crc32Utils.compute(emptyPayloadBytes);

        assertEquals(crcFirstComputation, crcSecondComputation);
    }

    @Test
    void computeLargePayloadProducesConsistentCrc() {
        byte[] largePayloadBytes = new byte[LARGE_PAYLOAD_SIZE];
        for (int index = 0; index < largePayloadBytes.length; index++) {
            largePayloadBytes[index] = (byte) (index % 256);
        }

        long crcFirstComputation = Crc32Utils.compute(largePayloadBytes);
        long crcSecondComputation = Crc32Utils.compute(largePayloadBytes);

        assertEquals(crcFirstComputation, crcSecondComputation);
        assertNotEquals(0, crcFirstComputation);
    }

    @Test
    void computeNullPayloadThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> Crc32Utils.compute(null));
    }

    @Test
    void computeAllZeroBytesProducesConsistentCrc() {
        byte[] allZeroBytes = new byte[ZERO_FILLED_ARRAY_SIZE];

        long crcFirstComputation = Crc32Utils.compute(allZeroBytes);
        long crcSecondComputation = Crc32Utils.compute(allZeroBytes);

        assertEquals(crcFirstComputation, crcSecondComputation);
    }

    @Test
    void computeAllOnesBytesProducesConsistentCrc() {
        byte[] allOnesBytes = new byte[ZERO_FILLED_ARRAY_SIZE];
        for (int index = 0; index < allOnesBytes.length; index++) {
            allOnesBytes[index] = (byte) 0xFF;
        }

        long crcFirstComputation = Crc32Utils.compute(allOnesBytes);
        long crcSecondComputation = Crc32Utils.compute(allOnesBytes);

        assertEquals(crcFirstComputation, crcSecondComputation);
    }

    @Test
    void computeDetectsReorderedBytes() {
        byte[] orderedSequence = {1, 2, 3, 4, 5};
        byte[] reversedSequence = {5, 4, 3, 2, 1};

        long crcOrdered = Crc32Utils.compute(orderedSequence);
        long crcReversed = Crc32Utils.compute(reversedSequence);

        assertNotEquals(crcOrdered, crcReversed);
    }

    @Test
    void computeProducesMostlyUniqueCrcsFor100DistinctInputs() {
        long[] computedCrcsForDistinctInputs = new long[100];
        for (int index = 0; index < 100; index++) {
            byte[] sequentialDataBytes = new byte[4];
            sequentialDataBytes[0] = (byte) ((index >>> 24) & 0xFF);
            sequentialDataBytes[1] = (byte) ((index >>> 16) & 0xFF);
            sequentialDataBytes[2] = (byte) ((index >>> 8) & 0xFF);
            sequentialDataBytes[3] = (byte) (index & 0xFF);
            computedCrcsForDistinctInputs[index] = Crc32Utils.compute(sequentialDataBytes);
        }

        Set<Long> uniqueCrcValues = new HashSet<>();
        for (long crc : computedCrcsForDistinctInputs) {
            uniqueCrcValues.add(crc);
        }

        assertTrue(uniqueCrcValues.size() >= MINIMUM_UNIQUE_CRC_COUNT_FOR_100_VALUES);
    }

    @Test
    void computeEntryCrcWithSimplePayloadProducesNonZeroPositiveCrc() throws IOException {
        byte[] simplePayloadBytes = SIMPLE_PAYLOAD.getBytes();

        long computedCrc = Crc32Utils.computeEntryCrc(ARBITRARY_TIMESTAMP, SMALL_PAYLOAD_SIZE, simplePayloadBytes);

        assertNotEquals(0, computedCrc);
        assertTrue(computedCrc > 0);
    }

    @Test
    void computeEntryCrcIdenticalInputProducesIdenticalCrcMultipleTimes() throws IOException {
        byte[] simplePayloadBytes = SIMPLE_PAYLOAD.getBytes();

        long crcFirstComputation = Crc32Utils.computeEntryCrc(ARBITRARY_TIMESTAMP, SMALL_PAYLOAD_SIZE, simplePayloadBytes);
        long crcSecondComputation = Crc32Utils.computeEntryCrc(ARBITRARY_TIMESTAMP, SMALL_PAYLOAD_SIZE, simplePayloadBytes);
        long crcThirdComputation = Crc32Utils.computeEntryCrc(ARBITRARY_TIMESTAMP, SMALL_PAYLOAD_SIZE, simplePayloadBytes);

        assertEquals(crcFirstComputation, crcSecondComputation);
        assertEquals(crcSecondComputation, crcThirdComputation);
    }

    @Test
    void computeEntryCrcDetectsDifferentTimestamps() throws IOException {
        byte[] simplePayloadBytes = SIMPLE_PAYLOAD.getBytes();

        long crcTimestamp1000 = Crc32Utils.computeEntryCrc(1000L, SMALL_PAYLOAD_SIZE, simplePayloadBytes);
        long crcTimestamp1001 = Crc32Utils.computeEntryCrc(1001L, SMALL_PAYLOAD_SIZE, simplePayloadBytes);
        long crcTimestamp2000 = Crc32Utils.computeEntryCrc(2000L, SMALL_PAYLOAD_SIZE, simplePayloadBytes);

        assertNotEquals(crcTimestamp1000, crcTimestamp1001);
        assertNotEquals(crcTimestamp1000, crcTimestamp2000);
    }

    @Test
    void computeEntryCrcDetectsDifferentPayloads() throws IOException {
        long timestamp = ARBITRARY_TIMESTAMP;

        long crcSimplePayload = Crc32Utils.computeEntryCrc(timestamp, SMALL_PAYLOAD_SIZE, SIMPLE_PAYLOAD.getBytes());
        long crcAlternativePayload = Crc32Utils.computeEntryCrc(timestamp, SMALL_PAYLOAD_SIZE, ALTERNATIVE_PAYLOAD.getBytes());

        assertNotEquals(crcSimplePayload, crcAlternativePayload);
    }

    @Test
    void computeEntryCrcDetectsDifferentSizes() throws IOException {
        long timestamp = ARBITRARY_TIMESTAMP;
        byte[] simplePayloadBytes = SIMPLE_PAYLOAD.getBytes();

        long crcSize5 = Crc32Utils.computeEntryCrc(timestamp, 5, simplePayloadBytes);
        long crcSize6 = Crc32Utils.computeEntryCrc(timestamp, 6, simplePayloadBytes);

        assertNotEquals(crcSize5, crcSize6);
    }

    @Test
    void computeEntryCrcEmptyPayloadProducesConsistentCrc() throws IOException {
        byte[] emptyPayloadBytes = new byte[0];

        long crcFirstComputation = Crc32Utils.computeEntryCrc(ARBITRARY_TIMESTAMP, 0, emptyPayloadBytes);
        long crcSecondComputation = Crc32Utils.computeEntryCrc(ARBITRARY_TIMESTAMP, 0, emptyPayloadBytes);

        assertEquals(crcFirstComputation, crcSecondComputation);
    }

    @Test
    void computeEntryCrcLargePayloadProducesValidCrc() throws IOException {
        byte[] largePayloadBytes = new byte[LARGE_PAYLOAD_SIZE];
        for (int index = 0; index < largePayloadBytes.length; index++) {
            largePayloadBytes[index] = (byte) (index % 256);
        }

        long computedCrc = Crc32Utils.computeEntryCrc(ARBITRARY_TIMESTAMP, LARGE_PAYLOAD_SIZE, largePayloadBytes);

        assertNotEquals(0, computedCrc);
    }

    @Test
    void computeEntryCrcExtremeTimestampsProducesMostlyUniqueCrcs() throws IOException {
        byte[] simplePayloadBytes = SIMPLE_PAYLOAD.getBytes();
        long[] extremeTimestampValues = {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE};
        long[] computedCrcsForExtremeTimestamps = new long[extremeTimestampValues.length];

        for (int index = 0; index < extremeTimestampValues.length; index++) {
            computedCrcsForExtremeTimestamps[index] = Crc32Utils.computeEntryCrc(extremeTimestampValues[index], SMALL_PAYLOAD_SIZE, simplePayloadBytes);
        }

        Set<Long> uniqueCrcValues = new HashSet<>();
        for (long crc : computedCrcsForExtremeTimestamps) {
            uniqueCrcValues.add(crc);
        }

        assertTrue(uniqueCrcValues.size() >= 4);
    }

    @Test
    void computeEntryCrcMatchesComputeOfSerializedEntry() throws IOException {
        long timestamp = ARBITRARY_TIMESTAMP;
        int size = SMALL_PAYLOAD_SIZE;
        byte[] simplePayloadBytes = SIMPLE_PAYLOAD.getBytes();

        byte[] serializedEntryBytes = EntrySerdes.serializeEntrySanseCrc(timestamp, size, simplePayloadBytes);
        long crcOfSerializedEntry = Crc32Utils.compute(serializedEntryBytes);
        long crcViaEntryMethod = Crc32Utils.computeEntryCrc(timestamp, size, simplePayloadBytes);

        assertEquals(crcOfSerializedEntry, crcViaEntryMethod);
    }

    @Test
    void computeEntryCrcProducesNonZeroCrc() throws IOException {
        byte[] testPayloadBytes = "test".getBytes();

        long computedCrc = Crc32Utils.computeEntryCrc(ARBITRARY_TIMESTAMP, 4, testPayloadBytes);

        assertNotEquals(0, computedCrc);
    }
}