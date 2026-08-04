package io.writeahead.log.fsync;

import io.writeahead.log.metrics.WalMetricsRecorder;
import io.writeahead.log.models.WalConfiguration;

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
