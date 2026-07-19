package io.writeahead.log.models;

import io.writeahead.log.enums.FsyncStrategy;

public class WalConfiguration {
  private final int batchSize;
  private final long segmentSizeBytes;
  private final String logDir;
  private final FsyncStrategy fsyncStrategy;
  private final String timestampFormat;

  public WalConfiguration(
      int batchSize,
      long segmentSizeBytes,
      String logDir,
      FsyncStrategy fsyncStrategy,
      String timestampFormat) {
    this.batchSize = batchSize;
    this.segmentSizeBytes = segmentSizeBytes;
    this.logDir = logDir;
    this.fsyncStrategy = fsyncStrategy;
    this.timestampFormat = timestampFormat;
  }

  public int batchSize() {
    return batchSize;
  }

  public long segmentSizeBytes() {
    return segmentSizeBytes;
  }

  public String logDir() {
    return logDir;
  }

  public FsyncStrategy fsyncStrategy() {
    return fsyncStrategy;
  }

  public String timestampFormat() {
    return timestampFormat;
  }

  public static class Builder {
    private int batchSize = 10;
    private long segmentSizeBytes = 10 * 1024 * 1024;
    private String logDir;
    private FsyncStrategy fsyncStrategy = FsyncStrategy.FSYNC_EVERY_BATCH;
    private String timestampFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    public Builder batchSize(int size) {
      this.batchSize = size;
      return this;
    }

    public Builder segmentSizeBytes(long size) {
      this.segmentSizeBytes = size;
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

    public WalConfiguration build() {
      if (logDir == null) {
        throw new IllegalArgumentException("logDir is required");
      }
      return new WalConfiguration(
          batchSize, segmentSizeBytes, logDir, fsyncStrategy, timestampFormat);
    }
  }
}
