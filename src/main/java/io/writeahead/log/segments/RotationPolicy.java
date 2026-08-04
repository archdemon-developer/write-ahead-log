package io.writeahead.log.segments;

import io.writeahead.log.enums.RotationPolicyType;
import io.writeahead.log.models.RotationDecision;
import io.writeahead.log.models.SegmentState;

public interface RotationPolicy {

  RotationDecision evaluate(SegmentState state, long maxSegmentSize);

  RotationPolicyType name();
}
