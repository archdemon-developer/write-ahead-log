package io.writeahead.log.utils;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.ConcurrencyErrorType;
import io.writeahead.log.enums.CorruptionType;
import io.writeahead.log.enums.RecoveryType;
import io.writeahead.log.exceptions.*;
import org.junit.jupiter.api.Test;

public class WalErrorClassifierExceptionConstructionTest {

    @Test
    void classifyCorruptionWithHeaderCrcMismatch() {
        CorruptionException result = WalErrorClassifier.classifyCorruption(
                "wal-1000-000001.log",
                48L,
                CorruptionType.HEADER_CRC_MISMATCH,
                0x12345678L,
                0x87654321L,
                "Header CRC validation failed");

        assertNotNull(result);
        assertInstanceOf(CorruptionException.class, result);
        assertEquals("wal-1000-000001.log", result.segmentName());
        assertEquals(48L, result.byteOffset());
        assertEquals(CorruptionType.HEADER_CRC_MISMATCH, result.corruptionType());
    }

    @Test
    void classifyCorruptionWithFooterCrcMismatch() {
        CorruptionException result = WalErrorClassifier.classifyCorruption(
                "wal-1000-000002.log",
                1024L,
                CorruptionType.FOOTER_CRC_MISMATCH,
                0xAAAAAAAAL,
                0xBBBBBBBBL,
                "Footer CRC validation failed");

        assertEquals(CorruptionType.FOOTER_CRC_MISMATCH, result.corruptionType());
    }

    @Test
    void classifyCorruptionWithInvalidMagic() {
        CorruptionException result = WalErrorClassifier.classifyCorruption(
                "wal-1000-000003.log",
                0L,
                CorruptionType.INVALID_MAGIC,
                0xBBL,
                0xAAL,
                "Magic byte is not 0xAA");

        assertEquals(CorruptionType.INVALID_MAGIC, result.corruptionType());
    }

    @Test
    void classifyCorruptionWithInvalidFooterMarker() {
        CorruptionException result = WalErrorClassifier.classifyCorruption(
                "wal-1000-000004.log",
                512L,
                CorruptionType.INVALID_FOOTER_MARKER,
                0xDEADBEEFL,
                0xDEADBEEFL,
                "Footer complete marker is not 0xDB");

        assertEquals(CorruptionType.INVALID_FOOTER_MARKER, result.corruptionType());
    }

    @Test
    void classifyCorruptionWithEntryCrcMismatch() {
        CorruptionException result = WalErrorClassifier.classifyCorruption(
                "wal-1000-000005.log",
                256L,
                CorruptionType.ENTRY_CRC_MISMATCH,
                0x11111111L,
                0x22222222L,
                "Entry CRC doesn't match data");

        assertEquals(CorruptionType.ENTRY_CRC_MISMATCH, result.corruptionType());
    }

    // Tests for classifyRecoveryError() - based on actual RecoveryType enum
    @Test
    void classifyRecoveryErrorWithSegmentTooSmall() {
        RecoveryException result = WalErrorClassifier.classifyRecoveryError(
                RecoveryType.SEGMENT_TOO_SMALL,
                "Segment file is smaller than minimum threshold");

        assertNotNull(result);
        assertInstanceOf(RecoveryException.class, result);
        assertEquals(RecoveryType.SEGMENT_TOO_SMALL, result.recoveryType());
    }

    @Test
    void classifyRecoveryErrorWithPartialEntryAtEof() {
        RecoveryException result = WalErrorClassifier.classifyRecoveryError(
                RecoveryType.PARTIAL_ENTRY_AT_EOF,
                "Incomplete entry found at end of file");

        assertEquals(RecoveryType.PARTIAL_ENTRY_AT_EOF, result.recoveryType());
    }

    @Test
    void classifyRecoveryErrorWithMissingSegmentFile() {
        RecoveryException result = WalErrorClassifier.classifyRecoveryError(
                RecoveryType.MISSING_SEGMENT_FILE,
                "Expected segment file not found");

        assertEquals(RecoveryType.MISSING_SEGMENT_FILE, result.recoveryType());
    }

    @Test
    void classifyRecoveryErrorWithUnreadableSegment() {
        RecoveryException result = WalErrorClassifier.classifyRecoveryError(
                RecoveryType.UNREADABLE_SEGMENT,
                "Cannot read segment file");

        assertEquals(RecoveryType.UNREADABLE_SEGMENT, result.recoveryType());
    }

    @Test
    void classifyRecoveryErrorWithIncompleteSegment() {
        RecoveryException result = WalErrorClassifier.classifyRecoveryError(
                RecoveryType.INCOMPLETE_SEGMENT,
                "Segment header written but not finalized");

        assertEquals(RecoveryType.INCOMPLETE_SEGMENT, result.recoveryType());
    }

    @Test
    void classifyConcurrencyErrorWithLockTimeout() {
        ConcurrencyException result = WalErrorClassifier.classifyConcurrencyError(
                ConcurrencyErrorType.LOCK_TIMEOUT,
                "Failed to acquire write lock within timeout");

        assertNotNull(result);
        assertInstanceOf(ConcurrencyException.class, result);
        assertEquals(ConcurrencyErrorType.LOCK_TIMEOUT, result.errorType());
    }

    @Test
    void classifyConcurrencyErrorWithInterrupted() {
        ConcurrencyException result = WalErrorClassifier.classifyConcurrencyError(
                ConcurrencyErrorType.INTERRUPTED,
                "Thread interrupted during lock acquisition");

        assertEquals(ConcurrencyErrorType.INTERRUPTED, result.errorType());
    }

    @Test
    void classifyConcurrencyErrorWithDeadlock() {
        ConcurrencyException result = WalErrorClassifier.classifyConcurrencyError(
                ConcurrencyErrorType.DEADLOCK,
                "Potential deadlock detected between writers");

        assertEquals(ConcurrencyErrorType.DEADLOCK, result.errorType());
    }

    @Test
    void classifyConcurrencyErrorWithUnknown() {
        ConcurrencyException result = WalErrorClassifier.classifyConcurrencyError(
                ConcurrencyErrorType.UNKNOWN,
                "Unknown concurrency error occurred");

        assertEquals(ConcurrencyErrorType.UNKNOWN, result.errorType());
    }
}