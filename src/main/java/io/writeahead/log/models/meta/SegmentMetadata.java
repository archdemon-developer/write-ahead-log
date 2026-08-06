package io.writeahead.log.models.meta;

import io.writeahead.log.config.WalConstants;

public record SegmentMetadata(
    String filename,
    long sequenceNumber,
    long createdAt,
    long fileSize,
    long entryCount,
    long minTimestamp,
    long maxTimestamp) {

  public SegmentMetadata {
    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException(
          "filename cannot be null or blank, got: "
              + (filename == null ? "null" : "\"" + filename + "\""));
    }

    if (sequenceNumber < 0) {
      throw new IllegalArgumentException(
          "sequenceNumber cannot be negative, got: " + sequenceNumber);
    }

    if (createdAt < 0) {
      throw new IllegalArgumentException("createdAt cannot be negative, got: " + createdAt);
    }

    if (fileSize < WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE) {
      throw new IllegalArgumentException(
          "fileSize must be >= %d (header + footer), got: %d"
              .formatted(
                  WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE, fileSize));
    }

    if (entryCount <= 0) {
      throw new IllegalArgumentException(
          "entryCount must be > 0 (metadata only exists for finalized segments), got: "
              + entryCount);
    }

    if (minTimestamp > maxTimestamp) {
      throw new IllegalArgumentException(
          "minTimestamp (%d) cannot be > maxTimestamp (%d)".formatted(minTimestamp, maxTimestamp));
    }
  }

  public long averageBytesPerEntry() {
    if (entryCount == 0) {
      return 0;
    }
    return fileSize / entryCount;
  }

  public long timestampRange() {
    return maxTimestamp - minTimestamp;
  }

  @Override
  public String toString() {
    return
        """
        SegmentMetadata {
          filename: %s
          sequenceNumber: %d
          createdAt: %d
          fileSize: %d bytes
          entryCount: %d
          minTimestamp: %d
          maxTimestamp: %d
          timestampRange: %d ms
          avgBytesPerEntry: %d
        }"""
        .formatted(
            filename,
            sequenceNumber,
            createdAt,
            fileSize,
            entryCount,
            minTimestamp,
            maxTimestamp,
            timestampRange(),
            averageBytesPerEntry());
  }
}
