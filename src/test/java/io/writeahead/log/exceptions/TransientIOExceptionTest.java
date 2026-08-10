package io.writeahead.log.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.exceptions.ErrorContext;
import io.writeahead.log.enums.exceptions.ErrorRecoveryAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TransientIOException Tests")
class TransientIOExceptionTest {

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("TransientIOException stores message correctly")
    void storesMessageCorrectly() {
      String message = "Temporary I/O error occurred";
      TransientIOException exception =
          new TransientIOException(message, ErrorContext.RESOURCE_BUSY, "read operation");

      assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("TransientIOException stores ErrorContext correctly")
    void storesErrorContextCorrectly() {
      ErrorContext context = ErrorContext.NO_MEMORY;
      TransientIOException exception =
          new TransientIOException("message", context, "write operation");

      assertEquals(context, exception.context());
    }

    @Test
    @DisplayName("TransientIOException stores operation description correctly")
    void storesOperationDescriptionCorrectly() {
      String operation = "segment rotation";
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, operation);

      assertEquals(operation, exception.operationDescription());
    }

    @Test
    @DisplayName("TransientIOException creates with RESOURCE_BUSY context")
    void createsWithResourceBusyContext() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "fsync operation");

      assertEquals(ErrorContext.RESOURCE_BUSY, exception.context());
    }

    @Test
    @DisplayName("TransientIOException creates with NO_MEMORY context")
    void createsWithNoMemoryContext() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.NO_MEMORY, "allocation operation");

      assertEquals(ErrorContext.NO_MEMORY, exception.context());
    }
  }

  @Nested
  @DisplayName("Transient Behavior Tests")
  class TransientBehaviorTests {

    @Test
    @DisplayName("TransientIOException isTransient returns true")
    void isTransientReturnsTrue() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");

      assertTrue(exception.isTransient());
    }

    @Test
    @DisplayName("TransientIOException isTransient always returns true")
    void isTransientAlwaysReturnsTrue() {
      TransientIOException resourceBusy =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "op");
      TransientIOException noMemory =
          new TransientIOException("message", ErrorContext.NO_MEMORY, "op");

      assertTrue(resourceBusy.isTransient());
      assertTrue(noMemory.isTransient());
    }
  }

  @Nested
  @DisplayName("Data Loss Indication Tests")
  class DataLossTests {

    @Test
    @DisplayName("TransientIOException indicatesDataLoss returns false")
    void indicatesDataLossReturnsFalse() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");

      assertFalse(exception.indicatesDataLoss());
    }

    @Test
    @DisplayName("TransientIOException never indicates data loss")
    void neverIndicatesDataLoss() {
      TransientIOException resourceBusy =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "op");
      TransientIOException noMemory =
          new TransientIOException("message", ErrorContext.NO_MEMORY, "op");

      assertFalse(resourceBusy.indicatesDataLoss());
      assertFalse(noMemory.indicatesDataLoss());
    }
  }

  @Nested
  @DisplayName("Recovery Action Tests")
  class RecoveryActionTests {

    @Test
    @DisplayName("TransientIOException suggests RETRY_WITH_BACKOFF action")
    void suggestsRetryWithBackoffAction() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");

      assertEquals(ErrorRecoveryAction.RETRY_WITH_BACKOFF, exception.suggestedAction());
    }

    @Test
    @DisplayName("TransientIOException always suggests retry with backoff")
    void alwaysSuggestsRetryWithBackoff() {
      TransientIOException resourceBusy =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "op1");
      TransientIOException noMemory =
          new TransientIOException("message", ErrorContext.NO_MEMORY, "op2");

      assertEquals(ErrorRecoveryAction.RETRY_WITH_BACKOFF, resourceBusy.suggestedAction());
      assertEquals(ErrorRecoveryAction.RETRY_WITH_BACKOFF, noMemory.suggestedAction());
    }
  }

  @Nested
  @DisplayName("Error Type Code Tests")
  class ErrorTypeCodeTests {

    @Test
    @DisplayName("TransientIOException errorTypeCode returns TRANSIENT_IO")
    void errorTypeCodeReturnsTransientIO() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");

      assertEquals("TRANSIENT_IO", exception.errorTypeCode());
    }

    @Test
    @DisplayName("TransientIOException errorTypeCode is constant")
    void errorTypeCodeIsConstant() {
      TransientIOException exception1 =
          new TransientIOException("msg1", ErrorContext.RESOURCE_BUSY, "op1");
      TransientIOException exception2 =
          new TransientIOException("msg2", ErrorContext.NO_MEMORY, "op2");

      assertEquals(exception1.errorTypeCode(), exception2.errorTypeCode());
      assertEquals("TRANSIENT_IO", exception1.errorTypeCode());
      assertEquals("TRANSIENT_IO", exception2.errorTypeCode());
    }
  }

  @Nested
  @DisplayName("toString() Format Tests")
  class ToStringTests {

    @Test
    @DisplayName("TransientIOException toString includes error type code")
    void toStringIncludesErrorTypeCode() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");

      String result = exception.toString();
      assertTrue(result.contains("TRANSIENT_IO"));
    }

    @Test
    @DisplayName("TransientIOException toString includes message")
    void toStringIncludesMessage() {
      String message = "Resource temporarily unavailable";
      TransientIOException exception =
          new TransientIOException(message, ErrorContext.RESOURCE_BUSY, "operation");

      String result = exception.toString();
      assertTrue(result.contains(message));
    }

    @Test
    @DisplayName("TransientIOException toString includes context")
    void toStringIncludesContext() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");

      String result = exception.toString();
      assertTrue(result.contains("RESOURCE_BUSY"));
    }

    @Test
    @DisplayName("TransientIOException toString includes recovery action")
    void toStringIncludesRecoveryAction() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");

      String result = exception.toString();
      assertTrue(result.contains("RETRY_WITH_BACKOFF"));
    }

    @Test
    @DisplayName("TransientIOException toString includes operation")
    void toStringIncludesOperation() {
      String operation = "segment read";
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, operation);

      String result = exception.toString();
      assertTrue(result.contains(operation));
    }
  }

  @Nested
  @DisplayName("Context Parameter Tests")
  class ContextParameterTests {

    @Test
    @DisplayName("TransientIOException works with RESOURCE_BUSY")
    void worksWithResourceBusy() {
      TransientIOException exception =
          new TransientIOException("msg", ErrorContext.RESOURCE_BUSY, "op");
      assertEquals(ErrorContext.RESOURCE_BUSY, exception.context());
    }

    @Test
    @DisplayName("TransientIOException works with NO_MEMORY")
    void worksWithNoMemory() {
      TransientIOException exception =
          new TransientIOException("msg", ErrorContext.NO_MEMORY, "op");
      assertEquals(ErrorContext.NO_MEMORY, exception.context());
    }

    @Test
    @DisplayName("TransientIOException works with DISK_FULL")
    void worksWithDiskFull() {
      TransientIOException exception =
          new TransientIOException("msg", ErrorContext.DISK_FULL, "op");
      assertEquals(ErrorContext.DISK_FULL, exception.context());
    }

    @Test
    @DisplayName("TransientIOException works with PERMISSION_DENIED")
    void worksWithPermissionDenied() {
      TransientIOException exception =
          new TransientIOException("msg", ErrorContext.PERMISSION_DENIED, "op");
      assertEquals(ErrorContext.PERMISSION_DENIED, exception.context());
    }

    @Test
    @DisplayName("TransientIOException accepts any ErrorContext")
    void acceptsAnyErrorContext() {
      TransientIOException exception =
          new TransientIOException("msg", ErrorContext.FILE_NOT_FOUND, "op");
      assertEquals(ErrorContext.FILE_NOT_FOUND, exception.context());
    }
  }

  @Nested
  @DisplayName("Inheritance Tests")
  class InheritanceTests {

    @Test
    @DisplayName("TransientIOException extends WalException")
    void extendsWalException() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");

      assertTrue(exception instanceof WalException);
    }

    @Test
    @DisplayName("TransientIOException extends IOException")
    void extendsIOException() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");

      assertTrue(exception instanceof java.io.IOException);
    }

    @Test
    @DisplayName("TransientIOException can be caught as WalException")
    void canBeCaughtAsWalException() {
      assertThrows(
          WalException.class,
          () -> {
            throw new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");
          });
    }

    @Test
    @DisplayName("TransientIOException can be caught as IOException")
    void canBeCaughtAsIOException() {
      assertThrows(
          java.io.IOException.class,
          () -> {
            throw new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");
          });
    }
  }

  @Nested
  @DisplayName("Message Parameter Tests")
  class MessageParameterTests {

    @Test
    @DisplayName("TransientIOException handles null message")
    void handlesNullMessage() {
      TransientIOException exception =
          new TransientIOException(null, ErrorContext.RESOURCE_BUSY, "operation");
      assertNull(exception.getMessage());
    }

    @Test
    @DisplayName("TransientIOException handles empty message")
    void handlesEmptyMessage() {
      TransientIOException exception =
          new TransientIOException("", ErrorContext.RESOURCE_BUSY, "operation");
      assertEquals("", exception.getMessage());
    }

    @Test
    @DisplayName("TransientIOException handles long message")
    void handlesLongMessage() {
      String longMessage = "x".repeat(5000);
      TransientIOException exception =
          new TransientIOException(longMessage, ErrorContext.RESOURCE_BUSY, "operation");
      assertEquals(longMessage, exception.getMessage());
    }

    @Test
    @DisplayName("TransientIOException handles message with special characters")
    void handlesMessageWithSpecialCharacters() {
      String message = "Error: 日本語 テスト \n\t\r %s @#$%";
      TransientIOException exception =
          new TransientIOException(message, ErrorContext.RESOURCE_BUSY, "operation");
      assertEquals(message, exception.getMessage());
    }
  }

  @Nested
  @DisplayName("Operation Description Tests")
  class OperationDescriptionTests {

    @Test
    @DisplayName("TransientIOException stores operation description")
    void storesOperationDescription() {
      String operation = "batch write";
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, operation);
      assertEquals(operation, exception.operationDescription());
    }

    @Test
    @DisplayName("TransientIOException handles null operation description")
    void handlesNullOperationDescription() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, null);
      assertNull(exception.operationDescription());
    }

    @Test
    @DisplayName("TransientIOException handles empty operation description")
    void handlesEmptyOperationDescription() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "");
      assertEquals("", exception.operationDescription());
    }

    @Test
    @DisplayName("TransientIOException handles long operation description")
    void handlesLongOperationDescription() {
      String operation = "x".repeat(3000);
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, operation);
      assertEquals(operation, exception.operationDescription());
    }
  }

  @Nested
  @DisplayName("Timestamp Tests")
  class TimestampTests {

    @Test
    @DisplayName("TransientIOException captures timestamp")
    void capturesTimestamp() {
      long before = System.currentTimeMillis();
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");
      long after = System.currentTimeMillis();

      long timestamp = exception.timestamp();
      assertTrue(timestamp >= before);
      assertTrue(timestamp <= after + 1);
    }

    @Test
    @DisplayName("TransientIOException timestamp is always positive")
    void timestampIsAlwaysPositive() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");
      assertTrue(exception.timestamp() > 0);
    }

    @Test
    @DisplayName("TransientIOException timestamps increase over time")
    void timestampsIncreaseOverTime() throws InterruptedException {
      TransientIOException exception1 =
          new TransientIOException("msg1", ErrorContext.RESOURCE_BUSY, "op1");
      Thread.sleep(1);
      TransientIOException exception2 =
          new TransientIOException("msg2", ErrorContext.RESOURCE_BUSY, "op2");

      assertTrue(exception2.timestamp() >= exception1.timestamp());
    }
  }

  @Nested
  @DisplayName("Consistency Tests")
  class ConsistencyTests {

    @Test
    @DisplayName("TransientIOException methods return consistent values")
    void methodsReturnConsistentValues() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");

      assertTrue(exception.isTransient());
      assertTrue(exception.isTransient());

      assertFalse(exception.indicatesDataLoss());
      assertFalse(exception.indicatesDataLoss());

      assertEquals("TRANSIENT_IO", exception.errorTypeCode());
      assertEquals("TRANSIENT_IO", exception.errorTypeCode());
    }

    @Test
    @DisplayName("TransientIOException context remains unchanged")
    void contextRemainsUnchanged() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");

      ErrorContext context1 = exception.context();
      ErrorContext context2 = exception.context();

      assertEquals(context1, context2);
    }

    @Test
    @DisplayName("TransientIOException operation description remains unchanged")
    void operationDescriptionRemainsUnchanged() {
      String operation = "fsync";
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, operation);

      assertEquals(operation, exception.operationDescription());
      assertEquals(operation, exception.operationDescription());
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("TransientIOException with maximum string length")
    void withMaximumStringLength() {
      String maxString = "x".repeat(Integer.MAX_VALUE / 100);
      TransientIOException exception =
          new TransientIOException(maxString, ErrorContext.RESOURCE_BUSY, "op");
      assertNotNull(exception.getMessage());
    }

    @Test
    @DisplayName("TransientIOException toString does not throw")
    void toStringDoesNotThrow() {
      TransientIOException exception =
          new TransientIOException("message", ErrorContext.RESOURCE_BUSY, "operation");

      assertDoesNotThrow(() -> exception.toString());
    }

    @Test
    @DisplayName("TransientIOException can be created multiple times")
    void canBeCreatedMultipleTimes() {
      TransientIOException ex1 =
          new TransientIOException("msg1", ErrorContext.RESOURCE_BUSY, "op1");
      TransientIOException ex2 = new TransientIOException("msg2", ErrorContext.NO_MEMORY, "op2");

      assertTrue(ex1.isTransient());
      assertTrue(ex2.isTransient());
    }
  }
}
