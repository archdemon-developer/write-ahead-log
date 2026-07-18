package io.writeahead.log.exceptions;

import java.io.IOException;

public class CorruptedEntryException extends IOException {

  private final String segmentName;
  private final long byteOffset;
  private final long computedCrc;
  private final long storedCrc;
  private final long entriesReadBefore;

  public CorruptedEntryException(
      String segmentName,
      long byteOffset,
      long computedCrc,
      long storedCrc,
      long entriesReadBefore) {
    super(
        "Corrupted entry in segment "
            + segmentName
            + " at offset "
            + byteOffset
            + ". "
            + entriesReadBefore
            + " entries recovered before corruption. Expected CRC: "
            + computedCrc
            + ", Found: "
            + storedCrc);
    this.segmentName = segmentName;
    this.byteOffset = byteOffset;
    this.computedCrc = computedCrc;
    this.storedCrc = storedCrc;
    this.entriesReadBefore = entriesReadBefore;
  }

  public String getSegmentName() {
    return segmentName;
  }

  public long getByteOffset() {
    return byteOffset;
  }

  public long getComputedCrc() {
    return computedCrc;
  }

  public long getStoredCrc() {
    return storedCrc;
  }

  public long getEntriesReadBefore() {
    return entriesReadBefore;
  }
}
