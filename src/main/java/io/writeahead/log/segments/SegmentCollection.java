package io.writeahead.log.segments;

import io.writeahead.log.models.RotationDecision;
import io.writeahead.log.models.SegmentMetadata;
import io.writeahead.log.models.SegmentState;
import io.writeahead.log.models.TruncateSegmentsResult;
import java.util.ArrayList;
import java.util.List;

public class SegmentCollection {
  List<SegmentMetadata> segments;

  public SegmentCollection() {
    this.segments = new ArrayList<>();
  }

  public void add(SegmentMetadata metadata) {
    segments.add(metadata);
  }

  public RotationDecision shouldRotate(
      RotationPolicy rotationPolicy, SegmentState state, long maxSegmentSize) {
    return rotationPolicy.evaluate(state, maxSegmentSize);
  }

  public TruncateSegmentsResult truncateMatching(TruncateFilter filter) {
    List<SegmentMetadata> toDelete = new ArrayList<>();

    for (SegmentMetadata metadata : segments) {
      if (filter.shouldDelete(metadata)) {
        toDelete.add(metadata);
      }
    }

    if (toDelete.isEmpty()) {
      long oldestRemaining = segments.isEmpty() ? 0 : segments.getFirst().sequenceNumber();
      return TruncateSegmentsResult.nothingRemoved(oldestRemaining);
    }

    if (toDelete.size() == segments.size()) {
      toDelete.removeLast();
    }

    List<SegmentMetadata> removedSegments = new ArrayList<>(toDelete);
    for (SegmentMetadata metadata : toDelete) {
      segments.remove(metadata);
    }

    long oldestRemaining = segments.isEmpty() ? 0 : segments.getFirst().sequenceNumber();

    return TruncateSegmentsResult.segmentsRemoved(
        removedSegments.size(), oldestRemaining, removedSegments);
  }

  public long getOldestSequenceNumber() {
    if (segments.isEmpty()) {
      return 0;
    }

    return segments.getFirst().sequenceNumber();
  }

  public long getNewestSequenceNumber() {
    if (segments.isEmpty()) {
      return 0;
    }

    return segments.getLast().sequenceNumber();
  }

  public List<SegmentMetadata> findSegmentsAfterTimestamp(long timestamp) {
    List<SegmentMetadata> segmentsAfterTimestamp = new ArrayList<>();

    for (SegmentMetadata segmentMetadata : segments) {
      if (segmentMetadata.minTimestamp() >= timestamp) {
        segmentsAfterTimestamp.add(segmentMetadata);
      }
    }

    return segmentsAfterTimestamp;
  }

  public int size() {
    return segments.size();
  }

  public List<SegmentMetadata> getSegments() {
    return new ArrayList<>(segments);
  }
}
