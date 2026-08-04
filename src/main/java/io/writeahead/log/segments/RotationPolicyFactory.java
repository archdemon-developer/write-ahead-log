package io.writeahead.log.segments;

import io.writeahead.log.enums.RotationPolicyType;

public class RotationPolicyFactory {

  public static RotationPolicy create(RotationPolicyType policyType) {
    return switch (policyType) {
      case SIZE_BASED -> new SizeBasedRotationPolicy();
    };
  }
}
