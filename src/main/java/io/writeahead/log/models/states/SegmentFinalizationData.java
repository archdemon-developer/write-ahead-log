package io.writeahead.log.models.states;

public record SegmentFinalizationData(int entryCount, long minTimestamp, long maxTimestamp) {

  public SegmentFinalizationData {
    if (entryCount <= 0) {
      throw new IllegalArgumentException(
          "Cannot finalize segment with 0 entries, got " + entryCount);
    }

    if (minTimestamp < 0) {
      throw new IllegalArgumentException("minTimestamp cannot be negative, got " + minTimestamp);
    }

    if (maxTimestamp < 0) {
      throw new IllegalArgumentException("maxTimestamp cannot be negative, got " + maxTimestamp);
    }

    if (minTimestamp > maxTimestamp) {
      throw new IllegalArgumentException(
          "minTimestamp (" + minTimestamp + ") cannot be > maxTimestamp (" + maxTimestamp + ")");
    }
  }

  public static SegmentFinalizationData of(int entryCount, long minTimestamp, long maxTimestamp) {
    return new SegmentFinalizationData(entryCount, minTimestamp, maxTimestamp);
  }

  public long getTimestampRange() {
    return maxTimestamp - minTimestamp;
  }

  public long getAverageTimestampPerEntry() {
    if (entryCount == 0) {
      return 0;
    }
    return getTimestampRange() / entryCount;
  }

  public boolean hasValidTimestampRange() {
    return minTimestamp <= maxTimestamp;
  }

  public boolean coversTimestamp(long timestamp) {
    return timestamp >= minTimestamp && timestamp <= maxTimestamp;
  }

  @Override
  public String toString() {
    return "SegmentFinalizationData{"
        + "entryCount="
        + entryCount
        + ", minTimestamp="
        + minTimestamp
        + ", maxTimestamp="
        + maxTimestamp
        + ", timestampRange="
        + getTimestampRange()
        + '}';
  }
}
