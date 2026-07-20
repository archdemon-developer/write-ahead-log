package io.writeahead.log.fsync.retry;

import io.writeahead.log.fsync.FsyncOperation;
import io.writeahead.log.fsync.FsyncRetryStrategy;
import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.metrics.SimpleWalMetrics;

import java.io.IOException;

public class ExponentialBackoffRetryStrategy implements FsyncRetryStrategy {

  private final int maxRetries;
  private final long retryBackoffMs;
  private final double retryBackoffMultiplier;

  private final SimpleWalMetrics metrics;
  private static final Logger log = LoggerFactory.getLogger(ExponentialBackoffRetryStrategy.class);

  public ExponentialBackoffRetryStrategy(
      int maxRetries, long retryBackoffMs, double retryBackoffMultiplier,
      SimpleWalMetrics metrics) {
    this.maxRetries = maxRetries;
    this.retryBackoffMs = retryBackoffMs;
    this.retryBackoffMultiplier = retryBackoffMultiplier;
    this.metrics = metrics;
  }

  public void executeWithRetry(FsyncOperation operation) throws IOException {
    IOException lastException = null;

    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        long startNanos = System.nanoTime();
        operation.fsync();
        long endNanos = System.nanoTime();
        long latencyMs = (endNanos - startNanos) / 1_000_000;

        metrics.recordFsync(latencyMs);

        if (attempt > 0) {
          log.info("Fsync succeeded on attempt {}", attempt);
        }
        return;
      } catch (IOException ex) {
        lastException = ex;
        if (attempt < maxRetries) {
          long waitMs = (long) (retryBackoffMs * Math.pow(retryBackoffMultiplier, attempt));
          log.warn(
              "Fsync attempt {}/{} failed, retrying in {}ms: {}",
              attempt + 1,
              maxRetries + 1,
              waitMs,
              ex.getMessage());
          sleep(waitMs);
        } else {
          log.error("Fsync failed on final attempt {}/{}: {}", ex, attempt + 1, maxRetries + 1);
        }
      }
    }

    log.error(
        "Fsync failed after {} attempts: {}",
        lastException,
        (maxRetries + 1),
        lastException == null ? null : lastException.getMessage());
  }

  private void sleep(long waitMs) throws IOException {
    try {
      Thread.sleep(waitMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted during fsync retry", ex);
    }
  }
}
