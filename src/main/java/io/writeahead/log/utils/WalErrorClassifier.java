package io.writeahead.log.utils;

import io.writeahead.log.enums.ConcurrencyErrorType;
import io.writeahead.log.enums.CorruptionType;
import io.writeahead.log.enums.ErrorContext;
import io.writeahead.log.enums.RecoveryType;
import io.writeahead.log.exceptions.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.Map;

public class WalErrorClassifier {

    private static final Map<String, ErrorContext> ERROR_PATTERNS =
            Map.ofEntries(
                    Map.entry("No space left on device", ErrorContext.DISK_FULL),
                    Map.entry("ENOSPC", ErrorContext.DISK_FULL),
                    Map.entry("Permission denied", ErrorContext.PERMISSION_DENIED),
                    Map.entry("EACCES", ErrorContext.PERMISSION_DENIED),
                    Map.entry("Bad file descriptor", ErrorContext.BAD_FD),
                    Map.entry("EBADF", ErrorContext.BAD_FD),
                    Map.entry("No such file", ErrorContext.FILE_NOT_FOUND),
                    Map.entry("ENOENT", ErrorContext.FILE_NOT_FOUND),
                    Map.entry("Resource temporarily unavailable", ErrorContext.RESOURCE_BUSY),
                    Map.entry("EAGAIN", ErrorContext.RESOURCE_BUSY),
                    Map.entry("EWOULDBLOCK", ErrorContext.RESOURCE_BUSY),
                    Map.entry("Cannot allocate memory", ErrorContext.NO_MEMORY),
                    Map.entry("ENOMEM", ErrorContext.NO_MEMORY)
            );

    public static WalException classifyIOException(IOException ex,
                                                   String operation) {
        String message = ex.getMessage() != null ? ex.getMessage() : "";

        switch (ex) {
            case AccessDeniedException accessDeniedException -> {
                return new PermanentIOException(
                        "Permission denied during " + operation,
                        ErrorContext.PERMISSION_DENIED,
                        operation
                );
            }
            case FileNotFoundException fileNotFoundException -> {
                return new PermanentIOException(
                        "File not found during " + operation,
                        ErrorContext.FILE_NOT_FOUND,
                        operation
                );
            }
            case NoSuchFileException noSuchFileException -> {
                return new PermanentIOException(
                        "File not found during " + operation,
                        ErrorContext.FILE_NOT_FOUND,
                        operation
                );
            }
            default -> {
            }
        }

        for (Map.Entry<String, ErrorContext> pattern : ERROR_PATTERNS.entrySet()) {
            if (message.contains(pattern.getKey())) {
                ErrorContext context = pattern.getValue();

                if (isTransientContext(context)) {
                    return new TransientIOException(
                            message,
                            context,
                            operation
                    );
                } else {
                    return new PermanentIOException(
                            message,
                            context,
                            operation
                    );
                }
            }
        }

        return new PermanentIOException(
                "I/O error during " + operation + ": " + message,
                ErrorContext.UNKNOWN_IO_ERROR,
                operation
        );
    }

    public static CorruptionException classifyCorruption(
            String segmentName,
            long byteOffset,
            CorruptionType type,
            long computedValue,
            long expectedValue,
            String details) {

        return new CorruptionException(
                "Corruption detected in segment " + segmentName +
                        " at offset " + byteOffset + ": " + details,
                segmentName,
                byteOffset,
                type,
                computedValue,
                expectedValue
        );
    }

    public static RecoveryException classifyRecoveryError(
            RecoveryType type,
            String details) {

        return new RecoveryException(
                "Recovery error: " + details,
                type
        );
    }

    public static ConcurrencyException classifyConcurrencyError(
            ConcurrencyErrorType type,
            String details) {

        return new ConcurrencyException(
                "Concurrency error: " + details,
                type
        );
    }

    private static boolean isTransientContext(ErrorContext context) {
        return context == ErrorContext.RESOURCE_BUSY ||
                context == ErrorContext.NO_MEMORY;
    }
}
