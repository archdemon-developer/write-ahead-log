package io.writeahead.log.metrics;

public interface WalMetricsQuery {
  long getEntriesWritten();

  long getBytesWritten();

  long getSegmentCount();

  long getCorruptedEntriesDetected();

  long getLastRotationTimeMs();

  double getThroughputEntriesPerSec();

  double getThroughputMbPerSec();

  long getTotalFsyncs();

  double getAverageFsyncLatencyMs();

  long getLastFsyncTimeMs();
}
