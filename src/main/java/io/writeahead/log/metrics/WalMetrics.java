package io.writeahead.log.metrics;

public interface WalMetrics {
  long getEntriesWritten();

  long getBytesWritten();

  int getSegmentCount();

  long getCorruptedEntriesDetected();

  long getLastRotationTimeMs();
}
