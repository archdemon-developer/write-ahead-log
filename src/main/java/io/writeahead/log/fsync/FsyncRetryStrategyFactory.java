package io.writeahead.log.fsync;

import io.writeahead.log.metrics.SimpleWalMetrics;
import io.writeahead.log.models.WalConfiguration;

public class FsyncRetryStrategyFactory {

  public static FsyncRetryStrategy create(WalConfiguration walConfiguration , SimpleWalMetrics metrics) {
    return new ExponentialBackoffRetryStrategy(
        walConfiguration.maxRetries(),
        walConfiguration.retryBackoffMs(),
        walConfiguration.retryBackoffMultiplier(), metrics);
  }
}
