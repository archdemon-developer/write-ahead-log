package io.writeahead.log.models.states;

public record BatchState(
    long entriesPendingInBatch,
    long totalBytesInBatch,
    long oldestEntryTimestamp,
    long newestEntryTimestamp,
    boolean isEmpty) {

  public BatchState {
    if (entriesPendingInBatch < 0) {
      throw new IllegalArgumentException(
          "entriesPendingInBatch cannot be negative, got " + entriesPendingInBatch);
    }

    if (totalBytesInBatch < 0) {
      throw new IllegalArgumentException(
          "totalBytesInBatch cannot be negative, got " + totalBytesInBatch);
    }

    if (isEmpty && (entriesPendingInBatch != 0 || totalBytesInBatch != 0)) {
      throw new IllegalArgumentException(
          "isEmpty = true requires entriesPendingInBatch = 0 and totalBytesInBatch = 0");
    }

    if (!isEmpty && (entriesPendingInBatch == 0 || totalBytesInBatch == 0)) {
      throw new IllegalArgumentException(
          "isEmpty = false requires entriesPendingInBatch > 0 and totalBytesInBatch > 0");
    }

    if (oldestEntryTimestamp > newestEntryTimestamp) {
      throw new IllegalArgumentException(
          "oldestEntryTimestamp ("
              + oldestEntryTimestamp
              + ") cannot be > newestEntryTimestamp ("
              + newestEntryTimestamp
              + ")");
    }
  }

  public static BatchState emptyBatch() {
    return new BatchState(0, 0L, Long.MIN_VALUE, Long.MAX_VALUE, true);
  }

  public static BatchState withPendingEntries(
      int entriesPendingInBatch,
      long totalBytesInBatch,
      long oldestEntryTimestamp,
      long newestEntryTimestamp) {
    return new BatchState(
        entriesPendingInBatch,
        totalBytesInBatch,
        oldestEntryTimestamp,
        newestEntryTimestamp,
        false);
  }

  public int estimatedFillPercent(int batchSize) {
    if (isEmpty) return 0;
    if (batchSize <= 0) return 0;

    double percentDouble = ((double) entriesPendingInBatch / (double) batchSize) * 100.0;
    return (int) Math.min(percentDouble, 100.0);
  }

  public long averageBytesPerEntry() {
    if (isEmpty) return 0;
    return totalBytesInBatch / entriesPendingInBatch;
  }

  public boolean wouldExceedCapacity(long newEntrySize, int batchSize) {
    if (batchSize <= 0) return true;
    return (entriesPendingInBatch + 1) > batchSize;
  }
}
