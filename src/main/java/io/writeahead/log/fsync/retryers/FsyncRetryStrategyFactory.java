package io.writeahead.log.fsync.retryers;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.metrics.WalMetricsRecorder;

public class FsyncRetryStrategyFactory {

  public static FsyncRetryStrategy create(
      WalConfiguration walConfiguration, WalMetricsRecorder metrics) {
    return new ExponentialBackoffRetryStrategy(
        walConfiguration.maxRetries(),
        walConfiguration.retryBackoffMs(),
        walConfiguration.retryBackoffMultiplier(),
        metrics);
  }
}
