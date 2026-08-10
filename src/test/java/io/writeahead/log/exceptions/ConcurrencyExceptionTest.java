package io.writeahead.log.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.exceptions.ConcurrencyErrorType;
import io.writeahead.log.enums.exceptions.ErrorContext;
import io.writeahead.log.enums.exceptions.ErrorRecoveryAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("ConcurrencyException Tests")
class ConcurrencyExceptionTest {

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("ConcurrencyException stores message correctly")
    void storesMessageCorrectly() {
      String message = "Concurrent write detected";
      ConcurrencyException exception =
          new ConcurrencyException(message, ConcurrencyErrorType.LOCK_TIMEOUT);

      assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("ConcurrencyException stores ConcurrencyErrorType correctly")
    void storesConcurrencyErrorTypeCorrectly() {
      ConcurrencyErrorType type = ConcurrencyErrorType.LOCK_TIMEOUT;
      ConcurrencyException exception = new ConcurrencyException("message", type);

      assertEquals(type, exception.errorType());
    }

    @Test
    @DisplayName("ConcurrencyException creates with various ConcurrencyErrorTypes")
    void createsWithVariousConcurrencyErrorTypes() {
      for (ConcurrencyErrorType type : ConcurrencyErrorType.values()) {
        ConcurrencyException exception = new ConcurrencyException("message", type);
        assertEquals(type, exception.errorType());
      }
    }
  }

  @Nested
  @DisplayName("Transient Behavior Tests")
  class TransientBehaviorTests {

    @Test
    @DisplayName("ConcurrencyException isTransient returns true")
    void isTransientReturnsTrue() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      assertTrue(exception.isTransient());
    }

    @Test
    @DisplayName("ConcurrencyException is always transient")
    void isAlwaysTransient() {
      ConcurrencyException ex1 =
          new ConcurrencyException("msg1", ConcurrencyErrorType.LOCK_TIMEOUT);
      ConcurrencyException ex2 = new ConcurrencyException("msg2", ConcurrencyErrorType.INTERRUPTED);

      assertTrue(ex1.isTransient());
      assertTrue(ex2.isTransient());
    }
  }

  @Nested
  @DisplayName("Data Loss Indication Tests")
  class DataLossTests {

    @Test
    @DisplayName("ConcurrencyException indicatesDataLoss returns false")
    void indicatesDataLossReturnsFalse() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      assertFalse(exception.indicatesDataLoss());
    }

    @Test
    @DisplayName("ConcurrencyException never indicates data loss")
    void neverIndicatesDataLoss() {
      ConcurrencyException ex1 =
          new ConcurrencyException("msg1", ConcurrencyErrorType.LOCK_TIMEOUT);
      ConcurrencyException ex2 = new ConcurrencyException("msg2", ConcurrencyErrorType.INTERRUPTED);

      assertFalse(ex1.indicatesDataLoss());
      assertFalse(ex2.indicatesDataLoss());
    }
  }

  @Nested
  @DisplayName("Recovery Action Tests")
  class RecoveryActionTests {

    @Test
    @DisplayName("ConcurrencyException suggests FAIL_AND_RETRY_OPERATION action")
    void suggestsFailAndRetryOperationAction() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      assertEquals(ErrorRecoveryAction.FAIL_AND_RETRY_OPERATION, exception.suggestedAction());
    }

    @Test
    @DisplayName("ConcurrencyException always suggests fail and retry")
    void alwaysSuggestsFailAndRetry() {
      ConcurrencyException ex1 =
          new ConcurrencyException("msg1", ConcurrencyErrorType.LOCK_TIMEOUT);
      ConcurrencyException ex2 = new ConcurrencyException("msg2", ConcurrencyErrorType.INTERRUPTED);

      assertEquals(ErrorRecoveryAction.FAIL_AND_RETRY_OPERATION, ex1.suggestedAction());
      assertEquals(ErrorRecoveryAction.FAIL_AND_RETRY_OPERATION, ex2.suggestedAction());
    }
  }

  @Nested
  @DisplayName("Error Context Tests")
  class ErrorContextTests {

    @Test
    @DisplayName("ConcurrencyException uses CONCURRENCY context")
    void usesConcurrencyContext() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      assertEquals(ErrorContext.CONCURRENCY, exception.context());
    }

    @Test
    @DisplayName("ConcurrencyException always uses CONCURRENCY context")
    void alwaysUsesConcurrencyContext() {
      ConcurrencyException ex1 =
          new ConcurrencyException("msg1", ConcurrencyErrorType.LOCK_TIMEOUT);
      ConcurrencyException ex2 = new ConcurrencyException("msg2", ConcurrencyErrorType.INTERRUPTED);

      assertEquals(ErrorContext.CONCURRENCY, ex1.context());
      assertEquals(ErrorContext.CONCURRENCY, ex2.context());
    }
  }

  @Nested
  @DisplayName("Error Type Code Tests")
  class ErrorTypeCodeTests {

    @Test
    @DisplayName("ConcurrencyException errorTypeCode includes CONCURRENCY_ prefix")
    void errorTypeCodeIncludesConcurrencyPrefix() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      String code = exception.errorTypeCode();
      assertTrue(code.startsWith("CONCURRENCY_"));
    }

    @Test
    @DisplayName("ConcurrencyException errorTypeCode includes error type")
    void errorTypeCodeIncludesErrorType() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      String code = exception.errorTypeCode();
      assertTrue(code.contains("LOCK_TIMEOUT"));
    }

    @Test
    @DisplayName("ConcurrencyException errorTypeCode reflects error type changes")
    void errorTypeCodeReflectsErrorTypeChanges() {
      ConcurrencyException ex1 = new ConcurrencyException("msg", ConcurrencyErrorType.LOCK_TIMEOUT);
      ConcurrencyException ex2 = new ConcurrencyException("msg", ConcurrencyErrorType.INTERRUPTED);

      assertNotEquals(ex1.errorTypeCode(), ex2.errorTypeCode());
      assertTrue(ex1.errorTypeCode().contains("LOCK_TIMEOUT"));
      assertTrue(ex2.errorTypeCode().contains("INTERRUPTED"));
    }
  }

  @Nested
  @DisplayName("toString() Format Tests")
  class ToStringTests {

    @Test
    @DisplayName("ConcurrencyException toString includes error type code")
    void toStringIncludesErrorTypeCode() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      String result = exception.toString();
      assertTrue(result.contains("CONCURRENCY_"));
    }

    @Test
    @DisplayName("ConcurrencyException toString includes message")
    void toStringIncludesMessage() {
      String message = "Race condition detected";
      ConcurrencyException exception =
          new ConcurrencyException(message, ConcurrencyErrorType.LOCK_TIMEOUT);

      String result = exception.toString();
      assertTrue(result.contains(message));
    }

    @Test
    @DisplayName("ConcurrencyException toString includes context")
    void toStringIncludesContext() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      String result = exception.toString();
      assertTrue(result.contains("CONCURRENCY"));
    }

    @Test
    @DisplayName("ConcurrencyException toString includes recovery action")
    void toStringIncludesRecoveryAction() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      String result = exception.toString();
      assertTrue(result.contains("FAIL_AND_RETRY_OPERATION"));
    }

    @Test
    @DisplayName("ConcurrencyException toString includes operation")
    void toStringIncludesOperation() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      String result = exception.toString();
      assertTrue(result.contains("concurrent operation"));
    }
  }

  @Nested
  @DisplayName("ConcurrencyErrorType Coverage Tests")
  class ConcurrencyErrorTypeCoverageTests {

    @Test
    @DisplayName("ConcurrencyException works with all ConcurrencyErrorType values")
    void worksWithAllConcurrencyErrorTypeValues() {
      ConcurrencyErrorType[] types = ConcurrencyErrorType.values();
      assertTrue(types.length > 0, "ConcurrencyErrorType should have at least one value");

      for (ConcurrencyErrorType type : types) {
        ConcurrencyException exception = new ConcurrencyException("message", type);
        assertEquals(type, exception.errorType());
      }
    }

    @Test
    @DisplayName("ConcurrencyException distinguishes between error types")
    void distinguishesBetweenErrorTypes() {
      ConcurrencyErrorType[] types = ConcurrencyErrorType.values();
      if (types.length < 2) return;

      ConcurrencyException ex1 = new ConcurrencyException("msg", types[0]);
      ConcurrencyException ex2 = new ConcurrencyException("msg", types[1]);

      assertNotEquals(ex1.errorTypeCode(), ex2.errorTypeCode());
    }

    @Test
    @DisplayName("ConcurrencyException works with LOCK_TIMEOUT type")
    void worksWithLockTimeoutType() {
      ConcurrencyException exception =
          new ConcurrencyException("msg", ConcurrencyErrorType.LOCK_TIMEOUT);
      assertEquals(ConcurrencyErrorType.LOCK_TIMEOUT, exception.errorType());
    }

    @Test
    @DisplayName("ConcurrencyException works with INTERRUPTED type")
    void worksWithInterruptedType() {
      ConcurrencyException exception =
          new ConcurrencyException("msg", ConcurrencyErrorType.INTERRUPTED);
      assertEquals(ConcurrencyErrorType.INTERRUPTED, exception.errorType());
    }

    @Test
    @DisplayName("ConcurrencyException works with DEADLOCK type")
    void worksWithDeadlockType() {
      ConcurrencyException exception =
          new ConcurrencyException("msg", ConcurrencyErrorType.DEADLOCK);
      assertEquals(ConcurrencyErrorType.DEADLOCK, exception.errorType());
    }

    @Test
    @DisplayName("ConcurrencyException works with UNKNOWN type")
    void worksWithUnknownType() {
      ConcurrencyException exception =
          new ConcurrencyException("msg", ConcurrencyErrorType.UNKNOWN);
      assertEquals(ConcurrencyErrorType.UNKNOWN, exception.errorType());
    }

    @ParameterizedTest
    @EnumSource(ConcurrencyErrorType.class)
    @DisplayName("ConcurrencyException errorTypeCode includes each error type")
    void errorTypeCodeIncludesEachType(ConcurrencyErrorType type) {
      ConcurrencyException exception = new ConcurrencyException("msg", type);
      String code = exception.errorTypeCode();
      assertTrue(code.startsWith("CONCURRENCY_"));
      assertTrue(code.contains(type.name()));
    }

    @ParameterizedTest
    @EnumSource(ConcurrencyErrorType.class)
    @DisplayName("ConcurrencyErrorType enum description() method has non-empty values")
    void descriptionMethodWorks(ConcurrencyErrorType type) {
      assertNotNull(type.description());
      assertFalse(type.description().isEmpty());
    }
  }

  @Nested
  @DisplayName("Inheritance Tests")
  class InheritanceTests {

    @Test
    @DisplayName("ConcurrencyException extends WalException")
    void extendsWalException() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      assertTrue(exception instanceof WalException);
    }

    @Test
    @DisplayName("ConcurrencyException extends IOException")
    void extendsIOException() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      assertTrue(exception instanceof java.io.IOException);
    }

    @Test
    @DisplayName("ConcurrencyException can be caught as WalException")
    void canBeCaughtAsWalException() {
      assertThrows(
          WalException.class,
          () -> {
            throw new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);
          });
    }

    @Test
    @DisplayName("ConcurrencyException can be caught as IOException")
    void canBeCaughtAsIOException() {
      assertThrows(
          java.io.IOException.class,
          () -> {
            throw new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);
          });
    }
  }

  @Nested
  @DisplayName("Message Parameter Tests")
  class MessageParameterTests {

    @Test
    @DisplayName("ConcurrencyException handles null message")
    void handlesNullMessage() {
      ConcurrencyException exception =
          new ConcurrencyException(null, ConcurrencyErrorType.LOCK_TIMEOUT);
      assertNull(exception.getMessage());
    }

    @Test
    @DisplayName("ConcurrencyException handles empty message")
    void handlesEmptyMessage() {
      ConcurrencyException exception =
          new ConcurrencyException("", ConcurrencyErrorType.LOCK_TIMEOUT);
      assertEquals("", exception.getMessage());
    }

    @Test
    @DisplayName("ConcurrencyException handles long message")
    void handlesLongMessage() {
      String longMessage = "x".repeat(5000);
      ConcurrencyException exception =
          new ConcurrencyException(longMessage, ConcurrencyErrorType.LOCK_TIMEOUT);
      assertEquals(longMessage, exception.getMessage());
    }

    @Test
    @DisplayName("ConcurrencyException handles message with special characters")
    void handlesMessageWithSpecialCharacters() {
      String message = "Error: 日本語 テスト \n\t\r concurrent";
      ConcurrencyException exception =
          new ConcurrencyException(message, ConcurrencyErrorType.LOCK_TIMEOUT);
      assertEquals(message, exception.getMessage());
    }
  }

  @Nested
  @DisplayName("Timestamp Tests")
  class TimestampTests {

    @Test
    @DisplayName("ConcurrencyException captures timestamp")
    void capturesTimestamp() {
      long before = System.currentTimeMillis();
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);
      long after = System.currentTimeMillis();

      long timestamp = exception.timestamp();
      assertTrue(timestamp >= before);
      assertTrue(timestamp <= after + 1);
    }

    @Test
    @DisplayName("ConcurrencyException timestamp is always positive")
    void timestampIsAlwaysPositive() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);
      assertTrue(exception.timestamp() > 0);
    }

    @Test
    @DisplayName("ConcurrencyException timestamps are ordered")
    void timestampsAreOrdered() throws InterruptedException {
      ConcurrencyException ex1 =
          new ConcurrencyException("msg1", ConcurrencyErrorType.LOCK_TIMEOUT);
      Thread.sleep(1);
      ConcurrencyException ex2 = new ConcurrencyException("msg2", ConcurrencyErrorType.INTERRUPTED);

      assertTrue(ex2.timestamp() >= ex1.timestamp());
    }
  }

  @Nested
  @DisplayName("Consistency Tests")
  class ConsistencyTests {

    @Test
    @DisplayName("ConcurrencyException methods return consistent values")
    void methodsReturnConsistentValues() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      assertTrue(exception.isTransient());
      assertTrue(exception.isTransient());

      assertFalse(exception.indicatesDataLoss());
      assertFalse(exception.indicatesDataLoss());

      assertTrue(exception.errorTypeCode().startsWith("CONCURRENCY_"));
      assertTrue(exception.errorTypeCode().startsWith("CONCURRENCY_"));
    }

    @Test
    @DisplayName("ConcurrencyException context remains unchanged")
    void contextRemainsUnchanged() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      ErrorContext context1 = exception.context();
      ErrorContext context2 = exception.context();

      assertEquals(context1, context2);
    }

    @Test
    @DisplayName("ConcurrencyException error type remains unchanged")
    void errorTypeRemainsUnchanged() {
      ConcurrencyErrorType type = ConcurrencyErrorType.LOCK_TIMEOUT;
      ConcurrencyException exception = new ConcurrencyException("message", type);

      assertEquals(type, exception.errorType());
      assertEquals(type, exception.errorType());
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("ConcurrencyException toString does not throw")
    void toStringDoesNotThrow() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      assertDoesNotThrow(() -> exception.toString());
    }

    @Test
    @DisplayName("ConcurrencyException can be created multiple times")
    void canBeCreatedMultipleTimes() {
      ConcurrencyException ex1 =
          new ConcurrencyException("msg1", ConcurrencyErrorType.LOCK_TIMEOUT);
      ConcurrencyException ex2 = new ConcurrencyException("msg2", ConcurrencyErrorType.INTERRUPTED);

      assertTrue(ex1.isTransient());
      assertTrue(ex2.isTransient());
    }

    @Test
    @DisplayName("ConcurrencyException getters are consistent")
    void gettersAreConsistent() {
      ConcurrencyErrorType type = ConcurrencyErrorType.LOCK_TIMEOUT;
      ConcurrencyException exception = new ConcurrencyException("message", type);

      assertEquals(type, exception.errorType());
      assertEquals(type, exception.errorType());
    }
  }

  @Nested
  @DisplayName("Operation Description Tests")
  class OperationDescriptionTests {

    @Test
    @DisplayName("ConcurrencyException always has concurrent operation as operation")
    void alwaysHasConcurrentOperationAsOperation() {
      ConcurrencyException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);

      String operation = exception.operationDescription();
      assertEquals("concurrent operation", operation);
    }

    @Test
    @DisplayName("ConcurrencyException operation is consistent across instances")
    void operationIsConsistent() {
      ConcurrencyException ex1 =
          new ConcurrencyException("msg1", ConcurrencyErrorType.LOCK_TIMEOUT);
      ConcurrencyException ex2 = new ConcurrencyException("msg2", ConcurrencyErrorType.INTERRUPTED);

      assertEquals(ex1.operationDescription(), ex2.operationDescription());
      assertEquals("concurrent operation", ex1.operationDescription());
    }
  }
}
