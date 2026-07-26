package io.writeahead.log.segments;

public record SegmentMetadata(String filename, long sequenceNumber, long createdAt, long fileSize, long entryCount, long minTimestamp, long maxTimestamp) {}
