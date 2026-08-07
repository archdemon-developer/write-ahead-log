package io.writeahead.log.utils;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.exceptions.ConcurrencyErrorType;
import io.writeahead.log.enums.exceptions.CorruptionType;
import io.writeahead.log.enums.exceptions.ErrorContext;
import io.writeahead.log.enums.exceptions.ErrorRecoveryAction;
import io.writeahead.log.enums.strategies.RecoveryType;
import io.writeahead.log.exceptions.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WalErrorClassifier - I/O and Corruption Error Classification")
public class WalErrorClassifierTest {

  @Test
  @DisplayName("classifyIOException classifies AccessDeniedException as PERMISSION_DENIED")
  void testClassifyAccessDenied() throws Exception {
    AccessDeniedException ex = new AccessDeniedException("file");
    String operation = "write";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(PermanentIOException.class, result);
    assertEquals(ErrorContext.PERMISSION_DENIED, result.context());
    assertFalse(result.isTransient());
    assertFalse(result.indicatesDataLoss());
  }

  @Test
  @DisplayName("classifyIOException classifies FileNotFoundException as FILE_NOT_FOUND")
  void testClassifyFileNotFound() {
    FileNotFoundException ex = new FileNotFoundException("file not found");
    String operation = "read";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(PermanentIOException.class, result);
    assertEquals(ErrorContext.FILE_NOT_FOUND, result.context());
    assertTrue(result.getMessage().contains("File not found"));
  }

  @Test
  @DisplayName("classifyIOException classifies NoSuchFileException as FILE_NOT_FOUND")
  void testClassifyNoSuchFile() throws Exception {
    NoSuchFileException ex = new NoSuchFileException("file");
    String operation = "read";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(PermanentIOException.class, result);
    assertEquals(ErrorContext.FILE_NOT_FOUND, result.context());
  }

  @Test
  @DisplayName("classifyIOException classifies DISK_FULL message as permanent")
  void testClassifyDiskFull() {
    IOException ex = new IOException("No space left on device");
    String operation = "write";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(PermanentIOException.class, result);
    assertEquals(ErrorContext.DISK_FULL, result.context());
    assertFalse(result.isTransient());
  }

  @Test
  @DisplayName("classifyIOException classifies ENOSPC as DISK_FULL")
  void testClassifyENOSPC() {
    IOException ex = new IOException("ENOSPC");
    String operation = "write";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(PermanentIOException.class, result);
    assertEquals(ErrorContext.DISK_FULL, result.context());
  }

  @Test
  @DisplayName("classifyIOException classifies Permission denied message as permanent")
  void testClassifyPermissionDeniedMessage() {
    IOException ex = new IOException("Permission denied when opening file");
    String operation = "read";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(PermanentIOException.class, result);
    assertEquals(ErrorContext.PERMISSION_DENIED, result.context());
  }

  @Test
  @DisplayName("classifyIOException classifies EACCES as PERMISSION_DENIED")
  void testClassifyEACCES() {
    IOException ex = new IOException("EACCES");
    String operation = "read";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(PermanentIOException.class, result);
    assertEquals(ErrorContext.PERMISSION_DENIED, result.context());
  }

  @Test
  @DisplayName("classifyIOException classifies Bad file descriptor as BAD_FD")
  void testClassifyBadFileDescriptor() {
    IOException ex = new IOException("Bad file descriptor");
    String operation = "fsync";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(PermanentIOException.class, result);
    assertEquals(ErrorContext.BAD_FD, result.context());
  }

  @Test
  @DisplayName("classifyIOException classifies EBADF as BAD_FD")
  void testClassifyEBADF() {
    IOException ex = new IOException("EBADF");
    String operation = "fsync";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(PermanentIOException.class, result);
    assertEquals(ErrorContext.BAD_FD, result.context());
  }

  @Test
  @DisplayName("classifyIOException classifies No such file message as FILE_NOT_FOUND")
  void testClassifyNoSuchFileMessage() {
    IOException ex = new IOException("No such file or directory");
    String operation = "read";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(PermanentIOException.class, result);
    assertEquals(ErrorContext.FILE_NOT_FOUND, result.context());
  }

  @Test
  @DisplayName("classifyIOException classifies ENOENT as FILE_NOT_FOUND")
  void testClassifyENOENT() {
    IOException ex = new IOException("ENOENT");
    String operation = "read";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(PermanentIOException.class, result);
    assertEquals(ErrorContext.FILE_NOT_FOUND, result.context());
  }

  @Test
  @DisplayName("classifyIOException classifies Resource temporarily unavailable as TRANSIENT")
  void testClassifyResourceBusy() {
    IOException ex = new IOException("Resource temporarily unavailable");
    String operation = "read";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(TransientIOException.class, result);
    assertEquals(ErrorContext.RESOURCE_BUSY, result.context());
    assertTrue(result.isTransient());
  }

  @Test
  @DisplayName("classifyIOException classifies EAGAIN as TRANSIENT")
  void testClassifyEAGAIN() {
    IOException ex = new IOException("EAGAIN");
    String operation = "read";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(TransientIOException.class, result);
    assertEquals(ErrorContext.RESOURCE_BUSY, result.context());
    assertTrue(result.isTransient());
  }

  @Test
  @DisplayName("classifyIOException classifies EWOULDBLOCK as TRANSIENT")
  void testClassifyEWOULDBLOCK() {
    IOException ex = new IOException("EWOULDBLOCK");
    String operation = "read";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(TransientIOException.class, result);
    assertEquals(ErrorContext.RESOURCE_BUSY, result.context());
    assertTrue(result.isTransient());
  }

  @Test
  @DisplayName("classifyIOException classifies Cannot allocate memory as TRANSIENT")
  void testClassifyMemoryError() {
    IOException ex = new IOException("Cannot allocate memory");
    String operation = "read";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(TransientIOException.class, result);
    assertEquals(ErrorContext.NO_MEMORY, result.context());
    assertTrue(result.isTransient());
  }

  @Test
  @DisplayName("classifyIOException classifies ENOMEM as TRANSIENT")
  void testClassifyENOMEM() {
    IOException ex = new IOException("ENOMEM");
    String operation = "read";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(TransientIOException.class, result);
    assertEquals(ErrorContext.NO_MEMORY, result.context());
    assertTrue(result.isTransient());
  }

  @Test
  @DisplayName("classifyIOException classifies unknown IOException as UNKNOWN_IO_ERROR")
  void testClassifyUnknownIOException() {
    IOException ex = new IOException("some unknown error");
    String operation = "operation";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(PermanentIOException.class, result);
    assertEquals(ErrorContext.UNKNOWN_IO_ERROR, result.context());
    assertTrue(result.getMessage().contains("I/O error during"));
  }

  @Test
  @DisplayName("classifyIOException handles null exception message")
  void testClassifyIOExceptionWithNullMessage() {
    IOException ex = new IOException((String) null);
    String operation = "read";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertInstanceOf(PermanentIOException.class, result);
    assertEquals(ErrorContext.UNKNOWN_IO_ERROR, result.context());
  }

  @Test
  @DisplayName("classifyIOException includes operation in result")
  void testClassifyIncludesOperation() {
    IOException ex = new IOException("File not found");
    String operation = "segment_write";

    WalException result = WalErrorClassifier.classifyIOException(ex, operation);

    assertTrue(result.getMessage().contains("segment_write"));
    assertEquals("segment_write", result.operationDescription());
  }

  @Test
  @DisplayName("classifyIOException sets timestamp on exception")
  void testClassifyIncludesTimestamp() {
    IOException ex = new IOException("test error");
    long beforeTime = System.currentTimeMillis();

    WalException result = WalErrorClassifier.classifyIOException(ex, "operation");

    long afterTime = System.currentTimeMillis();
    assertTrue(result.timestamp() >= beforeTime);
    assertTrue(result.timestamp() <= afterTime);
  }

  @Test
  @DisplayName("classifyIOException sets suggested recovery action")
  void testClassifySetsSuggestedAction() {
    IOException exPermanent = new IOException("Permission denied");
    IOException exTransient = new IOException("EAGAIN");

    WalException permanentResult = WalErrorClassifier.classifyIOException(exPermanent, "operation");
    WalException transientResult = WalErrorClassifier.classifyIOException(exTransient, "operation");

    assertEquals(ErrorRecoveryAction.FAIL_FAST_ALERT_OPERATOR, permanentResult.suggestedAction());
    assertEquals(ErrorRecoveryAction.RETRY_WITH_BACKOFF, transientResult.suggestedAction());
  }

  @Test
  @DisplayName("classifyCorruption creates CorruptionException with all details")
  void testClassifyCorruptionCreatesException() {
    String segmentName = "wal-001.log";
    long byteOffset = 100;
    CorruptionType type = CorruptionType.ENTRY_CRC_MISMATCH;
    long computedValue = 12345;
    long expectedValue = 54321;
    String details = "CRC mismatch";

    CorruptionException result =
        WalErrorClassifier.classifyCorruption(
            segmentName, byteOffset, type, computedValue, expectedValue, details);

    assertEquals(segmentName, result.segmentName());
    assertEquals(byteOffset, result.byteOffset());
    assertEquals(type, result.corruptionType());
    assertEquals(computedValue, result.computedValue());
    assertEquals(expectedValue, result.expectedValue());
    assertTrue(result.getMessage().contains("Corruption detected"));
    assertTrue(result.getMessage().contains(segmentName));
    assertFalse(result.isTransient());
    assertTrue(result.indicatesDataLoss());
  }

  @Test
  @DisplayName("classifyCorruption includes details in message")
  void testClassifyCorruptionIncludesDetails() {
    CorruptionException result =
        WalErrorClassifier.classifyCorruption(
            "segment.log", 50, CorruptionType.INVALID_MAGIC, 0xAA, 0xBB, "Header marker mismatch");

    assertTrue(result.getMessage().contains("Header marker mismatch"));
  }

  @Test
  @DisplayName("classifyCorruption sets DATA_CORRUPTION context")
  void testClassifyCorruptionContext() {
    CorruptionException result =
        WalErrorClassifier.classifyCorruption(
            "segment.log", 0, CorruptionType.HEADER_CRC_MISMATCH, 0, 0, "test");

    assertEquals(ErrorContext.DATA_CORRUPTION, result.context());
  }

  @Test
  @DisplayName("classifyCorruption sets QUARANTINE_AND_ALERT action")
  void testClassifyCorruptionAction() {
    CorruptionException result =
        WalErrorClassifier.classifyCorruption(
            "segment.log", 0, CorruptionType.HEADER_CRC_MISMATCH, 0, 0, "test");

    assertEquals(ErrorRecoveryAction.QUARANTINE_AND_ALERT, result.suggestedAction());
  }

  @Test
  @DisplayName("classifyCorruption works with different corruption types")
  void testClassifyCorruptionDifferentTypes() {
    CorruptionType[] types = {
      CorruptionType.HEADER_CRC_MISMATCH,
      CorruptionType.INVALID_MAGIC,
      CorruptionType.INVALID_FOOTER_MARKER
    };

    for (CorruptionType type : types) {
      CorruptionException result =
          WalErrorClassifier.classifyCorruption("seg.log", 0, type, 0, 0, "test");
      assertEquals(type, result.corruptionType());
      assertTrue(result.errorTypeCode().contains(type.name()));
    }
  }

  @Test
  @DisplayName("classifyRecoveryError creates RecoveryException with type")
  void testClassifyRecoveryErrorCreatesException() {
    RecoveryType type = RecoveryType.INCOMPLETE_SEGMENT;
    String details = "Segment lacks footer marker";

    RecoveryException result = WalErrorClassifier.classifyRecoveryError(type, details);

    assertEquals(type, result.recoveryType());
    assertTrue(result.getMessage().contains("Recovery error"));
    assertTrue(result.getMessage().contains("Segment lacks footer marker"));
    assertFalse(result.isTransient());
    assertTrue(result.indicatesDataLoss());
  }

  @Test
  @DisplayName("classifyRecoveryError sets RECOVERY_FAILURE context")
  void testClassifyRecoveryErrorContext() {
    RecoveryException result =
        WalErrorClassifier.classifyRecoveryError(RecoveryType.INCOMPLETE_SEGMENT, "test");

    assertEquals(ErrorContext.RECOVERY_FAILURE, result.context());
  }

  @Test
  @DisplayName("classifyRecoveryError sets SKIP_AND_CONTINUE action")
  void testClassifyRecoveryErrorAction() {
    RecoveryException result =
        WalErrorClassifier.classifyRecoveryError(RecoveryType.INCOMPLETE_SEGMENT, "test");

    assertEquals(ErrorRecoveryAction.SKIP_AND_CONTINUE, result.suggestedAction());
  }

  @Test
  @DisplayName("classifyRecoveryError works with different recovery types")
  void testClassifyRecoveryErrorDifferentTypes() {
    RecoveryType[] types = RecoveryType.values();

    for (RecoveryType type : types) {
      RecoveryException result = WalErrorClassifier.classifyRecoveryError(type, "test");
      assertEquals(type, result.recoveryType());
      assertTrue(result.errorTypeCode().contains(type.name()));
    }
  }

  @Test
  @DisplayName("classifyConcurrencyError creates ConcurrencyException with type")
  void testClassifyConcurrencyErrorCreatesException() {
    ConcurrencyErrorType type = ConcurrencyErrorType.LOCK_TIMEOUT;
    String details = "Failed to acquire write lock within timeout";

    ConcurrencyException result = WalErrorClassifier.classifyConcurrencyError(type, details);

    assertEquals(type, result.errorType());
    assertTrue(result.getMessage().contains("Concurrency error"));
    assertTrue(result.getMessage().contains("Failed to acquire write lock"));
    assertTrue(result.isTransient());
    assertFalse(result.indicatesDataLoss());
  }

  @Test
  @DisplayName("classifyConcurrencyError sets CONCURRENCY context")
  void testClassifyConcurrencyErrorContext() {
    ConcurrencyException result =
        WalErrorClassifier.classifyConcurrencyError(ConcurrencyErrorType.LOCK_TIMEOUT, "test");

    assertEquals(ErrorContext.CONCURRENCY, result.context());
  }

  @Test
  @DisplayName("classifyConcurrencyError sets FAIL_AND_RETRY_OPERATION action")
  void testClassifyConcurrencyErrorAction() {
    ConcurrencyException result =
        WalErrorClassifier.classifyConcurrencyError(ConcurrencyErrorType.LOCK_TIMEOUT, "test");

    assertEquals(ErrorRecoveryAction.FAIL_AND_RETRY_OPERATION, result.suggestedAction());
  }

  @Test
  @DisplayName("classifyConcurrencyError works with different concurrency error types")
  void testClassifyConcurrencyErrorDifferentTypes() {
    ConcurrencyErrorType[] types = ConcurrencyErrorType.values();

    for (ConcurrencyErrorType type : types) {
      ConcurrencyException result = WalErrorClassifier.classifyConcurrencyError(type, "test");
      assertEquals(type, result.errorType());
      assertTrue(result.errorTypeCode().contains(type.name()));
    }
  }

  @Test
  @DisplayName("Error classification preserves exception chain information")
  void testErrorClassificationPreservesInfo() {
    IOException cause = new IOException("Original cause");
    IOException ex = new IOException("Outer error", cause);

    WalException result = WalErrorClassifier.classifyIOException(ex, "operation");

    assertEquals("I/O error during operation: Outer error", result.getMessage());
  }

  @Test
  @DisplayName("All permanent IO errors have fail-fast action")
  void testAllPermanentErrorsFailFast() {
    IOException[] permanentErrors = {
      new FileNotFoundException("not found"),
      new IOException("Permission denied"),
      new IOException("No space left on device")
    };

    for (IOException ex : permanentErrors) {
      WalException result = WalErrorClassifier.classifyIOException(ex, "operation");
      assertEquals(
          ErrorRecoveryAction.FAIL_FAST_ALERT_OPERATOR,
          result.suggestedAction(),
          "Permanent error should have FAIL_FAST action");
    }
  }

  @Test
  @DisplayName("All transient IO errors have retry action")
  void testAllTransientErrorsRetry() {
    IOException[] transientErrors = {
      new IOException("EAGAIN"), new IOException("ENOMEM"), new IOException("EWOULDBLOCK")
    };

    for (IOException ex : transientErrors) {
      WalException result = WalErrorClassifier.classifyIOException(ex, "operation");
      assertEquals(
          ErrorRecoveryAction.RETRY_WITH_BACKOFF,
          result.suggestedAction(),
          "Transient error should have RETRY action");
    }
  }

  @Test
  @DisplayName("Exception toString format is consistent and readable")
  void testExceptionToString() {
    WalException ex = WalErrorClassifier.classifyIOException(new IOException("test"), "test_op");
    String str = ex.toString();

    assertTrue(str.contains("["));
    assertTrue(str.contains("]"));
    assertTrue(str.contains("context:"));
    assertTrue(str.contains("recovery:"));
    assertTrue(str.contains("op:"));
  }
}
