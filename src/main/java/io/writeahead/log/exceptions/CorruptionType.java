package io.writeahead.log.exceptions;

public enum CorruptionType {
    HEADER_CRC_MISMATCH("Header CRC doesn't match data"),
    FOOTER_CRC_MISMATCH("Footer CRC doesn't match data"),
    INVALID_MAGIC("Magic byte is not 0xAA"),
    INVALID_FOOTER_MARKER("Footer complete marker is not 0xDB"),
    ENTRY_CRC_MISMATCH("Entry CRC doesn't match data"),
    UNKNOWN("Unknown corruption type");

    private final String description;

    CorruptionType(String desc) {
        this.description = desc;
    }

    public String description() {
        return description;
    }
}
