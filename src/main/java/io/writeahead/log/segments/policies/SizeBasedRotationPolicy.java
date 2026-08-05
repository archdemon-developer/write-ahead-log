package io.writeahead.log.segments.policies;

import io.writeahead.log.enums.strategies.RotationPolicyType;
import io.writeahead.log.models.states.RotationDecision;
import io.writeahead.log.models.states.SegmentState;

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
