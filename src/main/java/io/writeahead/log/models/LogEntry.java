package io.writeahead.log.models;

public class LogEntry {
    private final int size;
    private final byte[] data;

    public LogEntry(int size, byte[] data) {
        this.size = size;
        this.data = data;
    }

    public int getSize() {
        return size;
    }

    public byte[] getData() {
        return data;
    }
}
