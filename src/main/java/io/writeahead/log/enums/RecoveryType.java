package io.writeahead.log.enums;

public enum RecoveryType {
    SEGMENT_TOO_SMALL("Segment file smaller than minimum size"),
    PARTIAL_ENTRY_AT_EOF("Partial entry at end of file"),
    MISSING_SEGMENT_FILE("Segment file not found during recovery"),
    UNREADABLE_SEGMENT("Cannot read segment"),
    INCOMPLETE_SEGMENT("Segment header written but not finalized"),
    UNKNOWN("Unknown recovery error");

    private final String description;

    RecoveryType(String desc) {
        this.description = desc;
    }

    public String description() {
        return description;
    }
}
