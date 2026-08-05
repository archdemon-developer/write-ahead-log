package io.writeahead.log.metrics;

import io.writeahead.log.enums.exceptions.CorruptionType;
import io.writeahead.log.enums.exceptions.ErrorContext;

public interface WalMetricsRecorder {

  void recordEntryAppended(int entrySize);

  void recordFsync(long latencyMs);

  void recordCorruptedEntry();

  void recordSegmentRotation();

  void setCurrentSegmentEntryCount(long count);

  void setCurrentSegmentByteCount(long count);

  void setTotalSegmentCount(long count);

  void recordCorruptionType(CorruptionType type);

  void recordSegmentCorruption();

  void recordRecoveryCompleted(long durationMs, long segmentsScanned, long segmentsRecovered);

  void recordFsyncRetrySuccess(int attempts);

  void recordFsyncTransientFailure(ErrorContext context);

  void recordFsyncPermanentFailure(ErrorContext context);
}
