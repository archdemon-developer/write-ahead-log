package io.writeahead.log.models.results;

public record CloseResult(
    boolean success,
    long totalSegmentsAtClose,
    long oldestSegmentSequence,
    long newestSegmentSequence,
    long totalEntriesPersisted,
    long totalBytesPersisted,
    boolean hasUnflushedEntries,
    boolean corruptionDetected,
    String errorMessage) {

  public CloseResult {
    if (totalSegmentsAtClose < 0) {
      throw new IllegalArgumentException(
          "totalSegmentsAtClose cannot be negative, got " + totalSegmentsAtClose);
    }
    if (oldestSegmentSequence < 0) {
      throw new IllegalArgumentException(
          "oldestSegmentSequence cannot be negative, got " + oldestSegmentSequence);
    }
    if (newestSegmentSequence < 0) {
      throw new IllegalArgumentException(
          "newestSegmentSequence cannot be negative, got " + newestSegmentSequence);
    }
    if (totalEntriesPersisted < 0) {
      throw new IllegalArgumentException(
          "totalEntriesPersisted cannot be negative, got " + totalEntriesPersisted);
    }
    if (totalBytesPersisted < 0) {
      throw new IllegalArgumentException(
          "totalBytesPersisted cannot be negative, got " + totalBytesPersisted);
    }

    if (totalSegmentsAtClose == 0) {
      if (oldestSegmentSequence != 0) {
        throw new IllegalArgumentException(
            "If totalSegmentsAtClose=0, oldestSegmentSequence must be 0, got "
                + oldestSegmentSequence);
      }
      if (newestSegmentSequence != 0) {
        throw new IllegalArgumentException(
            "If totalSegmentsAtClose=0, newestSegmentSequence must be 0, got "
                + newestSegmentSequence);
      }
    }

    if (totalSegmentsAtClose > 0) {
      if (oldestSegmentSequence > newestSegmentSequence) {
        throw new IllegalArgumentException(
            "oldestSegmentSequence ("
                + oldestSegmentSequence
                + ") cannot be > newestSegmentSequence ("
                + newestSegmentSequence
                + ")");
      }
    }

    if (hasUnflushedEntries && success) {
      throw new IllegalArgumentException("If hasUnflushedEntries=true, success must be false");
    }

    if (success) {
      if (errorMessage != null) {
        throw new IllegalArgumentException("If success=true, errorMessage must be null");
      }
      if (corruptionDetected) {
        throw new IllegalArgumentException("If success=true, corruptionDetected must be false");
      }
      if (hasUnflushedEntries) {
        throw new IllegalArgumentException("If success=true, hasUnflushedEntries must be false");
      }
    }

    if (!success) {
      if (errorMessage == null || errorMessage.isEmpty()) {
        throw new IllegalArgumentException(
            "If success=false, errorMessage must be non-null and non-empty");
      }
    }
  }

  public static CloseResult successfulClose(
      long totalSegmentsAtClose,
      long oldestSegmentSequence,
      long newestSegmentSequence,
      long totalEntriesPersisted,
      long totalBytesPersisted) {
    return new CloseResult(
        true,
        totalSegmentsAtClose,
        oldestSegmentSequence,
        newestSegmentSequence,
        totalEntriesPersisted,
        totalBytesPersisted,
        false,
        false,
        null);
  }

  public static CloseResult closeWithUnflushedEntries(
      long totalSegmentsAtClose,
      long oldestSegmentSequence,
      long newestSegmentSequence,
      long totalEntriesPersisted,
      long totalBytesPersisted,
      String errorMessage) {
    if (errorMessage == null || errorMessage.isEmpty()) {
      throw new IllegalArgumentException("errorMessage must be non-null and non-empty");
    }
    return new CloseResult(
        false,
        totalSegmentsAtClose,
        oldestSegmentSequence,
        newestSegmentSequence,
        totalEntriesPersisted,
        totalBytesPersisted,
        true,
        false,
        errorMessage);
  }

  public static CloseResult closeFailed(
      long totalSegmentsAtClose,
      long oldestSegmentSequence,
      long newestSegmentSequence,
      String errorMessage) {
    if (errorMessage == null || errorMessage.isEmpty()) {
      throw new IllegalArgumentException("errorMessage must be non-null and non-empty");
    }
    return new CloseResult(
        false,
        totalSegmentsAtClose,
        oldestSegmentSequence,
        newestSegmentSequence,
        0,
        0,
        false,
        false,
        errorMessage);
  }

  public static CloseResult corruptionDetectedAtClose(
      long totalSegmentsAtClose, String errorMessage) {
    if (errorMessage == null || errorMessage.isEmpty()) {
      throw new IllegalArgumentException("errorMessage must be non-null and non-empty");
    }
    return new CloseResult(false, totalSegmentsAtClose, 0, 0, 0, 0, false, true, errorMessage);
  }

  public boolean isClean() {
    return success && !hasUnflushedEntries && !corruptionDetected;
  }

  public boolean hadErrors() {
    return !success || hasUnflushedEntries || corruptionDetected;
  }

  public boolean canSafelyReopen() {
    return success && !hasUnflushedEntries && totalSegmentsAtClose > 0;
  }
}
