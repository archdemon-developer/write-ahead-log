package io.writeahead.log.exceptions;

public enum ErrorContext {
    DISK_FULL("Disk full"),
    PERMISSION_DENIED("Permission denied"),
    BAD_FD("Bad file descriptor"),
    FILE_NOT_FOUND("File not found"),
    RESOURCE_BUSY("Resource busy"),
    NO_MEMORY("No memory"),
    DATA_CORRUPTION("Data corruption"),
    RECOVERY_FAILURE("Recovery failure"),
    CONCURRENCY("Concurrency error"),
    UNKNOWN_IO_ERROR("Unknown I/O error");

    private final String description;

    ErrorContext(String desc) {
        this.description = desc;
    }

    public String description() {
        return description;
    }
}