package io.writeahead.log.models.results;

public record TruncateResult(
    boolean success,
    long segmentsRemoved,
    long oldestRemainingSegmentSequence,
    boolean corruptionDetected,
    String errorMessage) {

  public TruncateResult {
    if (segmentsRemoved < 0) {
      throw new IllegalArgumentException(
          "segmentsRemoved cannot be negative, got " + segmentsRemoved);
    }
    if (oldestRemainingSegmentSequence < 0) {
      throw new IllegalArgumentException(
          "oldestRemainingSegmentSequence cannot be negative, got "
              + oldestRemainingSegmentSequence);
    }

    if (success) {
      if (corruptionDetected) {
        throw new IllegalArgumentException("If success=true, corruptionDetected must be false");
      }
      if (errorMessage != null) {
        throw new IllegalArgumentException("If success=true, errorMessage must be null");
      }
    }

    if (!success) {
      if (errorMessage == null || errorMessage.isEmpty()) {
        throw new IllegalArgumentException(
            "If success=false, errorMessage must be non-null and non-empty");
      }
      if (segmentsRemoved != 0) {
        throw new IllegalArgumentException(
            "If success=false, segmentsRemoved must be 0, got " + segmentsRemoved);
      }
    }
  }

  public static TruncateResult successfulTruncate(
      long segmentsRemoved, long oldestRemainingSegmentSequence) {
    return new TruncateResult(true, segmentsRemoved, oldestRemainingSegmentSequence, false, null);
  }

  public static TruncateResult nothingToTruncate(long oldestRemainingSegmentSequence) {
    return new TruncateResult(true, 0, oldestRemainingSegmentSequence, false, null);
  }

  public static TruncateResult truncationFailed(
      long oldestRemainingSegmentSequence, String errorMessage) {
    if (errorMessage == null || errorMessage.isEmpty()) {
      throw new IllegalArgumentException("errorMessage must be non-null and non-empty");
    }
    return new TruncateResult(false, 0, oldestRemainingSegmentSequence, false, errorMessage);
  }

  public static TruncateResult corruptionDetectedDuringTruncate(
      long oldestRemainingSegmentSequence, String errorMessage) {
    if (errorMessage == null || errorMessage.isEmpty()) {
      throw new IllegalArgumentException("errorMessage must be non-null and non-empty");
    }
    return new TruncateResult(false, 0, oldestRemainingSegmentSequence, true, errorMessage);
  }

  public boolean didRemoveSegments() {
    return success && segmentsRemoved > 0;
  }

  public boolean isErrorState() {
    return !success;
  }

  public boolean canContinueNormally() {
    return success && !corruptionDetected;
  }
}
