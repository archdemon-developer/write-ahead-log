package io.writeahead.log.segments.policies;

import io.writeahead.log.enums.strategies.RotationPolicyType;

public class RotationPolicyFactory {

  public static RotationPolicy create(RotationPolicyType policyType) {
    return switch (policyType) {
      case SIZE_BASED -> new SizeBasedRotationPolicy();
    };
  }
}
