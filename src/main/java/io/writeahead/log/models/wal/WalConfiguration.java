package io.writeahead.log.models.wal;

import io.writeahead.log.enums.FsyncStrategy;

public record WalConfiguration(
    int batchSize,
    long maxSegmentSize,
    String logDir,
    FsyncStrategy fsyncStrategy,
    String timestampFormat,
    int maxRetries,
    long retryBackoffMs,
    double retryBackoffMultiplier) {

  public static class Builder {
    private int batchSize = 10;
    private long maxSegmentSize = 10 * 1024 * 1024;
    private String logDir;
    private FsyncStrategy fsyncStrategy = FsyncStrategy.FSYNC_EVERY_BATCH;
    private String timestampFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    private int maxRetries = 3;
    private long retryBackoffMs = 10;
    private double retryBackoffMultiplier = 5.0;

    public Builder batchSize(int size) {
      this.batchSize = size;
      return this;
    }

    public Builder maxSegmentSize(long size) {
      this.maxSegmentSize = size;
      return this;
    }

    public Builder logDir(String dir) {
      this.logDir = dir;
      return this;
    }

    public Builder fsyncStrategy(FsyncStrategy strategy) {
      this.fsyncStrategy = strategy;
      return this;
    }

    public Builder timestampFormat(String format) {
      this.timestampFormat = format;
      return this;
    }

    public Builder maxRetries(int retries) {
      this.maxRetries = retries;
      return this;
    }

    public Builder retryBackoffMs(long backoffMs) {
      this.retryBackoffMs = backoffMs;
      return this;
    }

    public Builder retryBackoffMultiplier(double backoffMultiplier) {
      this.retryBackoffMultiplier = backoffMultiplier;
      return this;
    }

    public WalConfiguration build() {
      if (logDir == null) {
        throw new IllegalArgumentException("logDir is required");
      }
      return new WalConfiguration(
          batchSize,
          maxSegmentSize,
          logDir,
          fsyncStrategy,
          timestampFormat,
          maxRetries,
          retryBackoffMs,
          retryBackoffMultiplier);
    }
  }
}
