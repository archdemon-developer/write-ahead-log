package io.writeahead.log.models.states;

import io.writeahead.log.metrics.WalMetricsQuery;
import io.writeahead.log.models.meta.SegmentMetadata;
import java.util.List;

public record WalSnapshot(
    List<SegmentState> closedSegments,
    SegmentState currentSegment,
    BatchState batchState,
    WalMetricsQuery metrics,
    boolean isOpen,
    long snapshotTimeMs) {

  public WalSnapshot {
    if (closedSegments == null) {
      throw new IllegalArgumentException("closedSegments cannot be null");
    }

    if (currentSegment == null) {
      throw new IllegalArgumentException("currentSegment cannot be null");
    }

    if (batchState == null) {
      throw new IllegalArgumentException("batchState cannot be null");
    }

    if (metrics == null) {
      throw new IllegalArgumentException("metrics cannot be null");
    }

    if (snapshotTimeMs < 0) {
      throw new IllegalArgumentException(
          "snapshotTimeMs cannot be negative, got " + snapshotTimeMs);
    }
  }

  public static WalSnapshot of(
      List<SegmentMetadata> closedSegmentMetadata,
      long currentSequenceNumber,
      long currentEntryCount,
      long currentStreamSize,
      long currentMinTimestamp,
      long currentMaxTimestamp,
      long currentSegmentCreatedAt,
      BatchState batchState,
      WalMetricsQuery metrics,
      boolean isOpen)
      throws java.io.IOException {

    List<SegmentState> closedSegments =
        closedSegmentMetadata.stream()
            .map(
                meta ->
                    new SegmentState(
                        meta.sequenceNumber(),
                        meta.entryCount(),
                        meta.fileSize(),
                        meta.minTimestamp(),
                        meta.maxTimestamp(),
                        meta.createdAt(),
                        true))
            .toList();

    long normalizedMin = currentEntryCount > 0 ? currentMinTimestamp : Long.MIN_VALUE;
    long normalizedMax = currentEntryCount > 0 ? currentMaxTimestamp : Long.MAX_VALUE;
    SegmentState currentSegment =
        new SegmentState(
            currentSequenceNumber,
            currentEntryCount,
            currentStreamSize,
            normalizedMin,
            normalizedMax,
            currentSegmentCreatedAt,
            false);

    long snapshotTimeMs = System.currentTimeMillis();

    return new WalSnapshot(
        closedSegments, currentSegment, batchState, metrics, isOpen, snapshotTimeMs);
  }

  /** Helper: Total entry count across all segments (closed + current). */
  public long getTotalEntries() {
    long totalFromClosed = closedSegments.stream().mapToLong(SegmentState::entryCount).sum();
    return totalFromClosed + currentSegment.entryCount();
  }

  /** Helper: Total byte count across all segments (closed + current). */
  public long getTotalBytes() {
    long totalFromClosed = closedSegments.stream().mapToLong(SegmentState::totalByteCount).sum();
    return totalFromClosed + currentSegment.totalByteCount();
  }

  public long getTotalSegmentCount() {
    return closedSegments.size() + 1;
  }

  public boolean isCurrentSegmentEmpty() {
    return currentSegment.entryCount() == 0;
  }

  public boolean hasPendingEntries() {
    return !batchState.isEmpty();
  }

  public long getOldestSegmentCreationTime() {
    if (closedSegments.isEmpty()) {
      return currentSegment.createdAtTimestamp();
    }
    return closedSegments.stream()
        .mapToLong(SegmentState::createdAtTimestamp)
        .min()
        .orElse(currentSegment.createdAtTimestamp());
  }

  /** Helper: Age of current segment in milliseconds. */
  public long getCurrentSegmentAgeMillis() {
    return snapshotTimeMs - currentSegment.createdAtTimestamp();
  }
}
