package io.writeahead.log.models;

public record SegmentState(
    long segmentSequenceNumber,
    long entryCount,
    long totalByteCount,
    long minTimestamp,
    long maxTimestamp,
    long createdAtTimestamp,
    boolean isFinalized) {

  public SegmentState {

    if (segmentSequenceNumber <= 0) {
      throw new IllegalArgumentException(
          "Segment sequence number must be greater than zero, got " + segmentSequenceNumber);
    }

    if (entryCount < 0) {
      throw new IllegalArgumentException(
          "Entry count must be greater than or equal to 0, got " + entryCount);
    }

    if (totalByteCount < 48) {
      throw new IllegalArgumentException(
          "Total byte count must be greater than or equal to 48, got " + totalByteCount);
    }

    if (minTimestamp > maxTimestamp) {
      throw new IllegalArgumentException(
          "minTimestamp is greater than maxTimestamp, got minTimestamp = "
              + minTimestamp
              + " maxTimestamp = "
              + maxTimestamp);
    }

    if (createdAtTimestamp < 0) {
      throw new IllegalArgumentException(
          "createdAtTimestamp must be greater than or equal to 0, got " + createdAtTimestamp);
    }

    if (isFinalized && entryCount <= 0) {
      throw new IllegalArgumentException(
          "Cannot finalize segment with 0 entries. entryCount=" + entryCount);
    }
  }

  public static SegmentState emptyOpenSegment(long segmentSequenceNumber, long createdAtTimestamp) {
    return new SegmentState(
        segmentSequenceNumber, 0, 48, Long.MIN_VALUE, Long.MAX_VALUE, createdAtTimestamp, false);
  }

  public static SegmentState withEntries(
      long segmentSequenceNumber,
      long entryCount,
      long totalByteCount,
      long minTimestamp,
      long maxTimestamp,
      long createdAtTimestamp,
      boolean isFinalized) {

    return new SegmentState(
        segmentSequenceNumber,
        entryCount,
        totalByteCount,
        minTimestamp,
        maxTimestamp,
        createdAtTimestamp,
        isFinalized);
  }

  public int estimatedFillPercent(long maxSegmentSize) {
    if (maxSegmentSize <= 0) {
      return 0;
    }

    double percent = ((double) totalByteCount / (double) maxSegmentSize) * 100.0;

    return (int) Math.min(100, (int) percent);
  }

  public long ageInMilliseconds(long currentTimeInMillis) {
    if (currentTimeInMillis < createdAtTimestamp) {
      return 0;
    }

    return currentTimeInMillis - createdAtTimestamp;
  }

  public long averageBytesPerEntry() {
    if (entryCount == 0) {
      return 0;
    }

    return totalByteCount / entryCount;
  }

  public boolean canAcceptMoreEntries() {
    return !isFinalized;
  }
}
