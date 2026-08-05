package io.writeahead.log.segments.policies;

import io.writeahead.log.enums.strategies.RotationPolicyType;
import io.writeahead.log.models.states.RotationDecision;
import io.writeahead.log.models.states.SegmentState;

public interface RotationPolicy {

  RotationDecision evaluate(SegmentState state, long maxSegmentSize);

  RotationPolicyType name();
}
