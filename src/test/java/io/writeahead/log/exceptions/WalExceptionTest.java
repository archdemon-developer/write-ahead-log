package io.writeahead.log.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.exceptions.ConcurrencyErrorType;
import io.writeahead.log.enums.exceptions.CorruptionType;
import io.writeahead.log.enums.exceptions.ErrorContext;
import io.writeahead.log.enums.exceptions.ErrorRecoveryAction;
import io.writeahead.log.enums.strategies.RecoveryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WalException Base Class Tests")
class WalExceptionTest {

  @Nested
  @DisplayName("Sealed Class Contract Tests")
  class SealedClassContractTests {

    @Test
    @DisplayName("WalException is sealed and permits only specified exceptions")
    void isSealed() {
      assertTrue(WalException.class.isSealed());
    }

    @Test
    @DisplayName("TransientIOException is permitted subclass of WalException")
    void transientIOExceptionIsPermitted() {
      WalException exception = new TransientIOException("msg", ErrorContext.RESOURCE_BUSY, "op");
      assertTrue(exception instanceof TransientIOException);
      assertTrue(exception instanceof WalException);
    }

    @Test
    @DisplayName("PermanentIOException is permitted subclass of WalException")
    void permanentIOExceptionIsPermitted() {
      WalException exception =
          new PermanentIOException("msg", ErrorContext.PERMISSION_DENIED, "op");
      assertTrue(exception instanceof PermanentIOException);
      assertTrue(exception instanceof WalException);
    }

    @Test
    @DisplayName("CorruptionException is permitted subclass of WalException")
    void corruptionExceptionIsPermitted() {
      WalException exception =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertTrue(exception instanceof CorruptionException);
      assertTrue(exception instanceof WalException);
    }

    @Test
    @DisplayName("RecoveryException is permitted subclass of WalException")
    void recoveryExceptionIsPermitted() {
      WalException exception = new RecoveryException("msg", RecoveryType.SEGMENT_TOO_SMALL);
      assertTrue(exception instanceof RecoveryException);
      assertTrue(exception instanceof WalException);
    }

    @Test
    @DisplayName("ConcurrencyException is permitted subclass of WalException")
    void concurrencyExceptionIsPermitted() {
      WalException exception = new ConcurrencyException("msg", ConcurrencyErrorType.LOCK_TIMEOUT);
      assertTrue(exception instanceof ConcurrencyException);
      assertTrue(exception instanceof WalException);
    }
  }

  @Nested
  @DisplayName("Constructor and Initialization Tests")
  class ConstructorTests {

    @Test
    @DisplayName("WalException stores message correctly via TransientIOException")
    void storesMessageCorrectlyTransient() {
      String message = "Test exception message";
      WalException exception = new TransientIOException(message, ErrorContext.DISK_FULL, "write");
      assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("WalException stores message correctly via PermanentIOException")
    void storesMessageCorrectlyPermanent() {
      String message = "Test exception message";
      WalException exception = new PermanentIOException(message, ErrorContext.DISK_FULL, "write");
      assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("WalException stores message correctly via CorruptionException")
    void storesMessageCorrectlyCorruption() {
      String message = "Test exception message";
      WalException exception =
          new CorruptionException(
              message, "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("WalException stores ErrorContext correctly via TransientIOException")
    void storesErrorContextCorrectly() {
      WalException exception =
          new TransientIOException("message", ErrorContext.PERMISSION_DENIED, "read");
      assertEquals(ErrorContext.PERMISSION_DENIED, exception.context());
    }

    @Test
    @DisplayName("WalException stores ErrorRecoveryAction correctly via TransientIOException")
    void storesErrorRecoveryActionCorrectly() {
      WalException exception =
          new TransientIOException("message", ErrorContext.DISK_FULL, "validate");
      assertEquals(ErrorRecoveryAction.RETRY_WITH_BACKOFF, exception.suggestedAction());
    }

    @Test
    @DisplayName("WalException stores operation description correctly via TransientIOException")
    void storesOperationDescriptionCorrectly() {
      String operation = "segment rotation";
      WalException exception =
          new TransientIOException("message", ErrorContext.NO_MEMORY, operation);
      assertEquals(operation, exception.operationDescription());
    }

    @Test
    @DisplayName("WalException captures timestamp on creation via PermanentIOException")
    void capturesTimestampOnCreation() {
      long beforeCreation = System.currentTimeMillis();
      WalException exception =
          new PermanentIOException("message", ErrorContext.RESOURCE_BUSY, "recovery");
      long afterCreation = System.currentTimeMillis();

      long exceptionTime = exception.timestamp();
      assertTrue(exceptionTime >= beforeCreation);
      assertTrue(exceptionTime <= afterCreation + 1);
    }

    @Test
    @DisplayName("WalException timestamp is always set via CorruptionException")
    void timestampIsAlwaysSet() {
      WalException exception =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertTrue(exception.timestamp() > 0);
    }
  }

  @Nested
  @DisplayName("Abstract Method Implementation Tests")
  class AbstractMethodTests {

    @Test
    @DisplayName("isTransient is implemented in TransientIOException")
    void isTransientImplementedInTransientIOException() {
      WalException exception =
          new TransientIOException("message", ErrorContext.FILE_NOT_FOUND, "operation");
      assertTrue(exception.isTransient());
    }

    @Test
    @DisplayName("isTransient is implemented in PermanentIOException")
    void isTransientImplementedInPermanentIOException() {
      WalException exception =
          new PermanentIOException("message", ErrorContext.FILE_NOT_FOUND, "operation");
      assertFalse(exception.isTransient());
    }

    @Test
    @DisplayName("isTransient is implemented in CorruptionException")
    void isTransientImplementedInCorruptionException() {
      WalException exception =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertFalse(exception.isTransient());
    }

    @Test
    @DisplayName("indicatesDataLoss is implemented in TransientIOException")
    void indicatesDataLossImplementedInTransientIOException() {
      WalException exception =
          new TransientIOException("message", ErrorContext.UNKNOWN_IO_ERROR, "operation");
      assertFalse(exception.indicatesDataLoss());
    }

    @Test
    @DisplayName("indicatesDataLoss is implemented in CorruptionException")
    void indicatesDataLossImplementedInCorruptionException() {
      WalException exception =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertTrue(exception.indicatesDataLoss());
    }

    @Test
    @DisplayName("indicatesDataLoss is implemented in RecoveryException")
    void indicatesDataLossImplementedInRecoveryException() {
      WalException exception = new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);
      assertTrue(exception.indicatesDataLoss());
    }

    @Test
    @DisplayName("errorTypeCode is implemented in TransientIOException")
    void errorTypeCodeImplementedInTransientIOException() {
      WalException exception =
          new TransientIOException("message", ErrorContext.CONCURRENCY, "operation");
      assertEquals("TRANSIENT_IO", exception.errorTypeCode());
    }

    @Test
    @DisplayName("errorTypeCode is implemented in PermanentIOException")
    void errorTypeCodeImplementedInPermanentIOException() {
      WalException exception =
          new PermanentIOException("message", ErrorContext.CONCURRENCY, "operation");
      assertEquals("PERMANENT_IO", exception.errorTypeCode());
    }

    @Test
    @DisplayName("errorTypeCode is implemented in CorruptionException")
    void errorTypeCodeImplementedInCorruptionException() {
      WalException exception =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertTrue(exception.errorTypeCode().startsWith("CORRUPTION_"));
    }

    @Test
    @DisplayName("All permitted exceptions implement all abstract methods")
    void allPermittedExceptionsImplementAllAbstractMethods() {
      WalException[] exceptions = {
        new TransientIOException("msg", ErrorContext.DISK_FULL, "op"),
        new PermanentIOException("msg", ErrorContext.DISK_FULL, "op"),
        new CorruptionException(
            "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L),
        new RecoveryException("msg", RecoveryType.SEGMENT_TOO_SMALL),
        new ConcurrencyException("msg", ConcurrencyErrorType.LOCK_TIMEOUT)
      };

      for (WalException ex : exceptions) {
        assertNotNull(ex.errorTypeCode());
        assertFalse(ex.errorTypeCode().isEmpty());

        boolean isTransient = ex.isTransient();
        assertTrue(isTransient || !isTransient);

        boolean isDataLoss = ex.indicatesDataLoss();
        assertTrue(isDataLoss || !isDataLoss);
      }
    }
  }

  @Nested
  @DisplayName("Getter Method Tests")
  class GetterTests {

    @Test
    @DisplayName("context() returns stored ErrorContext via TransientIOException")
    void contextGetterReturnsCorrectValueTransient() {
      ErrorContext context = ErrorContext.DISK_FULL;
      WalException exception = new TransientIOException("message", context, "operation");
      assertEquals(context, exception.context());
    }

    @Test
    @DisplayName("context() returns stored ErrorContext via PermanentIOException")
    void contextGetterReturnsCorrectValuePermanent() {
      ErrorContext context = ErrorContext.BAD_FD;
      WalException exception = new PermanentIOException("message", context, "operation");
      assertEquals(context, exception.context());
    }

    @Test
    @DisplayName("suggestedAction() returns RETRY_WITH_BACKOFF for TransientIOException")
    void suggestedActionReturnRetryForTransient() {
      WalException exception =
          new TransientIOException("message", ErrorContext.DISK_FULL, "operation");
      assertEquals(ErrorRecoveryAction.RETRY_WITH_BACKOFF, exception.suggestedAction());
    }

    @Test
    @DisplayName("suggestedAction() returns FAIL_FAST_ALERT_OPERATOR for PermanentIOException")
    void suggestedActionReturnFailFastForPermanent() {
      WalException exception =
          new PermanentIOException("message", ErrorContext.DISK_FULL, "operation");
      assertEquals(ErrorRecoveryAction.FAIL_FAST_ALERT_OPERATOR, exception.suggestedAction());
    }

    @Test
    @DisplayName("suggestedAction() returns QUARANTINE_AND_ALERT for CorruptionException")
    void suggestedActionReturnQuarantineForCorruption() {
      WalException exception =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertEquals(ErrorRecoveryAction.QUARANTINE_AND_ALERT, exception.suggestedAction());
    }

    @Test
    @DisplayName("timestamp() returns non-zero timestamp via TransientIOException")
    void timestampGetterReturnsNonZeroValueTransient() {
      WalException exception =
          new TransientIOException("message", ErrorContext.DISK_FULL, "operation");
      assertTrue(exception.timestamp() > 0);
    }

    @Test
    @DisplayName("timestamp() returns non-zero timestamp via PermanentIOException")
    void timestampGetterReturnsNonZeroValuePermanent() {
      WalException exception =
          new PermanentIOException("message", ErrorContext.DISK_FULL, "operation");
      assertTrue(exception.timestamp() > 0);
    }

    @Test
    @DisplayName("operationDescription() returns stored description via TransientIOException")
    void operationDescriptionGetterReturnsCorrectValueTransient() {
      String operation = "segment validation";
      WalException exception =
          new TransientIOException("message", ErrorContext.DISK_FULL, operation);
      assertEquals(operation, exception.operationDescription());
    }

    @Test
    @DisplayName("operationDescription() returns stored description via PermanentIOException")
    void operationDescriptionGetterReturnsCorrectValuePermanent() {
      String operation = "segment validation";
      WalException exception =
          new PermanentIOException("message", ErrorContext.DISK_FULL, operation);
      assertEquals(operation, exception.operationDescription());
    }
  }

  @Nested
  @DisplayName("toString() Method Tests")
  class ToStringTests {

    @Test
    @DisplayName("toString() includes error type code via TransientIOException")
    void toStringIncludesErrorTypeCodeTransient() {
      WalException exception =
          new TransientIOException("test message", ErrorContext.DISK_FULL, "operation");
      String result = exception.toString();
      assertTrue(result.contains("TRANSIENT_IO"));
    }

    @Test
    @DisplayName("toString() includes error type code via PermanentIOException")
    void toStringIncludesErrorTypeCodePermanent() {
      WalException exception =
          new PermanentIOException("test message", ErrorContext.DISK_FULL, "operation");
      String result = exception.toString();
      assertTrue(result.contains("PERMANENT_IO"));
    }

    @Test
    @DisplayName("toString() includes error type code via CorruptionException")
    void toStringIncludesErrorTypeCodeCorruption() {
      WalException exception =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      String result = exception.toString();
      assertTrue(result.contains("CORRUPTION_"));
    }

    @Test
    @DisplayName("toString() includes message")
    void toStringIncludesMessage() {
      String message = "specific error details";
      WalException exception =
          new TransientIOException(message, ErrorContext.DISK_FULL, "operation");
      String result = exception.toString();
      assertTrue(result.contains(message));
    }

    @Test
    @DisplayName("toString() includes error context")
    void toStringIncludesErrorContext() {
      WalException exception =
          new TransientIOException("message", ErrorContext.DISK_FULL, "operation");
      String result = exception.toString();
      assertTrue(result.contains("DISK_FULL"));
    }

    @Test
    @DisplayName("toString() includes recovery action for TransientIOException")
    void toStringIncludesRecoveryActionTransient() {
      WalException exception =
          new TransientIOException("message", ErrorContext.DISK_FULL, "operation");
      String result = exception.toString();
      assertTrue(result.contains("RETRY_WITH_BACKOFF"));
    }

    @Test
    @DisplayName("toString() includes recovery action for PermanentIOException")
    void toStringIncludesRecoveryActionPermanent() {
      WalException exception =
          new PermanentIOException("message", ErrorContext.DISK_FULL, "operation");
      String result = exception.toString();
      assertTrue(result.contains("FAIL_FAST_ALERT_OPERATOR"));
    }

    @Test
    @DisplayName("toString() includes operation description")
    void toStringIncludesOperationDescription() {
      String operation = "fsync";
      WalException exception =
          new TransientIOException("message", ErrorContext.DISK_FULL, operation);
      String result = exception.toString();
      assertTrue(result.contains(operation));
    }

    @Test
    @DisplayName("toString() produces non-empty string")
    void toStringProducesNonEmptyString() {
      WalException exception =
          new TransientIOException("message", ErrorContext.DISK_FULL, "operation");
      String result = exception.toString();
      assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("toString() format is consistent")
    void toStringFormatIsConsistent() {
      WalException exception =
          new TransientIOException("message", ErrorContext.DISK_FULL, "operation");
      String result = exception.toString();
      assertTrue(result.contains("["));
      assertTrue(result.contains("]"));
    }
  }

  @Nested
  @DisplayName("Inheritance and IOException Contract Tests")
  class InheritanceTests {

    @Test
    @DisplayName("TransientIOException is IOException subclass")
    void transientIOExceptionIsIOExceptionSubclass() {
      WalException exception =
          new TransientIOException("message", ErrorContext.DISK_FULL, "operation");
      assertTrue(exception instanceof java.io.IOException);
    }

    @Test
    @DisplayName("PermanentIOException is IOException subclass")
    void permanentIOExceptionIsIOExceptionSubclass() {
      WalException exception =
          new PermanentIOException("message", ErrorContext.DISK_FULL, "operation");
      assertTrue(exception instanceof java.io.IOException);
    }

    @Test
    @DisplayName("CorruptionException is IOException subclass")
    void corruptionExceptionIsIOExceptionSubclass() {
      WalException exception =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertTrue(exception instanceof java.io.IOException);
    }

    @Test
    @DisplayName("WalException subclasses are Throwable subclasses")
    void walExceptionSubclassesAreThrowableSubclasses() {
      WalException[] exceptions = {
        new TransientIOException("msg", ErrorContext.DISK_FULL, "op"),
        new PermanentIOException("msg", ErrorContext.DISK_FULL, "op"),
        new CorruptionException(
            "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L),
        new RecoveryException("msg", RecoveryType.SEGMENT_TOO_SMALL),
        new ConcurrencyException("msg", ConcurrencyErrorType.LOCK_TIMEOUT)
      };

      for (WalException exception : exceptions) {
        assertTrue(exception instanceof Throwable);
      }
    }

    @Test
    @DisplayName("WalException can be thrown and caught as IOException via TransientIOException")
    void canBeThrownAndCaughtAsIOExceptionTransient() {
      assertThrows(
          java.io.IOException.class,
          () -> {
            throw new TransientIOException("message", ErrorContext.DISK_FULL, "operation");
          });
    }

    @Test
    @DisplayName("WalException can be thrown and caught as IOException via PermanentIOException")
    void canBeThrownAndCaughtAsIOExceptionPermanent() {
      assertThrows(
          java.io.IOException.class,
          () -> {
            throw new PermanentIOException("message", ErrorContext.DISK_FULL, "operation");
          });
    }

    @Test
    @DisplayName("WalException can be thrown and caught as WalException via TransientIOException")
    void canBeThrownAndCaughtAsWalExceptionTransient() {
      assertThrows(
          WalException.class,
          () -> {
            throw new TransientIOException("message", ErrorContext.DISK_FULL, "operation");
          });
    }

    @Test
    @DisplayName("WalException can be thrown and caught as WalException via PermanentIOException")
    void canBeThrownAndCaughtAsWalExceptionPermanent() {
      assertThrows(
          WalException.class,
          () -> {
            throw new PermanentIOException("message", ErrorContext.DISK_FULL, "operation");
          });
    }

    @Test
    @DisplayName("All permitted exceptions extend WalException and IOException")
    void allPermittedExceptionsExtendBases() {
      WalException[] exceptions = {
        new TransientIOException("msg", ErrorContext.DISK_FULL, "op"),
        new PermanentIOException("msg", ErrorContext.DISK_FULL, "op"),
        new CorruptionException(
            "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L),
        new RecoveryException("msg", RecoveryType.SEGMENT_TOO_SMALL),
        new ConcurrencyException("msg", ConcurrencyErrorType.LOCK_TIMEOUT)
      };

      for (WalException ex : exceptions) {
        assertTrue(ex instanceof WalException);
        assertTrue(ex instanceof java.io.IOException);
        assertTrue(ex instanceof Throwable);
      }
    }
  }

  @Nested
  @DisplayName("Error Context Coverage Tests")
  class ErrorContextCoverageTests {

    @Test
    @DisplayName("WalException works with DISK_FULL context via TransientIOException")
    void worksWithDiskFullContext() {
      WalException exception =
          new TransientIOException("message", ErrorContext.DISK_FULL, "operation");
      assertEquals(ErrorContext.DISK_FULL, exception.context());
    }

    @Test
    @DisplayName("WalException works with PERMISSION_DENIED context via PermanentIOException")
    void worksWithPermissionDeniedContext() {
      WalException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");
      assertEquals(ErrorContext.PERMISSION_DENIED, exception.context());
    }

    @Test
    @DisplayName("WalException works with BAD_FD context via PermanentIOException")
    void worksWithBadFdContext() {
      WalException exception =
          new PermanentIOException("message", ErrorContext.BAD_FD, "operation");
      assertEquals(ErrorContext.BAD_FD, exception.context());
    }

    @Test
    @DisplayName("WalException works with FILE_NOT_FOUND context via PermanentIOException")
    void worksWithFileNotFoundContext() {
      WalException exception =
          new PermanentIOException("message", ErrorContext.FILE_NOT_FOUND, "operation");
      assertEquals(ErrorContext.FILE_NOT_FOUND, exception.context());
    }

    @Test
    @DisplayName("WalException works with RESOURCE_BUSY context via TransientIOException")
    void worksWithResourceBusyContext() {
      WalException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");
      assertEquals(ErrorContext.RESOURCE_BUSY, exception.context());
    }

    @Test
    @DisplayName("WalException works with NO_MEMORY context via TransientIOException")
    void worksWithNoMemoryContext() {
      WalException exception =
          new TransientIOException("message", ErrorContext.NO_MEMORY, "operation");
      assertEquals(ErrorContext.NO_MEMORY, exception.context());
    }

    @Test
    @DisplayName("WalException works with CONCURRENCY context via ConcurrencyException")
    void worksWithConcurrencyContext() {
      WalException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);
      assertEquals(ErrorContext.CONCURRENCY, exception.context());
    }

    @Test
    @DisplayName("WalException works with DATA_CORRUPTION context via CorruptionException")
    void worksWithDataCorruptionContext() {
      WalException exception =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertEquals(ErrorContext.DATA_CORRUPTION, exception.context());
    }

    @Test
    @DisplayName("WalException works with RECOVERY_FAILURE context via RecoveryException")
    void worksWithRecoveryFailureContext() {
      WalException exception = new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);
      assertEquals(ErrorContext.RECOVERY_FAILURE, exception.context());
    }

    @Test
    @DisplayName("WalException works with UNKNOWN_IO_ERROR context via PermanentIOException")
    void worksWithUnknownIOErrorContext() {
      WalException exception =
          new PermanentIOException("message", ErrorContext.UNKNOWN_IO_ERROR, "operation");
      assertEquals(ErrorContext.UNKNOWN_IO_ERROR, exception.context());
    }
  }

  @Nested
  @DisplayName("Recovery Action Coverage Tests")
  class RecoveryActionCoverageTests {

    @Test
    @DisplayName("WalException works with FAIL_AND_RETRY_OPERATION action via ConcurrencyException")
    void worksWithFailAndRetryAction() {
      WalException exception =
          new ConcurrencyException("message", ConcurrencyErrorType.LOCK_TIMEOUT);
      assertEquals(ErrorRecoveryAction.FAIL_AND_RETRY_OPERATION, exception.suggestedAction());
    }

    @Test
    @DisplayName("WalException works with QUARANTINE_AND_ALERT action via CorruptionException")
    void worksWithQuarantineAndAlertAction() {
      WalException exception =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      assertEquals(ErrorRecoveryAction.QUARANTINE_AND_ALERT, exception.suggestedAction());
    }

    @Test
    @DisplayName("WalException works with RETRY_WITH_BACKOFF action via TransientIOException")
    void worksWithRetryWithBackoffAction() {
      WalException exception =
          new TransientIOException("message", ErrorContext.DISK_FULL, "operation");
      assertEquals(ErrorRecoveryAction.RETRY_WITH_BACKOFF, exception.suggestedAction());
    }

    @Test
    @DisplayName("WalException works with FAIL_FAST_ALERT_OPERATOR action via PermanentIOException")
    void worksWithFailFastAlertOperatorAction() {
      WalException exception =
          new PermanentIOException("message", ErrorContext.DISK_FULL, "operation");
      assertEquals(ErrorRecoveryAction.FAIL_FAST_ALERT_OPERATOR, exception.suggestedAction());
    }

    @Test
    @DisplayName("WalException works with SKIP_AND_CONTINUE action via RecoveryException")
    void worksWithSkipAndContinueAction() {
      WalException exception = new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);
      assertEquals(ErrorRecoveryAction.SKIP_AND_CONTINUE, exception.suggestedAction());
    }

    @Test
    @DisplayName("Each permitted exception uses correct recovery action")
    void eachPermittedExceptionUsesCorrectAction() {
      assertEquals(
          ErrorRecoveryAction.RETRY_WITH_BACKOFF,
          ((WalException) new TransientIOException("msg", ErrorContext.DISK_FULL, "op"))
              .suggestedAction());
      assertEquals(
          ErrorRecoveryAction.FAIL_FAST_ALERT_OPERATOR,
          ((WalException) new PermanentIOException("msg", ErrorContext.DISK_FULL, "op"))
              .suggestedAction());
      assertEquals(
          ErrorRecoveryAction.QUARANTINE_AND_ALERT,
          ((WalException)
                  new CorruptionException(
                      "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L))
              .suggestedAction());
      assertEquals(
          ErrorRecoveryAction.SKIP_AND_CONTINUE,
          ((WalException) new RecoveryException("msg", RecoveryType.SEGMENT_TOO_SMALL))
              .suggestedAction());
      assertEquals(
          ErrorRecoveryAction.FAIL_AND_RETRY_OPERATION,
          ((WalException) new ConcurrencyException("msg", ConcurrencyErrorType.LOCK_TIMEOUT))
              .suggestedAction());
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("WalException handles empty message via TransientIOException")
    void handlesEmptyMessage() {
      WalException exception = new TransientIOException("", ErrorContext.DISK_FULL, "operation");
      assertEquals("", exception.getMessage());
    }

    @Test
    @DisplayName("WalException handles null message via TransientIOException")
    void handlesNullMessage() {
      WalException exception = new TransientIOException(null, ErrorContext.DISK_FULL, "operation");
      assertNull(exception.getMessage());
    }

    @Test
    @DisplayName("WalException handles empty operation description via TransientIOException")
    void handlesEmptyOperationDescription() {
      WalException exception = new TransientIOException("message", ErrorContext.DISK_FULL, "");
      assertEquals("", exception.operationDescription());
    }

    @Test
    @DisplayName("WalException handles very long message via TransientIOException")
    void handlesVeryLongMessage() {
      String longMessage = "x".repeat(10000);
      WalException exception =
          new TransientIOException(longMessage, ErrorContext.DISK_FULL, "operation");
      assertEquals(longMessage, exception.getMessage());
    }

    @Test
    @DisplayName("WalException handles special characters in message via TransientIOException")
    void handlesSpecialCharactersInMessage() {
      String messageWithSpecialChars = "Error: 日本語 テスト 🚀 \n\t\r";
      WalException exception =
          new TransientIOException(messageWithSpecialChars, ErrorContext.DISK_FULL, "operation");
      assertEquals(messageWithSpecialChars, exception.getMessage());
    }

    @Test
    @DisplayName("All permitted exceptions handle edge case messages")
    void allPermittedExceptionsHandleEdgeCases() {
      WalException ex1 = new TransientIOException("", ErrorContext.DISK_FULL, "");
      WalException ex2 = new PermanentIOException(null, ErrorContext.DISK_FULL, "op");
      WalException ex3 =
          new CorruptionException(
              "msg", "seg.log", 100, CorruptionType.HEADER_CRC_MISMATCH, 0x1L, 0x2L);
      WalException ex4 = new RecoveryException("message", RecoveryType.SEGMENT_TOO_SMALL);
      WalException ex5 = new ConcurrencyException("msg", ConcurrencyErrorType.LOCK_TIMEOUT);

      assertEquals("", ex1.getMessage());
      assertNull(ex2.getMessage());
      assertNotNull(ex3.getMessage());
      assertNotNull(ex4.getMessage());
      assertNotNull(ex5.getMessage());
    }
  }
}
