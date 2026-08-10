package io.writeahead.log.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.exceptions.CorruptionType;
import io.writeahead.log.enums.exceptions.ErrorContext;
import io.writeahead.log.enums.exceptions.ErrorRecoveryAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("CorruptionException Tests")
class CorruptionExceptionTest {

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("CorruptionException stores message correctly")
    void storesMessageCorrectly() {
      String message = "Header CRC mismatch detected";
      CorruptionException exception =
          new CorruptionException(
              message,
              "segment_0001.log",
              1024,
              CorruptionType.HEADER_CRC_MISMATCH,
              0xABCD1234L,
              0xDEADBEEFL);

      assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("CorruptionException stores segment name correctly")
    void storesSegmentNameCorrectly() {
      String segmentName = "segment_0042.log";
      CorruptionException exception =
          new CorruptionException(
              "message",
              segmentName,
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0xAABBCCDDL,
              0xDEADBEEFL);

      assertEquals(segmentName, exception.segmentName());
    }

    @Test
    @DisplayName("CorruptionException stores byte offset correctly")
    void storesByteOffsetCorrectly() {
      long offset = 5120;
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              offset,
              CorruptionType.HEADER_CRC_MISMATCH,
              0xAABBCCDDL,
              0xDEADBEEFL);

      assertEquals(offset, exception.byteOffset());
    }

    @Test
    @DisplayName("CorruptionException stores CorruptionType correctly")
    void storesCorruptionTypeCorrectly() {
      CorruptionType type = CorruptionType.HEADER_CRC_MISMATCH;
      CorruptionException exception =
          new CorruptionException("message", "segment.log", 100, type, 0xAABBCCDDL, 0xDEADBEEFL);

      assertEquals(type, exception.corruptionType());
    }

    @Test
    @DisplayName("CorruptionException stores computed value correctly")
    void storesComputedValueCorrectly() {
      long computed = 0x12345678L;
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              computed,
              0xDEADBEEFL);

      assertEquals(computed, exception.computedValue());
    }

    @Test
    @DisplayName("CorruptionException stores expected value correctly")
    void storesExpectedValueCorrectly() {
      long expected = 0xDEADBEEFL;
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              expected);

      assertEquals(expected, exception.expectedValue());
    }
  }

  @Nested
  @DisplayName("Transient Behavior Tests")
  class TransientBehaviorTests {

    @Test
    @DisplayName("CorruptionException isTransient returns false")
    void isTransientReturnsFalse() {
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      assertFalse(exception.isTransient());
    }

    @Test
    @DisplayName("CorruptionException is never transient")
    void isNeverTransient() {
      CorruptionException ex1 =
          new CorruptionException(
              "msg1", "seg1.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      CorruptionException ex2 =
          new CorruptionException(
              "msg2", "seg2.log", 200, CorruptionType.INVALID_MAGIC, 0x3L, 0x4L);

      assertFalse(ex1.isTransient());
      assertFalse(ex2.isTransient());
    }
  }

  @Nested
  @DisplayName("Data Loss Indication Tests")
  class DataLossTests {

    @Test
    @DisplayName("CorruptionException indicatesDataLoss returns true")
    void indicatesDataLossReturnsTrue() {
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      assertTrue(exception.indicatesDataLoss());
    }

    @Test
    @DisplayName("CorruptionException always indicates data loss")
    void alwaysIndicatesDataLoss() {
      CorruptionException ex1 =
          new CorruptionException(
              "msg1", "seg1.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      CorruptionException ex2 =
          new CorruptionException(
              "msg2", "seg2.log", 200, CorruptionType.INVALID_MAGIC, 0x3L, 0x4L);

      assertTrue(ex1.indicatesDataLoss());
      assertTrue(ex2.indicatesDataLoss());
    }
  }

  @Nested
  @DisplayName("Recovery Action Tests")
  class RecoveryActionTests {

    @Test
    @DisplayName("CorruptionException suggests QUARANTINE_AND_ALERT action")
    void suggestsQuarantineAndAlertAction() {
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      assertEquals(ErrorRecoveryAction.QUARANTINE_AND_ALERT, exception.suggestedAction());
    }

    @Test
    @DisplayName("CorruptionException always suggests quarantine")
    void alwaysSuggestsQuarantine() {
      CorruptionException ex1 =
          new CorruptionException(
              "msg1", "seg1.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      CorruptionException ex2 =
          new CorruptionException(
              "msg2", "seg2.log", 200, CorruptionType.INVALID_MAGIC, 0x3L, 0x4L);

      assertEquals(ErrorRecoveryAction.QUARANTINE_AND_ALERT, ex1.suggestedAction());
      assertEquals(ErrorRecoveryAction.QUARANTINE_AND_ALERT, ex2.suggestedAction());
    }
  }

  @Nested
  @DisplayName("Error Context Tests")
  class ErrorContextTests {

    @Test
    @DisplayName("CorruptionException uses DATA_CORRUPTION context")
    void usesDataCorruptionContext() {
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      assertEquals(ErrorContext.DATA_CORRUPTION, exception.context());
    }

    @Test
    @DisplayName("CorruptionException always uses DATA_CORRUPTION context")
    void alwaysUsesDataCorruptionContext() {
      CorruptionException ex1 =
          new CorruptionException(
              "msg1", "seg1.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      CorruptionException ex2 =
          new CorruptionException(
              "msg2", "seg2.log", 200, CorruptionType.INVALID_MAGIC, 0x3L, 0x4L);

      assertEquals(ErrorContext.DATA_CORRUPTION, ex1.context());
      assertEquals(ErrorContext.DATA_CORRUPTION, ex2.context());
    }
  }

  @Nested
  @DisplayName("Error Type Code Tests")
  class ErrorTypeCodeTests {

    @Test
    @DisplayName("CorruptionException errorTypeCode includes CORRUPTION prefix")
    void errorTypeCodeIncludesCorruptionPrefix() {
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      String code = exception.errorTypeCode();
      assertTrue(code.startsWith("CORRUPTION_"));
    }

    @Test
    @DisplayName("CorruptionException errorTypeCode includes corruption type")
    void errorTypeCodeIncludesCorruptionType() {
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      String code = exception.errorTypeCode();
      assertTrue(code.contains("HEADER_CRC_MISMATCH"));
    }

    @Test
    @DisplayName("CorruptionException errorTypeCode reflects corruption type changes")
    void errorTypeCodeReflectsCorruptionTypeChanges() {
      CorruptionException ex1 =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      CorruptionException ex2 =
          new CorruptionException("msg", "seg.log", 100, CorruptionType.INVALID_MAGIC, 0x1L, 0x2L);

      assertNotEquals(ex1.errorTypeCode(), ex2.errorTypeCode());
      assertTrue(ex1.errorTypeCode().contains("HEADER_CRC_MISMATCH"));
      assertTrue(ex2.errorTypeCode().contains("INVALID_MAGIC"));
    }
  }

  @Nested
  @DisplayName("toString() Format Tests")
  class ToStringTests {

    @Test
    @DisplayName("CorruptionException toString includes error type code")
    void toStringIncludesErrorTypeCode() {
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      String result = exception.toString();
      assertTrue(result.contains("CORRUPTION_"));
    }

    @Test
    @DisplayName("CorruptionException toString includes message")
    void toStringIncludesMessage() {
      String message = "CRC validation failed";
      CorruptionException exception =
          new CorruptionException(
              message,
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      String result = exception.toString();
      assertTrue(result.contains(message));
    }

    @Test
    @DisplayName("CorruptionException toString includes context")
    void toStringIncludesContext() {
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      String result = exception.toString();
      assertTrue(result.contains("DATA_CORRUPTION"));
    }

    @Test
    @DisplayName("CorruptionException toString includes recovery action")
    void toStringIncludesRecoveryAction() {
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      String result = exception.toString();
      assertTrue(result.contains("QUARANTINE_AND_ALERT"));
    }

    @Test
    @DisplayName("CorruptionException toString includes operation")
    void toStringIncludesOperation() {
      String segmentName = "segment_0001.log";
      CorruptionException exception =
          new CorruptionException(
              "message",
              segmentName,
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      String result = exception.toString();
      assertTrue(result.contains(segmentName));
    }
  }

  @Nested
  @DisplayName("Segment Name Tests")
  class SegmentNameTests {

    @Test
    @DisplayName("CorruptionException stores various segment names")
    void storesVariousSegmentNames() {
      String[] names = {
        "segment_0.log", "segment_0001.log", "segment_9999.log", "archive.log", "metadata.log"
      };

      for (String name : names) {
        CorruptionException exception =
            new CorruptionException(
                "message", name, 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
        assertEquals(name, exception.segmentName());
      }
    }

    @Test
    @DisplayName("CorruptionException handles null segment name")
    void handlesNullSegmentName() {
      CorruptionException exception =
          new CorruptionException(
              "message", null, 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertNull(exception.segmentName());
    }

    @Test
    @DisplayName("CorruptionException handles empty segment name")
    void handlesEmptySegmentName() {
      CorruptionException exception =
          new CorruptionException(
              "message", "", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertEquals("", exception.segmentName());
    }

    @Test
    @DisplayName("CorruptionException handles long segment name")
    void handlesLongSegmentName() {
      String longName = "x".repeat(1000) + ".log";
      CorruptionException exception =
          new CorruptionException(
              "message", longName, 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertEquals(longName, exception.segmentName());
    }
  }

  @Nested
  @DisplayName("Byte Offset Tests")
  class ByteOffsetTests {

    @Test
    @DisplayName("CorruptionException handles zero byte offset")
    void handlesZeroByteOffset() {
      CorruptionException exception =
          new CorruptionException(
              "message", "segment.log", 0, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertEquals(0, exception.byteOffset());
    }

    @Test
    @DisplayName("CorruptionException handles large byte offset")
    void handlesLargeByteOffset() {
      long offset = Long.MAX_VALUE;
      CorruptionException exception =
          new CorruptionException(
              "message", "segment.log", offset, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertEquals(offset, exception.byteOffset());
    }

    @Test
    @DisplayName("CorruptionException handles negative byte offset")
    void handlesNegativeByteOffset() {
      long offset = -1L;
      CorruptionException exception =
          new CorruptionException(
              "message", "segment.log", offset, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertEquals(offset, exception.byteOffset());
    }

    @Test
    @DisplayName("CorruptionException various byte offsets")
    void variousByteOffsets() {
      long[] offsets = {0, 1, 48, 1024, 65536, 1048576, Long.MAX_VALUE};
      for (long offset : offsets) {
        CorruptionException exception =
            new CorruptionException(
                "message", "segment.log", offset, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
        assertEquals(offset, exception.byteOffset());
      }
    }
  }

  @Nested
  @DisplayName("Corruption Value Tests")
  class CorruptionValueTests {

    @Test
    @DisplayName("CorruptionException handles zero computed value")
    void handlesZeroComputedValue() {
      CorruptionException exception =
          new CorruptionException(
              "message", "segment.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0L, 0xDEADBEEFL);
      assertEquals(0L, exception.computedValue());
    }

    @Test
    @DisplayName("CorruptionException handles zero expected value")
    void handlesZeroExpectedValue() {
      CorruptionException exception =
          new CorruptionException(
              "message", "segment.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x12345678L, 0L);
      assertEquals(0L, exception.expectedValue());
    }

    @Test
    @DisplayName("CorruptionException handles max values")
    void handlesMaxValues() {
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              Long.MAX_VALUE,
              Long.MAX_VALUE);
      assertEquals(Long.MAX_VALUE, exception.computedValue());
      assertEquals(Long.MAX_VALUE, exception.expectedValue());
    }

    @Test
    @DisplayName("CorruptionException handles negative values")
    void handlesNegativeValues() {
      CorruptionException exception =
          new CorruptionException(
              "message", "segment.log", 100, CorruptionType.HEADER_CRC_MISMATCH, -1L, -100L);
      assertEquals(-1L, exception.computedValue());
      assertEquals(-100L, exception.expectedValue());
    }
  }

  @Nested
  @DisplayName("Corruption Type Coverage Tests")
  class CorruptionTypeCoverageTests {

    @Test
    @DisplayName("CorruptionException works with HEADER_CRC_MISMATCH type")
    void worksWithHeaderCrcMismatchType() {
      CorruptionException exception =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertEquals(CorruptionType.HEADER_CRC_MISMATCH, exception.corruptionType());
    }

    @Test
    @DisplayName("CorruptionException works with FOOTER_CRC_MISMATCH type")
    void worksWithFooterCrcMismatchType() {
      CorruptionException exception =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.FOOTER_CRC_MISMATCH, 0x1L, 0x2L);
      assertEquals(CorruptionType.FOOTER_CRC_MISMATCH, exception.corruptionType());
    }

    @Test
    @DisplayName("CorruptionException works with INVALID_MAGIC type")
    void worksWithInvalidMagicType() {
      CorruptionException exception =
          new CorruptionException("msg", "seg.log", 100, CorruptionType.INVALID_MAGIC, 0x1L, 0x2L);
      assertEquals(CorruptionType.INVALID_MAGIC, exception.corruptionType());
    }

    @Test
    @DisplayName("CorruptionException works with INVALID_FOOTER_MARKER type")
    void worksWithInvalidFooterMarkerType() {
      CorruptionException exception =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.INVALID_FOOTER_MARKER, 0x1L, 0x2L);
      assertEquals(CorruptionType.INVALID_FOOTER_MARKER, exception.corruptionType());
    }

    @Test
    @DisplayName("CorruptionException works with ENTRY_CRC_MISMATCH type")
    void worksWithEntryCrcMismatchType() {
      CorruptionException exception =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.ENTRY_CRC_MISMATCH, 0x1L, 0x2L);
      assertEquals(CorruptionType.ENTRY_CRC_MISMATCH, exception.corruptionType());
    }

    @Test
    @DisplayName("CorruptionException works with UNKNOWN corruption type")
    void worksWithUnknownType() {
      CorruptionException exception =
          new CorruptionException("msg", "seg.log", 100, CorruptionType.UNKNOWN, 0x1L, 0x2L);
      assertEquals(CorruptionType.UNKNOWN, exception.corruptionType());
    }

    @ParameterizedTest
    @EnumSource(CorruptionType.class)
    @DisplayName("CorruptionException works with all CorruptionType values")
    void worksWithAllCorruptionTypes(CorruptionType type) {
      CorruptionException exception =
          new CorruptionException("msg", "seg.log", 100, type, 0x1L, 0x2L);
      assertEquals(type, exception.corruptionType());
    }

    @ParameterizedTest
    @EnumSource(CorruptionType.class)
    @DisplayName("CorruptionException errorTypeCode includes each corruption type")
    void errorTypeCodeIncludesEachType(CorruptionType type) {
      CorruptionException exception =
          new CorruptionException("msg", "seg.log", 100, type, 0x1L, 0x2L);
      String code = exception.errorTypeCode();
      assertTrue(code.startsWith("CORRUPTION_"));
      assertTrue(code.contains(type.name()));
    }

    @ParameterizedTest
    @EnumSource(CorruptionType.class)
    @DisplayName("CorruptionType enum description() method has non-empty values")
    void descriptionMethodWorks(CorruptionType type) {
      assertNotNull(type.description());
      assertFalse(type.description().isEmpty());
    }
  }

  @Nested
  @DisplayName("Inheritance Tests")
  class InheritanceTests {

    @Test
    @DisplayName("CorruptionException extends WalException")
    void extendsWalException() {
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      assertTrue(exception instanceof WalException);
    }

    @Test
    @DisplayName("CorruptionException extends IOException")
    void extendsIOException() {
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      assertTrue(exception instanceof java.io.IOException);
    }

    @Test
    @DisplayName("CorruptionException can be caught as WalException")
    void canBeCaughtAsWalException() {
      assertThrows(
          WalException.class,
          () -> {
            throw new CorruptionException(
                "message",
                "segment.log",
                100,
                CorruptionType.HEADER_CRC_MISMATCH,
                0x12345678L,
                0xDEADBEEFL);
          });
    }

    @Test
    @DisplayName("CorruptionException can be caught as IOException")
    void canBeCaughtAsIOException() {
      assertThrows(
          java.io.IOException.class,
          () -> {
            throw new CorruptionException(
                "message",
                "segment.log",
                100,
                CorruptionType.HEADER_CRC_MISMATCH,
                0x12345678L,
                0xDEADBEEFL);
          });
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("CorruptionException toString does not throw")
    void toStringDoesNotThrow() {
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      assertDoesNotThrow(() -> exception.toString());
    }

    @Test
    @DisplayName("CorruptionException getters are consistent")
    void gettersAreConsistent() {
      CorruptionException exception =
          new CorruptionException(
              "message",
              "segment.log",
              100,
              CorruptionType.HEADER_CRC_MISMATCH,
              0x12345678L,
              0xDEADBEEFL);

      assertEquals("segment.log", exception.segmentName());
      assertEquals("segment.log", exception.segmentName());
      assertEquals(100, exception.byteOffset());
      assertEquals(100, exception.byteOffset());
    }

    @Test
    @DisplayName("CorruptionException can be created with identical values")
    void canBeCreatedWithIdenticalValues() {
      CorruptionException ex1 =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x1L);
      CorruptionException ex2 =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x1L);

      assertEquals(ex1.computedValue(), ex2.computedValue());
      assertEquals(ex1.expectedValue(), ex2.expectedValue());
    }
  }
}
