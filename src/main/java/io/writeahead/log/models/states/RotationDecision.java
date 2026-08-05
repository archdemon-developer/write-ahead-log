package io.writeahead.log.models.states;

import io.writeahead.log.enums.strategies.RotationPolicyType;

public record RotationDecision(
    boolean shouldRotate, String reason, int utilizationPercent, RotationPolicyType policyName) {
  public RotationDecision {
    if (reason == null || reason.isEmpty()) {
      throw new IllegalArgumentException("reason cannot be null or empty");
    }

    if (utilizationPercent < 0 || utilizationPercent > 100) {
      throw new IllegalArgumentException(
          "utilizationPercent must be between 0 and 100, got " + utilizationPercent + "%");
    }

    if (policyName == null) {
      throw new IllegalArgumentException("policyName cannot be null");
    }
  }

  public static RotationDecision rotateNow(
      String reason, int utilizationPercent, RotationPolicyType policyName) {
    return new RotationDecision(true, reason, utilizationPercent, policyName);
  }

  public static RotationDecision keepOpen(
      String reason, int utilizationPercent, RotationPolicyType policyName) {
    return new RotationDecision(false, reason, utilizationPercent, policyName);
  }

  public boolean needsRotation() {
    return shouldRotate;
  }

  public boolean canStayOpen() {
    return !shouldRotate;
  }
}
