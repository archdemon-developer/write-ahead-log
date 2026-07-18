package io.writeahead.log.models;

public record SegmentMetadata(String filename, long minTimestamp, long maxTimestamp) {}
