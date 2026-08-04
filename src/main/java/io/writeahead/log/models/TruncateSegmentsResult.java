package io.writeahead.log.models;

import java.util.ArrayList;
import java.util.List;

public record TruncateSegmentsResult(
    long segmentsRemoved, long oldestRemainingSequence, List<SegmentMetadata> removedSegments) {

  public TruncateSegmentsResult {
    if (segmentsRemoved < 0) {
      throw new IllegalArgumentException("segmentsRemoved cannot be negative");
    }

    if (oldestRemainingSequence < 0) {
      throw new IllegalArgumentException("oldestRemainingSequence cannot be negative");
    }

    if (removedSegments == null) {
      throw new IllegalArgumentException("removedSegments cannot be null");
    }

    if (removedSegments.size() != segmentsRemoved) {
      throw new IllegalArgumentException(
          "removedSegments.size() ("
              + removedSegments.size()
              + ") must equal segmentsRemoved ("
              + segmentsRemoved
              + ")");
    }
  }

  public static TruncateSegmentsResult nothingRemoved(long oldestRemainingSequence) {
    return new TruncateSegmentsResult(0, oldestRemainingSequence, List.of());
  }

  public static TruncateSegmentsResult segmentsRemoved(
      long segmentsRemoved, long oldestRemainingSequence, List<SegmentMetadata> removedSegments) {
    return new TruncateSegmentsResult(segmentsRemoved, oldestRemainingSequence, removedSegments);
  }

  public boolean wereSegmentsRemoved() {
    return segmentsRemoved > 0;
  }

  public List<SegmentMetadata> getSegmentsToDelete() {
    return new ArrayList<>(removedSegments);
  }
}
