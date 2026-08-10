package io.writeahead.log.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.exceptions.ErrorContext;
import io.writeahead.log.enums.exceptions.ErrorRecoveryAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PermanentIOException Tests")
class PermanentIOExceptionTest {

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("PermanentIOException stores message correctly")
    void storesMessageCorrectly() {
      String message = "Permanent I/O error occurred";
      PermanentIOException exception =
          new PermanentIOException(message, ErrorContext.PERMISSION_DENIED, "read operation");

      assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("PermanentIOException stores ErrorContext correctly")
    void storesErrorContextCorrectly() {
      ErrorContext context = ErrorContext.BAD_FD;
      PermanentIOException exception =
          new PermanentIOException("message", context, "write operation");

      assertEquals(context, exception.context());
    }

    @Test
    @DisplayName("PermanentIOException stores operation description correctly")
    void storesOperationDescriptionCorrectly() {
      String operation = "segment deletion";
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.FILE_NOT_FOUND, operation);

      assertEquals(operation, exception.operationDescription());
    }

    @Test
    @DisplayName("PermanentIOException creates with PERMISSION_DENIED context")
    void createsWithPermissionDeniedContext() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");

      assertEquals(ErrorContext.PERMISSION_DENIED, exception.context());
    }

    @Test
    @DisplayName("PermanentIOException creates with BAD_FD context")
    void createsWithBadFdContext() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.BAD_FD, "operation");

      assertEquals(ErrorContext.BAD_FD, exception.context());
    }

    @Test
    @DisplayName("PermanentIOException creates with FILE_NOT_FOUND context")
    void createsWithFileNotFoundContext() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.FILE_NOT_FOUND, "operation");

      assertEquals(ErrorContext.FILE_NOT_FOUND, exception.context());
    }

    @Test
    @DisplayName("PermanentIOException creates with DISK_FULL context")
    void createsWithDiskFullContext() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.DISK_FULL, "operation");

      assertEquals(ErrorContext.DISK_FULL, exception.context());
    }

    @Test
    @DisplayName("PermanentIOException creates with UNKNOWN_IO_ERROR context")
    void createsWithUnknownIOErrorContext() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.UNKNOWN_IO_ERROR, "operation");

      assertEquals(ErrorContext.UNKNOWN_IO_ERROR, exception.context());
    }
  }

  @Nested
  @DisplayName("Transient Behavior Tests")
  class TransientBehaviorTests {

    @Test
    @DisplayName("PermanentIOException isTransient returns false")
    void isTransientReturnsFalse() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");

      assertFalse(exception.isTransient());
    }

    @Test
    @DisplayName("PermanentIOException isTransient always returns false")
    void isTransientAlwaysReturnsFalse() {
      PermanentIOException ex1 =
          new PermanentIOException("msg1", ErrorContext.PERMISSION_DENIED, "op1");
      PermanentIOException ex2 = new PermanentIOException("msg2", ErrorContext.BAD_FD, "op2");
      PermanentIOException ex3 =
          new PermanentIOException("msg3", ErrorContext.FILE_NOT_FOUND, "op3");

      assertFalse(ex1.isTransient());
      assertFalse(ex2.isTransient());
      assertFalse(ex3.isTransient());
    }
  }

  @Nested
  @DisplayName("Data Loss Indication Tests")
  class DataLossTests {

    @Test
    @DisplayName("PermanentIOException indicatesDataLoss returns false")
    void indicatesDataLossReturnsFalse() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");

      assertFalse(exception.indicatesDataLoss());
    }

    @Test
    @DisplayName("PermanentIOException never indicates data loss")
    void neverIndicatesDataLoss() {
      PermanentIOException ex1 =
          new PermanentIOException("msg1", ErrorContext.PERMISSION_DENIED, "op1");
      PermanentIOException ex2 = new PermanentIOException("msg2", ErrorContext.BAD_FD, "op2");

      assertFalse(ex1.indicatesDataLoss());
      assertFalse(ex2.indicatesDataLoss());
    }
  }

  @Nested
  @DisplayName("Recovery Action Tests")
  class RecoveryActionTests {

    @Test
    @DisplayName("PermanentIOException suggests FAIL_FAST_ALERT_OPERATOR action")
    void suggestsFailFastAlertOperatorAction() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");

      assertEquals(ErrorRecoveryAction.FAIL_FAST_ALERT_OPERATOR, exception.suggestedAction());
    }

    @Test
    @DisplayName("PermanentIOException always suggests fail fast alert")
    void alwaysSuggestsFailFastAlert() {
      PermanentIOException ex1 =
          new PermanentIOException("msg1", ErrorContext.PERMISSION_DENIED, "op1");
      PermanentIOException ex2 = new PermanentIOException("msg2", ErrorContext.DISK_FULL, "op2");
      PermanentIOException ex3 =
          new PermanentIOException("msg3", ErrorContext.UNKNOWN_IO_ERROR, "op3");

      assertEquals(ErrorRecoveryAction.FAIL_FAST_ALERT_OPERATOR, ex1.suggestedAction());
      assertEquals(ErrorRecoveryAction.FAIL_FAST_ALERT_OPERATOR, ex2.suggestedAction());
      assertEquals(ErrorRecoveryAction.FAIL_FAST_ALERT_OPERATOR, ex3.suggestedAction());
    }
  }

  @Nested
  @DisplayName("Error Type Code Tests")
  class ErrorTypeCodeTests {

    @Test
    @DisplayName("PermanentIOException errorTypeCode returns PERMANENT_IO")
    void errorTypeCodeReturnsPermanentIO() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");

      assertEquals("PERMANENT_IO", exception.errorTypeCode());
    }

    @Test
    @DisplayName("PermanentIOException errorTypeCode is constant")
    void errorTypeCodeIsConstant() {
      PermanentIOException ex1 =
          new PermanentIOException("msg1", ErrorContext.PERMISSION_DENIED, "op1");
      PermanentIOException ex2 = new PermanentIOException("msg2", ErrorContext.BAD_FD, "op2");

      assertEquals(ex1.errorTypeCode(), ex2.errorTypeCode());
      assertEquals("PERMANENT_IO", ex1.errorTypeCode());
      assertEquals("PERMANENT_IO", ex2.errorTypeCode());
    }

    @Test
    @DisplayName("PermanentIOException errorTypeCode is never transient")
    void errorTypeCodeIsNeverTransient() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.DISK_FULL, "operation");

      String code = exception.errorTypeCode();
      assertFalse(code.contains("TRANSIENT"));
    }
  }

  @Nested
  @DisplayName("toString() Format Tests")
  class ToStringTests {

    @Test
    @DisplayName("PermanentIOException toString includes error type code")
    void toStringIncludesErrorTypeCode() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");

      String result = exception.toString();
      assertTrue(result.contains("PERMANENT_IO"));
    }

    @Test
    @DisplayName("PermanentIOException toString includes message")
    void toStringIncludesMessage() {
      String message = "Permission denied";
      PermanentIOException exception =
          new PermanentIOException(message, ErrorContext.PERMISSION_DENIED, "operation");

      String result = exception.toString();
      assertTrue(result.contains(message));
    }

    @Test
    @DisplayName("PermanentIOException toString includes context")
    void toStringIncludesContext() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");

      String result = exception.toString();
      assertTrue(result.contains("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("PermanentIOException toString includes recovery action")
    void toStringIncludesRecoveryAction() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");

      String result = exception.toString();
      assertTrue(result.contains("FAIL_FAST_ALERT_OPERATOR"));
    }

    @Test
    @DisplayName("PermanentIOException toString includes operation")
    void toStringIncludesOperation() {
      String operation = "metadata write";
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, operation);

      String result = exception.toString();
      assertTrue(result.contains(operation));
    }
  }

  @Nested
  @DisplayName("All Context Types Tests")
  class AllContextTypesTests {

    @Test
    @DisplayName("PermanentIOException works with DISK_FULL")
    void worksWithDiskFull() {
      PermanentIOException exception =
          new PermanentIOException("msg", ErrorContext.DISK_FULL, "op");
      assertEquals(ErrorContext.DISK_FULL, exception.context());
    }

    @Test
    @DisplayName("PermanentIOException works with PERMISSION_DENIED")
    void worksWithPermissionDenied() {
      PermanentIOException exception =
          new PermanentIOException("msg", ErrorContext.PERMISSION_DENIED, "op");
      assertEquals(ErrorContext.PERMISSION_DENIED, exception.context());
    }

    @Test
    @DisplayName("PermanentIOException works with BAD_FD")
    void worksWithBadFd() {
      PermanentIOException exception = new PermanentIOException("msg", ErrorContext.BAD_FD, "op");
      assertEquals(ErrorContext.BAD_FD, exception.context());
    }

    @Test
    @DisplayName("PermanentIOException works with FILE_NOT_FOUND")
    void worksWithFileNotFound() {
      PermanentIOException exception =
          new PermanentIOException("msg", ErrorContext.FILE_NOT_FOUND, "op");
      assertEquals(ErrorContext.FILE_NOT_FOUND, exception.context());
    }

    @Test
    @DisplayName("PermanentIOException works with UNKNOWN_IO_ERROR")
    void worksWithUnknownIOError() {
      PermanentIOException exception =
          new PermanentIOException("msg", ErrorContext.UNKNOWN_IO_ERROR, "op");
      assertEquals(ErrorContext.UNKNOWN_IO_ERROR, exception.context());
    }

    @Test
    @DisplayName("PermanentIOException works with RESOURCE_BUSY")
    void worksWithResourceBusy() {
      PermanentIOException exception =
          new PermanentIOException("msg", ErrorContext.RESOURCE_BUSY, "op");
      assertEquals(ErrorContext.RESOURCE_BUSY, exception.context());
    }

    @Test
    @DisplayName("PermanentIOException works with NO_MEMORY")
    void worksWithNoMemory() {
      PermanentIOException exception =
          new PermanentIOException("msg", ErrorContext.NO_MEMORY, "op");
      assertEquals(ErrorContext.NO_MEMORY, exception.context());
    }

    @Test
    @DisplayName("PermanentIOException works with CONCURRENCY")
    void worksWithConcurrency() {
      PermanentIOException exception =
          new PermanentIOException("msg", ErrorContext.CONCURRENCY, "op");
      assertEquals(ErrorContext.CONCURRENCY, exception.context());
    }

    @Test
    @DisplayName("PermanentIOException works with DATA_CORRUPTION")
    void worksWithDataCorruption() {
      PermanentIOException exception =
          new PermanentIOException("msg", ErrorContext.DATA_CORRUPTION, "op");
      assertEquals(ErrorContext.DATA_CORRUPTION, exception.context());
    }

    @Test
    @DisplayName("PermanentIOException works with RECOVERY_FAILURE")
    void worksWithRecoveryFailure() {
      PermanentIOException exception =
          new PermanentIOException("msg", ErrorContext.RECOVERY_FAILURE, "op");
      assertEquals(ErrorContext.RECOVERY_FAILURE, exception.context());
    }
  }

  @Nested
  @DisplayName("Inheritance Tests")
  class InheritanceTests {

    @Test
    @DisplayName("PermanentIOException extends WalException")
    void extendsWalException() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");

      assertTrue(exception instanceof WalException);
    }

    @Test
    @DisplayName("PermanentIOException extends IOException")
    void extendsIOException() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");

      assertTrue(exception instanceof java.io.IOException);
    }

    @Test
    @DisplayName("PermanentIOException can be caught as WalException")
    void canBeCaughtAsWalException() {
      assertThrows(
          WalException.class,
          () -> {
            throw new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");
          });
    }

    @Test
    @DisplayName("PermanentIOException can be caught as IOException")
    void canBeCaughtAsIOException() {
      assertThrows(
          java.io.IOException.class,
          () -> {
            throw new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");
          });
    }
  }

  @Nested
  @DisplayName("Message Parameter Tests")
  class MessageParameterTests {

    @Test
    @DisplayName("PermanentIOException handles null message")
    void handlesNullMessage() {
      PermanentIOException exception =
          new PermanentIOException(null, ErrorContext.PERMISSION_DENIED, "operation");
      assertNull(exception.getMessage());
    }

    @Test
    @DisplayName("PermanentIOException handles empty message")
    void handlesEmptyMessage() {
      PermanentIOException exception =
          new PermanentIOException("", ErrorContext.PERMISSION_DENIED, "operation");
      assertEquals("", exception.getMessage());
    }

    @Test
    @DisplayName("PermanentIOException handles long message")
    void handlesLongMessage() {
      String longMessage = "x".repeat(5000);
      PermanentIOException exception =
          new PermanentIOException(longMessage, ErrorContext.PERMISSION_DENIED, "operation");
      assertEquals(longMessage, exception.getMessage());
    }

    @Test
    @DisplayName("PermanentIOException handles message with special characters")
    void handlesMessageWithSpecialCharacters() {
      String message = "Error: 日本語 テスト \n\t\r %s";
      PermanentIOException exception =
          new PermanentIOException(message, ErrorContext.PERMISSION_DENIED, "operation");
      assertEquals(message, exception.getMessage());
    }
  }

  @Nested
  @DisplayName("Operation Description Tests")
  class OperationDescriptionTests {

    @Test
    @DisplayName("PermanentIOException stores operation description")
    void storesOperationDescription() {
      String operation = "segment removal";
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, operation);
      assertEquals(operation, exception.operationDescription());
    }

    @Test
    @DisplayName("PermanentIOException handles null operation description")
    void handlesNullOperationDescription() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, null);
      assertNull(exception.operationDescription());
    }

    @Test
    @DisplayName("PermanentIOException handles empty operation description")
    void handlesEmptyOperationDescription() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "");
      assertEquals("", exception.operationDescription());
    }

    @Test
    @DisplayName("PermanentIOException handles long operation description")
    void handlesLongOperationDescription() {
      String operation = "x".repeat(3000);
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, operation);
      assertEquals(operation, exception.operationDescription());
    }
  }

  @Nested
  @DisplayName("Timestamp Tests")
  class TimestampTests {

    @Test
    @DisplayName("PermanentIOException captures timestamp")
    void capturesTimestamp() {
      long before = System.currentTimeMillis();
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");
      long after = System.currentTimeMillis();

      long timestamp = exception.timestamp();
      assertTrue(timestamp >= before);
      assertTrue(timestamp <= after + 1);
    }

    @Test
    @DisplayName("PermanentIOException timestamp is always positive")
    void timestampIsAlwaysPositive() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");
      assertTrue(exception.timestamp() > 0);
    }

    @Test
    @DisplayName("PermanentIOException timestamps are ordered")
    void timestampsAreOrdered() throws InterruptedException {
      PermanentIOException ex1 =
          new PermanentIOException("msg1", ErrorContext.PERMISSION_DENIED, "op1");
      Thread.sleep(1);
      PermanentIOException ex2 = new PermanentIOException("msg2", ErrorContext.BAD_FD, "op2");

      assertTrue(ex2.timestamp() >= ex1.timestamp());
    }
  }

  @Nested
  @DisplayName("Consistency Tests")
  class ConsistencyTests {

    @Test
    @DisplayName("PermanentIOException methods return consistent values")
    void methodsReturnConsistentValues() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");

      assertFalse(exception.isTransient());
      assertFalse(exception.isTransient());

      assertFalse(exception.indicatesDataLoss());
      assertFalse(exception.indicatesDataLoss());

      assertEquals("PERMANENT_IO", exception.errorTypeCode());
      assertEquals("PERMANENT_IO", exception.errorTypeCode());
    }

    @Test
    @DisplayName("PermanentIOException context remains unchanged")
    void contextRemainsUnchanged() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");

      ErrorContext context1 = exception.context();
      ErrorContext context2 = exception.context();

      assertEquals(context1, context2);
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("PermanentIOException with maximum string length")
    void withMaximumStringLength() {
      String maxString = "x".repeat(Integer.MAX_VALUE / 100);
      PermanentIOException exception =
          new PermanentIOException(maxString, ErrorContext.PERMISSION_DENIED, "op");
      assertNotNull(exception.getMessage());
    }

    @Test
    @DisplayName("PermanentIOException toString does not throw")
    void toStringDoesNotThrow() {
      PermanentIOException exception =
          new PermanentIOException("message", ErrorContext.PERMISSION_DENIED, "operation");

      assertDoesNotThrow(() -> exception.toString());
    }

    @Test
    @DisplayName("PermanentIOException can be created multiple times")
    void canBeCreatedMultipleTimes() {
      PermanentIOException ex1 =
          new PermanentIOException("msg1", ErrorContext.PERMISSION_DENIED, "op1");
      PermanentIOException ex2 = new PermanentIOException("msg2", ErrorContext.BAD_FD, "op2");

      assertFalse(ex1.isTransient());
      assertFalse(ex2.isTransient());
    }
  }
}
