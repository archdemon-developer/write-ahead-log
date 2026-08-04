package io.writeahead.log.segments;

import io.writeahead.log.enums.RotationPolicyType;
import io.writeahead.log.models.RotationDecision;
import io.writeahead.log.models.SegmentState;

public class SizeBasedRotationPolicy implements RotationPolicy {

  @Override
  public RotationDecision evaluate(SegmentState currentSegment, long maxSegmentSize) {
    int utilizationPercent = currentSegment.estimatedFillPercent(maxSegmentSize);

    if (!currentSegment.canAcceptMoreEntries()) {
      return RotationDecision.keepOpen("Segment finalized", 100, name());
    }

    if (currentSegment.totalByteCount() >= maxSegmentSize) {
      return RotationDecision.rotateNow(
          "Segment reached max size ("
              + currentSegment.totalByteCount()
              + " >= "
              + maxSegmentSize
              + ")",
          utilizationPercent,
          name());
    }

    return RotationDecision.keepOpen(
        "Segment at " + utilizationPercent + "% utilization", utilizationPercent, name());
  }

  @Override
  public RotationPolicyType name() {
    return RotationPolicyType.SIZE_BASED;
  }
}
