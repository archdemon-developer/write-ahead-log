package io.writeahead.log.models;

public record LogEntry(int size, byte[] data, long timestamp) {}
