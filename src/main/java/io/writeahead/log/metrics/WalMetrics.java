package io.writeahead.log.metrics;

public interface WalMetrics {
  long getEntriesWritten();

  long getBytesWritten();

  long getSegmentCount();

  long getCorruptedEntriesDetected();

  long getLastRotationTimeMs();
}
