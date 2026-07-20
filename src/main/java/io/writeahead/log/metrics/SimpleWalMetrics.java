package io.writeahead.log.metrics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleWalMetrics implements WalMetrics, WalPerformanceMetrics {

  private final AtomicLong entriesWritten = new AtomicLong(0);
  private final AtomicLong bytesWritten = new AtomicLong(0);
  private final AtomicInteger segmentCount = new AtomicInteger(0);
  private final AtomicLong corruptedEntriesDetected = new AtomicLong(0);
  private final AtomicLong lastRotationTimeMs = new AtomicLong(0);

  private final AtomicLong totalFsyncs = new AtomicLong(0);
  private final AtomicLong totalFsyncLatencyMs = new AtomicLong(0);
  private final long startTimeMs = System.currentTimeMillis();
  private final AtomicLong lastFsyncTimeMs = new AtomicLong(0);

  public void recordEntryWritten(int size) {
    entriesWritten.incrementAndGet();
    bytesWritten.addAndGet(size);
  }

  public void recordFsync(long latencyMs) {
    totalFsyncs.incrementAndGet();
    totalFsyncLatencyMs.addAndGet(latencyMs);
    lastFsyncTimeMs.set(System.currentTimeMillis());
  }

  public void recordCorruptedEntry() {
    corruptedEntriesDetected.incrementAndGet();
  }

  public void recordSegmentRotation() {
    lastRotationTimeMs.set(System.currentTimeMillis());
  }

  public void setSegmentCount(int count) {
    segmentCount.set(count);
  }

  @Override
  public long getEntriesWritten() {
    return entriesWritten.get();
  }

  @Override
  public long getBytesWritten() {
    return bytesWritten.get();
  }

  @Override
  public int getSegmentCount() {
    return segmentCount.get();
  }

  @Override
  public long getCorruptedEntriesDetected() {
    return corruptedEntriesDetected.get();
  }

  @Override
  public long getLastRotationTimeMs() {
    return lastRotationTimeMs.get();
  }

  @Override
  public double getThroughputEntriesPerSec() {
    long elapsedMs = System.currentTimeMillis() - startTimeMs;
    if (elapsedMs == 0) {
      return 0.0;
    }
    return entriesWritten.get() / (elapsedMs / 1000.0);
  }

  @Override
  public double getThroughputMbPerSec() {
    long elapsedMs = System.currentTimeMillis() - startTimeMs;
    if (elapsedMs == 0) {
      return 0.0;
    }
    double mbWritten = bytesWritten.get() / (1024.0 * 1024.0);
    return mbWritten / (elapsedMs / 1000.0);
  }

  @Override
  public long getTotalFsyncs() {
    return totalFsyncs.get();
  }

  @Override
  public double getAverageFsyncLatencyMs() {
    long total = totalFsyncs.get();
    if (total == 0) {
      return 0.0;
    }
    return (double) totalFsyncLatencyMs.get() / total;
  }

  @Override
  public long getLastFsyncTimeMs() {
    return lastFsyncTimeMs.get();
  }
}
