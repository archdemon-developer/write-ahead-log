package io.writeahead.log.metrics;

public interface WalPerformanceMetrics {
  double getThroughputEntriesPerSec();

  double getThroughputMbPerSec();

  long getTotalFsyncs();

  double getAverageFsyncLatencyMs();

  long getLastFsyncTimeMs();
}
