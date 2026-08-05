package io.writeahead.log.fsync.retryers;

import io.writeahead.log.exceptions.WalException;
import io.writeahead.log.fsync.FsyncOperation;
import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.metrics.WalMetricsRecorder;
import io.writeahead.log.utils.WalErrorClassifier;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

public class ExponentialBackoffRetryStrategy implements FsyncRetryStrategy {

  private final int maxRetries;
  private final long retryBackoffMs;
  private final double retryBackoffMultiplier;

  private final WalMetricsRecorder metrics;
  private static final Logger log = LoggerFactory.getLogger(ExponentialBackoffRetryStrategy.class);

  public ExponentialBackoffRetryStrategy(
      int maxRetries,
      long retryBackoffMs,
      double retryBackoffMultiplier,
      WalMetricsRecorder metrics) {
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
          metrics.recordFsyncRetrySuccess(attempt);
          log.info("Fsync succeeded on attempt {}", attempt);
        }
        return;
      } catch (IOException ex) {
        WalException walEx = WalErrorClassifier.classifyIOException(ex, "fsync");

        if (walEx.isTransient() && attempt < maxRetries) {
          metrics.recordFsyncTransientFailure(walEx.context());
          long waitMs = (long) (retryBackoffMs * Math.pow(retryBackoffMultiplier, attempt));
          log.warn(
              "Fsync attempt {}/{} failed (transient), retrying in {}ms: {}",
              attempt + 1,
              maxRetries + 1,
              waitMs,
              ex.getMessage());
          sleep(waitMs);
          lastException = ex;
        } else {
          lastException = ex;
          metrics.recordFsyncPermanentFailure(walEx.context());
          throw walEx;
        }
      }
    }

    if (lastException == null) {
      throw new IOException("Fsync failed but no exception was recorded");
    }

    log.error("Fsync failed after {} attempts: {}", (maxRetries + 1), lastException.getMessage());

    throw lastException;
  }

  private void sleep(long baseWaitMs) throws IOException {
    long jitterMs = ThreadLocalRandom.current().nextLong(0, baseWaitMs / 4);
    long finalWaitMs = baseWaitMs + jitterMs;

    try {
      Thread.sleep(finalWaitMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted during fsync retry", ex);
    }
  }
}
