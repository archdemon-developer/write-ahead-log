package io.writeahead.log.models;

public record AppendResult(
    boolean flushed,
    int entriesPendingInBatch,
    long currentSegmentEntryCount,
    long currentSegmentSequenceNumber,
    long currentSegmentMinTimestamp,
    long currentSegmentMaxTimestamp,
    long currentSegmentByteCount,
    boolean corruptionDetected,
    String errorMessage) {

  public AppendResult {
    if (entriesPendingInBatch < 0) {
      throw new IllegalArgumentException(
          "entriesPendingInBatch cannot be negative, got " + entriesPendingInBatch);
    }
    if (currentSegmentEntryCount < 0) {
      throw new IllegalArgumentException(
          "currentSegmentEntryCount cannot be negative, got " + currentSegmentEntryCount);
    }
    if (currentSegmentSequenceNumber < 0) {
      throw new IllegalArgumentException(
          "currentSegmentSequenceNumber cannot be negative, got " + currentSegmentSequenceNumber);
    }

    if (currentSegmentByteCount < 48) {
      throw new IllegalArgumentException(
          "currentSegmentByteCount must be >= 48 (header size), got " + currentSegmentByteCount);
    }

    if (corruptionDetected) {
      if (errorMessage == null || errorMessage.isEmpty()) {
        throw new IllegalArgumentException(
            "If corruptionDetected=true, errorMessage must be non-null and non-empty");
      }
    } else {
      if (errorMessage != null) {
        throw new IllegalArgumentException(
            "If corruptionDetected=false, errorMessage must be null");
      }
    }

    if (flushed && entriesPendingInBatch != 0) {
      throw new IllegalArgumentException(
          "If flushed=true, entriesPendingInBatch must be 0, got " + entriesPendingInBatch);
    }

    if (currentSegmentMinTimestamp > currentSegmentMaxTimestamp) {
      throw new IllegalArgumentException(
          "currentSegmentMinTimestamp ("
              + currentSegmentMinTimestamp
              + ") cannot be > currentSegmentMaxTimestamp ("
              + currentSegmentMaxTimestamp
              + ")");
    }
  }

  public static AppendResult successfulAppendNoFlush(
      int entriesPendingInBatch,
      long currentSegmentSequenceNumber,
      long currentSegmentEntryCount,
      long currentSegmentByteCount,
      long currentSegmentMinTimestamp,
      long currentSegmentMaxTimestamp) {

    return new AppendResult(
        false,
        entriesPendingInBatch,
        currentSegmentEntryCount,
        currentSegmentSequenceNumber,
        currentSegmentMinTimestamp,
        currentSegmentMaxTimestamp,
        currentSegmentByteCount,
        false,
        null);
  }

  public static AppendResult successfulAppendWithFlush(
      long currentSegmentSequenceNumber,
      long currentSegmentEntryCount,
      long currentSegmentByteCount,
      long currentSegmentMinTimestamp,
      long currentSegmentMaxTimestamp) {
    return new AppendResult(
        true,
        0,
        currentSegmentEntryCount,
        currentSegmentSequenceNumber,
        currentSegmentMinTimestamp,
        currentSegmentMaxTimestamp,
        currentSegmentByteCount,
        false,
        null);
  }

  public static AppendResult corruptionDetectedResult(
      long currentSegmentSequenceNumber,
      long currentSegmentEntryCount,
      long currentSegmentByteCount,
      long currentSegmentMinTimestamp,
      long currentSegmentMaxTimestamp,
      String errorMessage) {

    return new AppendResult(
        false,
        0,
        currentSegmentEntryCount,
        currentSegmentSequenceNumber,
        currentSegmentMinTimestamp,
        currentSegmentMaxTimestamp,
        currentSegmentByteCount,
        true,
        errorMessage);
  }

  public boolean isErrorState() {
    return corruptionDetected;
  }

  public boolean isDurable() {
    return flushed;
  }

  public boolean canAcceptMoreWrites() {
    return !isErrorState();
  }

  public int estimateRotationRiskPercent(long maxSegmentSize) {
    if (currentSegmentByteCount == 0) {
      return 0;
    }

    double usedPercent = ((double) currentSegmentByteCount / (double) maxSegmentSize) * 100.0;

    return (int) Math.min(usedPercent, 100.0);
  }
}
