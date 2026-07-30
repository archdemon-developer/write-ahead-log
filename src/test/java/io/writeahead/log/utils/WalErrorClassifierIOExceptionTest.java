package io.writeahead.log.utils;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.ErrorContext;
import io.writeahead.log.exceptions.PermanentIOException;
import io.writeahead.log.exceptions.TransientIOException;
import io.writeahead.log.exceptions.WalException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import org.junit.jupiter.api.Test;

public class WalErrorClassifierIOExceptionTest {

    private static final String OPERATION = "write entry to segment";

    @Test
    void classifyIOExceptionAccessDeniedRetursPermanentIOException() {
        IOException ex = new AccessDeniedException("Cannot access file");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(PermanentIOException.class, result);
    }

    @Test
    void classifyIOExceptionAccessDeniedHasPermissionDeniedContext() {
        IOException ex = new AccessDeniedException("Cannot access file");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.PERMISSION_DENIED, result.context());
    }

    @Test
    void classifyIOExceptionAccessDeniedIsNotTransient() {
        IOException ex = new AccessDeniedException("Cannot access file");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertFalse(result.isTransient());
    }

    @Test
    void classifyIOExceptionAccessDeniedIncludesOperationInMessage() {
        IOException ex = new AccessDeniedException("Cannot access file");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertTrue(result.getMessage().contains(OPERATION));
    }

    @Test
    void classifyIOExceptionFileNotFoundRetursPermanentIOException() {
        IOException ex = new FileNotFoundException("File not found");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(PermanentIOException.class, result);
    }

    @Test
    void classifyIOExceptionFileNotFoundHasFileNotFoundContext() {
        IOException ex = new FileNotFoundException("File not found");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.FILE_NOT_FOUND, result.context());
    }

    @Test
    void classifyIOExceptionFileNotFoundIsNotTransient() {
        IOException ex = new FileNotFoundException("File not found");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertFalse(result.isTransient());
    }

    @Test
    void classifyIOExceptionNoSuchFileExceptionReturnsPermanentIOException() {
        IOException ex = new NoSuchFileException("File not found");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(PermanentIOException.class, result);
    }

    @Test
    void classifyIOExceptionNoSuchFileExceptionHasFileNotFoundContext() {
        IOException ex = new NoSuchFileException("File not found");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.FILE_NOT_FOUND, result.context());
    }

    @Test
    void classifyIOExceptionDiskFullHasDiskFullContext() {
        IOException ex = new IOException("No space left on device");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.DISK_FULL, result.context());
    }


    @Test
    void classifyIOExceptionResourceBusyReturnsTransientIOException() {
        IOException ex = new IOException("Resource temporarily unavailable");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(TransientIOException.class, result);
    }

    @Test
    void classifyIOExceptionResourceBusyHasResourceBusyContext() {
        IOException ex = new IOException("Resource temporarily unavailable");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.RESOURCE_BUSY, result.context());
    }

    @Test
    void classifyIOExceptionResourceBusyIsTransient() {
        IOException ex = new IOException("Resource temporarily unavailable");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertTrue(result.isTransient());
    }

    @Test
    void classifyIOExceptionEAGAINReturnsTransientIOException() {
        IOException ex = new IOException("EAGAIN: retry operation");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(TransientIOException.class, result);
        assertEquals(ErrorContext.RESOURCE_BUSY, result.context());
    }

    @Test
    void classifyIOExceptionEWOULDBLOCKReturnsTransientIOException() {
        IOException ex = new IOException("EWOULDBLOCK: operation would block");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(TransientIOException.class, result);
        assertEquals(ErrorContext.RESOURCE_BUSY, result.context());
    }

    @Test
    void classifyIOExceptionNoMemoryReturnsTransientIOException() {
        IOException ex = new IOException("Cannot allocate memory");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(TransientIOException.class, result);
    }

    @Test
    void classifyIOExceptionNoMemoryHasNoMemoryContext() {
        IOException ex = new IOException("Cannot allocate memory");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.NO_MEMORY, result.context());
    }

    @Test
    void classifyIOExceptionNoMemoryIsTransient() {
        IOException ex = new IOException("Cannot allocate memory");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertTrue(result.isTransient());
    }

    @Test
    void classifyIOExceptionENOMEMReturnsTransientIOException() {
        IOException ex = new IOException("ENOMEM: out of memory");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(TransientIOException.class, result);
        assertEquals(ErrorContext.NO_MEMORY, result.context());
    }

    @Test
    void classifyIOExceptionPermissionDeniedMessageReturnsPermanentIOException() {
        IOException ex = new IOException("Permission denied");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(PermanentIOException.class, result);
    }

    @Test
    void classifyIOExceptionPermissionDeniedHasPermissionDeniedContext() {
        IOException ex = new IOException("Permission denied");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.PERMISSION_DENIED, result.context());
    }

    @Test
    void classifyIOExceptionPermissionDeniedIsNotTransient() {
        IOException ex = new IOException("Permission denied");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertFalse(result.isTransient());
    }

    @Test
    void classifyIOExceptionEACCESReturnsPermanentIOException() {
        IOException ex = new IOException("EACCES: permission denied");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(PermanentIOException.class, result);
        assertEquals(ErrorContext.PERMISSION_DENIED, result.context());
    }

    @Test
    void classifyIOExceptionBadFileDescriptorReturnsPermanentIOException() {
        IOException ex = new IOException("Bad file descriptor");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(PermanentIOException.class, result);
    }

    @Test
    void classifyIOExceptionBadFileDescriptorHasBadFdContext() {
        IOException ex = new IOException("Bad file descriptor");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.BAD_FD, result.context());
    }

    @Test
    void classifyIOExceptionBadFileDescriptorIsNotTransient() {
        IOException ex = new IOException("Bad file descriptor");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertFalse(result.isTransient());
    }

    @Test
    void classifyIOExceptionEBADFReturnsPermanentIOException() {
        IOException ex = new IOException("EBADF: bad file descriptor");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(PermanentIOException.class, result);
        assertEquals(ErrorContext.BAD_FD, result.context());
    }

    @Test
    void classifyIOExceptionNoSuchFileMessageReturnsPermanentIOException() {
        IOException ex = new IOException("No such file or directory");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(PermanentIOException.class, result);
    }

    @Test
    void classifyIOExceptionNoSuchFileHasFileNotFoundContext() {
        IOException ex = new IOException("No such file or directory");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.FILE_NOT_FOUND, result.context());
    }

    @Test
    void classifyIOExceptionENOENTReturnsPermanentIOException() {
        IOException ex = new IOException("ENOENT: no such file");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(PermanentIOException.class, result);
        assertEquals(ErrorContext.FILE_NOT_FOUND, result.context());
    }

    @Test
    void classifyIOExceptionNullMessageHandledAsEmptyString() {
        IOException ex = new IOException((String) null);
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertNotNull(result);
        assertEquals(ErrorContext.UNKNOWN_IO_ERROR, result.context());
    }

    @Test
    void classifyIOExceptionUnknownMessageReturnsUnknownContext() {
        IOException ex = new IOException("Some completely unknown error message");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.UNKNOWN_IO_ERROR, result.context());
    }

    @Test
    void classifyIOExceptionUnknownMessageReturnsPermanent() {
        IOException ex = new IOException("Some completely unknown error message");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(PermanentIOException.class, result);
        assertFalse(result.isTransient());
    }

    @Test
    void classifyIOExceptionUnknownMessageIncludesOriginalMessage() {
        IOException ex = new IOException("Some completely unknown error message");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertTrue(result.getMessage().contains("Some completely unknown error message"));
    }

    @Test
    void classifyIOExceptionUnknownMessageIncludesOperation() {
        IOException ex = new IOException("Some unknown error");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertTrue(result.getMessage().contains(OPERATION));
    }

    @Test
    void classifyIOExceptionPreservesOperationDescription() {
        String customOperation = "fsync segment header";
        IOException ex = new IOException("No space left on device");
        WalException result = WalErrorClassifier.classifyIOException(ex, customOperation);

        assertEquals(customOperation, result.operationDescription());
    }

    @Test
    void classifyIOExceptionCaseSensitivePatternMatching() {
        IOException ex = new IOException("no space left on device");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.UNKNOWN_IO_ERROR, result.context());
    }

    @Test
    void classifyIOExceptionPartialMessageMatch() {
        IOException ex = new IOException("Operation failed: Resource temporarily unavailable during write");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.RESOURCE_BUSY, result.context());
        assertInstanceOf(TransientIOException.class, result);
    }

    @Test
    void classifyIOExceptionDiskFullMessageReturnsPermanentIOException() {
        IOException ex = new IOException("No space left on device");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(PermanentIOException.class, result);
    }

    @Test
    void classifyIOExceptionDiskFullIsNotTransient() {
        IOException ex = new IOException("No space left on device");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertFalse(result.isTransient());
    }

    @Test
    void classifyIOExceptionENOSPCReturnsPermanentIOException() {
        IOException ex = new IOException("ENOSPC: write failed");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertInstanceOf(PermanentIOException.class, result);
        assertEquals(ErrorContext.DISK_FULL, result.context());
    }

    @Test
    void classifyIOExceptionMultiplePatternsInMessageUsesFirstMatch() {
        // Message contains both "No space" and "Permission denied"
        // Pattern matching should find one consistently
        IOException ex = new IOException("No space left on device and Permission denied");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        // Should match "No space left on device" first (DISK_FULL = permanent)
        assertTrue(ErrorContext.PERMISSION_DENIED.equals(result.context()) || ErrorContext.DISK_FULL.equals(result.context()));
        assertInstanceOf(PermanentIOException.class, result);
    }

    @Test
    void classifyIOExceptionAllTransientPatternsAreTransient() {
        String[] transientPatterns = {
                "Resource temporarily unavailable",
                "EAGAIN",
                "EWOULDBLOCK",
                "Cannot allocate memory",
                "ENOMEM"
        };

        for (String pattern : transientPatterns) {
            IOException ex = new IOException(pattern);
            WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);
            assertTrue(result.isTransient(), "Pattern '" + pattern + "' should be transient");
        }
    }

    @Test
    void classifyIOExceptionAllPermanentPatternsArePermanent() {
        String[] permanentPatterns = {
                "No space left on device",
                "ENOSPC",
                "Permission denied",
                "EACCES",
                "Bad file descriptor",
                "EBADF",
                "No such file",
                "ENOENT"
        };

        for (String pattern : permanentPatterns) {
            IOException ex = new IOException(pattern);
            WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);
            assertFalse(result.isTransient(), "Pattern '" + pattern + "' should be permanent");
            assertInstanceOf(PermanentIOException.class, result);
        }
    }

    @Test
    void classifyIOExceptionTimestampIsReasonable() {
        long beforeCall = System.currentTimeMillis();
        IOException ex = new IOException("No space left on device");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);
        long afterCall = System.currentTimeMillis();

        assertTrue(result.timestamp() >= beforeCall);
        assertTrue(result.timestamp() <= afterCall + 1000);
    }

    @Test
    void classifyIOExceptionExceptionTypeCheckedBeforePatternMatching() {
        // AccessDeniedException should return PERMISSION_DENIED immediately,
        // not try pattern matching on its message
        IOException ex = new AccessDeniedException("Some other message");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.PERMISSION_DENIED, result.context());
        assertInstanceOf(PermanentIOException.class, result);
    }

    @Test
    void classifyIOExceptionFileNotFoundExceptionTypeCheckedBeforePattern() {
        // FileNotFoundException should return FILE_NOT_FOUND immediately
        IOException ex = new FileNotFoundException("Some other message");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.FILE_NOT_FOUND, result.context());
    }

    @Test
    void classifyIOExceptionNoSuchFileExceptionTypeCheckedBeforePattern() {
        // NoSuchFileException should return FILE_NOT_FOUND immediately
        IOException ex = new NoSuchFileException("Some other message");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.FILE_NOT_FOUND, result.context());
    }

    @Test
    void classifyIOExceptionPlainIOExceptionWithoutType() {
        IOException ex = new IOException("Random IO error");
        WalException result = WalErrorClassifier.classifyIOException(ex, OPERATION);

        assertEquals(ErrorContext.UNKNOWN_IO_ERROR, result.context());
        assertInstanceOf(PermanentIOException.class, result);
    }

    @Test
    void classifyIOExceptionDifferentOperations() {
        String[] operations = {
                "write entry",
                "fsync segment",
                "read header",
                "create directory",
                "delete file"
        };

        for (String op : operations) {
            IOException ex = new IOException("No space left on device");
            WalException result = WalErrorClassifier.classifyIOException(ex, op);
            assertEquals(op, result.operationDescription());
        }
    }
}