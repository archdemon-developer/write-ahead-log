package io.writeahead.log.fsync.factory;

import io.writeahead.log.fsync.FsyncRetryStrategy;
import io.writeahead.log.fsync.retry.ExponentialBackoffRetryStrategy;
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
