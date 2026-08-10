package io.writeahead.log.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.exceptions.ErrorContext;
import io.writeahead.log.enums.exceptions.ErrorRecoveryAction;
import io.writeahead.log.enums.strategies.RecoveryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("RecoveryException Tests")
class RecoveryExceptionTest {

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("RecoveryException stores message correctly")
    void storesMessageCorrectly() {
      String message = "Segment file smaller than minimum size";
      RecoveryException exception = new RecoveryException(message, RecoveryType.SEGMENT_TOO_SMALL);

      assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("RecoveryException stores RecoveryType correctly")
    void storesRecoveryTypeCorrectly() {
      RecoveryType type = RecoveryType.SEGMENT_TOO_SMALL;
      RecoveryException exception = new RecoveryException("message", type);

      assertEquals(type, exception.recoveryType());
    }

    @Test
    @DisplayName("RecoveryException creates with various RecoveryTypes")
    void createsWithVariousRecoveryTypes() {
      for (RecoveryType type : RecoveryType.values()) {
        RecoveryException exception = new RecoveryException("message", type);
        assertEquals(type, exception.recoveryType());
      }
    }
  }

  @Nested
  @DisplayName("Transient Behavior Tests")
  class TransientBehaviorTests {

    @Test
    @DisplayName("RecoveryException isTransient returns false")
    void isTransientReturnsFalse() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      assertFalse(exception.isTransient());
    }

    @Test
    @DisplayName("RecoveryException is never transient")
    void isNeverTransient() {
      RecoveryException ex1 = new RecoveryException("msg1", RecoveryType.SEGMENT_TOO_SMALL);
      RecoveryException ex2 = new RecoveryException("msg2", RecoveryType.PARTIAL_ENTRY_AT_EOF);

      assertFalse(ex1.isTransient());
      assertFalse(ex2.isTransient());
    }
  }

  @Nested
  @DisplayName("Data Loss Indication Tests")
  class DataLossTests {

    @Test
    @DisplayName("RecoveryException indicatesDataLoss returns true")
    void indicatesDataLossReturnsTrue() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      assertTrue(exception.indicatesDataLoss());
    }

    @Test
    @DisplayName("RecoveryException always indicates data loss")
    void alwaysIndicatesDataLoss() {
      RecoveryException ex1 = new RecoveryException("msg1", RecoveryType.SEGMENT_TOO_SMALL);
      RecoveryException ex2 = new RecoveryException("msg2", RecoveryType.PARTIAL_ENTRY_AT_EOF);

      assertTrue(ex1.indicatesDataLoss());
      assertTrue(ex2.indicatesDataLoss());
    }
  }

  @Nested
  @DisplayName("Recovery Action Tests")
  class RecoveryActionTests {

    @Test
    @DisplayName("RecoveryException suggests SKIP_AND_CONTINUE action")
    void suggestsSkipAndContinueAction() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      assertEquals(ErrorRecoveryAction.SKIP_AND_CONTINUE, exception.suggestedAction());
    }

    @Test
    @DisplayName("RecoveryException always suggests skip and continue")
    void alwaysSuggestsSkipAndContinue() {
      RecoveryException ex1 = new RecoveryException("msg1", RecoveryType.SEGMENT_TOO_SMALL);
      RecoveryException ex2 = new RecoveryException("msg2", RecoveryType.PARTIAL_ENTRY_AT_EOF);

      assertEquals(ErrorRecoveryAction.SKIP_AND_CONTINUE, ex1.suggestedAction());
      assertEquals(ErrorRecoveryAction.SKIP_AND_CONTINUE, ex2.suggestedAction());
    }
  }

  @Nested
  @DisplayName("Error Context Tests")
  class ErrorContextTests {

    @Test
    @DisplayName("RecoveryException uses RECOVERY_FAILURE context")
    void usesRecoveryFailureContext() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      assertEquals(ErrorContext.RECOVERY_FAILURE, exception.context());
    }

    @Test
    @DisplayName("RecoveryException always uses RECOVERY_FAILURE context")
    void alwaysUsesRecoveryFailureContext() {
      RecoveryException ex1 = new RecoveryException("msg1", RecoveryType.SEGMENT_TOO_SMALL);
      RecoveryException ex2 = new RecoveryException("msg2", RecoveryType.PARTIAL_ENTRY_AT_EOF);

      assertEquals(ErrorContext.RECOVERY_FAILURE, ex1.context());
      assertEquals(ErrorContext.RECOVERY_FAILURE, ex2.context());
    }
  }

  @Nested
  @DisplayName("Error Type Code Tests")
  class ErrorTypeCodeTests {

    @Test
    @DisplayName("RecoveryException errorTypeCode includes RECOVERY prefix")
    void errorTypeCodeIncludesRecoveryPrefix() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      String code = exception.errorTypeCode();
      assertTrue(code.startsWith("RECOVERY_"));
    }

    @Test
    @DisplayName("RecoveryException errorTypeCode includes recovery type")
    void errorTypeCodeIncludesRecoveryType() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      String code = exception.errorTypeCode();
      assertTrue(code.contains("SEGMENT_TOO_SMALL"));
    }

    @Test
    @DisplayName("RecoveryException errorTypeCode reflects recovery type changes")
    void errorTypeCodeReflectsRecoveryTypeChanges() {
      RecoveryException ex1 = new RecoveryException("msg", RecoveryType.SEGMENT_TOO_SMALL);
      RecoveryException ex2 = new RecoveryException("msg", RecoveryType.PARTIAL_ENTRY_AT_EOF);

      assertNotEquals(ex1.errorTypeCode(), ex2.errorTypeCode());
      assertTrue(ex1.errorTypeCode().contains("SEGMENT_TOO_SMALL"));
      assertTrue(ex2.errorTypeCode().contains("PARTIAL_ENTRY_AT_EOF"));
    }
  }

  @Nested
  @DisplayName("toString() Format Tests")
  class ToStringTests {

    @Test
    @DisplayName("RecoveryException toString includes error type code")
    void toStringIncludesErrorTypeCode() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      String result = exception.toString();
      assertTrue(result.contains("RECOVERY_"));
    }

    @Test
    @DisplayName("RecoveryException toString includes message")
    void toStringIncludesMessage() {
      String message = "Metadata recovery failed";
      RecoveryException exception = new RecoveryException(message, RecoveryType.SEGMENT_TOO_SMALL);

      String result = exception.toString();
      assertTrue(result.contains(message));
    }

    @Test
    @DisplayName("RecoveryException toString includes context")
    void toStringIncludesContext() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      String result = exception.toString();
      assertTrue(result.contains("RECOVERY_FAILURE"));
    }

    @Test
    @DisplayName("RecoveryException toString includes recovery action")
    void toStringIncludesRecoveryAction() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      String result = exception.toString();
      assertTrue(result.contains("SKIP_AND_CONTINUE"));
    }

    @Test
    @DisplayName("RecoveryException toString includes operation")
    void toStringIncludesOperation() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      String result = exception.toString();
      assertTrue(result.contains("recovery"));
    }
  }

  @Nested
  @DisplayName("Recovery Type Coverage Tests")
  class RecoveryTypeCoverageTests {

    @Test
    @DisplayName("RecoveryException works with SEGMENT_TOO_SMALL type")
    void worksWithSegmentTooSmallType() {
      RecoveryException exception = new RecoveryException("msg", RecoveryType.SEGMENT_TOO_SMALL);
      assertEquals(RecoveryType.SEGMENT_TOO_SMALL, exception.recoveryType());
    }

    @Test
    @DisplayName("RecoveryException works with PARTIAL_ENTRY_AT_EOF type")
    void worksWithPartialEntryAtEofType() {
      RecoveryException exception = new RecoveryException("msg", RecoveryType.PARTIAL_ENTRY_AT_EOF);
      assertEquals(RecoveryType.PARTIAL_ENTRY_AT_EOF, exception.recoveryType());
    }

    @Test
    @DisplayName("RecoveryException works with MISSING_SEGMENT_FILE type")
    void worksWithMissingSegmentFileType() {
      RecoveryException exception = new RecoveryException("msg", RecoveryType.MISSING_SEGMENT_FILE);
      assertEquals(RecoveryType.MISSING_SEGMENT_FILE, exception.recoveryType());
    }

    @Test
    @DisplayName("RecoveryException works with UNREADABLE_SEGMENT type")
    void worksWithUnreadableSegmentType() {
      RecoveryException exception = new RecoveryException("msg", RecoveryType.UNREADABLE_SEGMENT);
      assertEquals(RecoveryType.UNREADABLE_SEGMENT, exception.recoveryType());
    }

    @Test
    @DisplayName("RecoveryException works with INCOMPLETE_SEGMENT type")
    void worksWithIncompleteSegmentType() {
      RecoveryException exception = new RecoveryException("msg", RecoveryType.INCOMPLETE_SEGMENT);
      assertEquals(RecoveryType.INCOMPLETE_SEGMENT, exception.recoveryType());
    }

    @Test
    @DisplayName("RecoveryException works with UNKNOWN type")
    void worksWithUnknownType() {
      RecoveryException exception = new RecoveryException("msg", RecoveryType.UNKNOWN);
      assertEquals(RecoveryType.UNKNOWN, exception.recoveryType());
    }

    @ParameterizedTest
    @EnumSource(RecoveryType.class)
    @DisplayName("RecoveryException works with all RecoveryType values")
    void worksWithAllRecoveryTypeValuesParameterized(RecoveryType type) {
      RecoveryException exception = new RecoveryException("message", type);
      assertEquals(type, exception.recoveryType());
    }

    @ParameterizedTest
    @EnumSource(RecoveryType.class)
    @DisplayName("RecoveryException errorTypeCode includes each recovery type")
    void errorTypeCodeIncludesEachType(RecoveryType type) {
      RecoveryException exception = new RecoveryException("msg", type);
      String code = exception.errorTypeCode();
      assertTrue(code.startsWith("RECOVERY_"));
      assertTrue(code.contains(type.name()));
    }

    @ParameterizedTest
    @EnumSource(RecoveryType.class)
    @DisplayName("RecoveryType enum description() method has non-empty values")
    void descriptionMethodWorks(RecoveryType type) {
      assertNotNull(type.description());
      assertFalse(type.description().isEmpty());
    }
  }

  @Nested
  @DisplayName("Inheritance Tests")
  class InheritanceTests {

    @Test
    @DisplayName("RecoveryException extends WalException")
    void extendsWalException() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      assertTrue(exception instanceof WalException);
    }

    @Test
    @DisplayName("RecoveryException extends IOException")
    void extendsIOException() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      assertTrue(exception instanceof java.io.IOException);
    }

    @Test
    @DisplayName("RecoveryException can be caught as WalException")
    void canBeCaughtAsWalException() {
      assertThrows(
          WalException.class,
          () -> {
            throw new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);
          });
    }

    @Test
    @DisplayName("RecoveryException can be caught as IOException")
    void canBeCaughtAsIOException() {
      assertThrows(
          java.io.IOException.class,
          () -> {
            throw new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);
          });
    }
  }

  @Nested
  @DisplayName("Message Parameter Tests")
  class MessageParameterTests {

    @Test
    @DisplayName("RecoveryException handles null message")
    void handlesNullMessage() {
      RecoveryException exception = new RecoveryException(null, RecoveryType.SEGMENT_TOO_SMALL);
      assertNull(exception.getMessage());
    }

    @Test
    @DisplayName("RecoveryException handles empty message")
    void handlesEmptyMessage() {
      RecoveryException exception = new RecoveryException("", RecoveryType.SEGMENT_TOO_SMALL);
      assertEquals("", exception.getMessage());
    }

    @Test
    @DisplayName("RecoveryException handles long message")
    void handlesLongMessage() {
      String longMessage = "x".repeat(5000);
      RecoveryException exception =
          new RecoveryException(longMessage, RecoveryType.SEGMENT_TOO_SMALL);
      assertEquals(longMessage, exception.getMessage());
    }

    @Test
    @DisplayName("RecoveryException handles message with special characters")
    void handlesMessageWithSpecialCharacters() {
      String message = "Error: 日本語 テスト \n\t\r %s @recovery";
      RecoveryException exception = new RecoveryException(message, RecoveryType.SEGMENT_TOO_SMALL);
      assertEquals(message, exception.getMessage());
    }
  }

  @Nested
  @DisplayName("Timestamp Tests")
  class TimestampTests {

    @Test
    @DisplayName("RecoveryException captures timestamp")
    void capturesTimestamp() {
      long before = System.currentTimeMillis();
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);
      long after = System.currentTimeMillis();

      long timestamp = exception.timestamp();
      assertTrue(timestamp >= before);
      assertTrue(timestamp <= after + 1);
    }

    @Test
    @DisplayName("RecoveryException timestamp is always positive")
    void timestampIsAlwaysPositive() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);
      assertTrue(exception.timestamp() > 0);
    }

    @Test
    @DisplayName("RecoveryException timestamps are ordered")
    void timestampsAreOrdered() throws InterruptedException {
      RecoveryException ex1 = new RecoveryException("msg1", RecoveryType.SEGMENT_TOO_SMALL);
      Thread.sleep(1);
      RecoveryException ex2 = new RecoveryException("msg2", RecoveryType.PARTIAL_ENTRY_AT_EOF);

      assertTrue(ex2.timestamp() >= ex1.timestamp());
    }
  }

  @Nested
  @DisplayName("Consistency Tests")
  class ConsistencyTests {

    @Test
    @DisplayName("RecoveryException methods return consistent values")
    void methodsReturnConsistentValues() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      assertFalse(exception.isTransient());
      assertFalse(exception.isTransient());

      assertTrue(exception.indicatesDataLoss());
      assertTrue(exception.indicatesDataLoss());

      assertTrue(exception.errorTypeCode().startsWith("RECOVERY_"));
      assertTrue(exception.errorTypeCode().startsWith("RECOVERY_"));
    }

    @Test
    @DisplayName("RecoveryException context remains unchanged")
    void contextRemainsUnchanged() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      ErrorContext context1 = exception.context();
      ErrorContext context2 = exception.context();

      assertEquals(context1, context2);
    }

    @Test
    @DisplayName("RecoveryException recovery type remains unchanged")
    void recoveryTypeRemainsUnchanged() {
      RecoveryType type = RecoveryType.SEGMENT_TOO_SMALL;
      RecoveryException exception = new RecoveryException("message", type);

      assertEquals(type, exception.recoveryType());
      assertEquals(type, exception.recoveryType());
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("RecoveryException toString does not throw")
    void toStringDoesNotThrow() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      assertDoesNotThrow(() -> exception.toString());
    }

    @Test
    @DisplayName("RecoveryException can be created multiple times")
    void canBeCreatedMultipleTimes() {
      RecoveryException ex1 = new RecoveryException("msg1", RecoveryType.SEGMENT_TOO_SMALL);
      RecoveryException ex2 = new RecoveryException("msg2", RecoveryType.PARTIAL_ENTRY_AT_EOF);

      assertTrue(ex1.indicatesDataLoss());
      assertTrue(ex2.indicatesDataLoss());
    }

    @Test
    @DisplayName("RecoveryException getters are consistent")
    void gettersAreConsistent() {
      RecoveryType type = RecoveryType.SEGMENT_TOO_SMALL;
      RecoveryException exception = new RecoveryException("message", type);

      assertEquals(type, exception.recoveryType());
      assertEquals(type, exception.recoveryType());
    }
  }

  @Nested
  @DisplayName("Operation Description Tests")
  class OperationDescriptionTests {

    @Test
    @DisplayName("RecoveryException always has recovery as operation")
    void alwaysHasRecoveryAsOperation() {
      RecoveryException exception =
          new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);

      String operation = exception.operationDescription();
      assertEquals("recovery", operation);
    }

    @Test
    @DisplayName("RecoveryException operation is consistent across instances")
    void operationIsConsistent() {
      RecoveryException ex1 = new RecoveryException("msg1", RecoveryType.SEGMENT_TOO_SMALL);
      RecoveryException ex2 = new RecoveryException("msg2", RecoveryType.PARTIAL_ENTRY_AT_EOF);

      assertEquals(ex1.operationDescription(), ex2.operationDescription());
      assertEquals("recovery", ex1.operationDescription());
    }
  }
}
