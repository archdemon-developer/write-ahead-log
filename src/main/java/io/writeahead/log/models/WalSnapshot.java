package io.writeahead.log.models;

import io.writeahead.log.metrics.WalMetricsQuery;
import io.writeahead.log.segments.SegmentStoreManager;
import java.io.IOException;
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

  public static WalSnapshot of(SegmentStoreManager manager) throws IOException {
    List<SegmentMetadata> closedSegmentMetadata = manager.getSegments();

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

    SegmentState currentSegment =
        new SegmentState(
            manager.getCurrentSequenceNumber(),
            manager.getCurrentEntryCount(),
            manager.getCurrentStreamSize(),
            manager.getCurrentMinTimestamp(),
            manager.getCurrentMaxTimestamp(),
            manager.getCurrentSegmentCreatedAt(),
            false);

    long snapshotTimeMs = System.currentTimeMillis();

    return new WalSnapshot(
        closedSegments,
        currentSegment,
        manager.getBatchState(),
        manager.getMetrics(),
        manager.isOpen(),
        snapshotTimeMs);
  }

  public long getTotalEntries() {
    long total = currentSegment.entryCount();
    for (SegmentState segment : closedSegments) {
      total += segment.entryCount();
    }
    return total;
  }

  public long getTotalBytes() {
    long total = currentSegment.totalByteCount();
    for (SegmentState segment : closedSegments) {
      total += segment.totalByteCount();
    }
    return total;
  }

  public int getTotalSegmentCount() {
    return closedSegments.size() + 1; // closed + current
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
    return closedSegments.getFirst().createdAtTimestamp();
  }

  public long getCurrentSegmentAgeMillis() {
    return snapshotTimeMs - currentSegment.createdAtTimestamp();
  }
}
