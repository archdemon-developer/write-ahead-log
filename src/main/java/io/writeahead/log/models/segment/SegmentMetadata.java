package io.writeahead.log.models.segment;

public record SegmentMetadata(String filename, long minTimestamp, long maxTimestamp) {}
