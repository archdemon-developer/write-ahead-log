package io.writeahead.log.metrics;

import io.writeahead.log.enums.CorruptionType;
import io.writeahead.log.enums.ErrorContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleWalMetrics implements WalMetrics, WalPerformanceMetrics {

  private final AtomicLong entriesWritten = new AtomicLong(0);
  private final AtomicLong bytesWritten = new AtomicLong(0);
  private final AtomicLong segmentCount = new AtomicLong(0);
  private final AtomicLong corruptedEntriesDetected = new AtomicLong(0);
  private final AtomicLong lastRotationTimeMs = new AtomicLong(0);

  private final AtomicLong totalFsyncs = new AtomicLong(0);
  private final AtomicLong totalFsyncLatencyMs = new AtomicLong(0);
  private final long startTimeMs = System.currentTimeMillis();
  private final AtomicLong lastFsyncTimeMs = new AtomicLong(0);

  private final Map<String, Long> fsyncTransientErrorCounts = new ConcurrentHashMap<>();
  private final Map<String, Long> fsyncPermanentErrorCounts = new ConcurrentHashMap<>();
  private final AtomicLong segmentCorruptionCount = new AtomicLong(0);
  private final Map<String, Long> corruptionTypeCounts = new ConcurrentHashMap<>();

  private final AtomicLong fsyncRetrySuccessCount = new AtomicLong(0);

  private final AtomicLong recoveryCount = new AtomicLong(0);
  private final AtomicLong totalRecoveryTimeMs = new AtomicLong(0);
  private final AtomicLong lastRecoverySegmentsScanned = new AtomicLong(0);
  private final AtomicLong lastRecoverySegmentsRecovered = new AtomicLong(0);

  private static final double BYTES_PER_MB = 1024.0 * 1024.0;
  private static final double MILLISECONDS_PER_SECOND = 1000.0;

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

  public void setSegmentCount(long count) {
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
  public long getSegmentCount() {
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
    return entriesWritten.get() / (elapsedMs / MILLISECONDS_PER_SECOND);
  }

  @Override
  public double getThroughputMbPerSec() {
    long elapsedMs = System.currentTimeMillis() - startTimeMs;
    if (elapsedMs == 0) {
      return 0.0;
    }
    double mbWritten = bytesWritten.get() / BYTES_PER_MB;
    return mbWritten / (elapsedMs / MILLISECONDS_PER_SECOND);
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

  public void recordFsyncTransientFailure(ErrorContext context) {
    fsyncTransientErrorCounts.merge(context.name(), 1L, Long::sum);
  }

  public void recordFsyncPermanentFailure(ErrorContext context) {
    fsyncPermanentErrorCounts.merge(context.name(), 1L, Long::sum);
  }

  public void recordFsyncRetrySuccess(int attempts) {
    fsyncRetrySuccessCount.incrementAndGet();
  }

  public void recordSegmentCorruption() {
    segmentCorruptionCount.incrementAndGet();
  }

  public void recordCorruptionType(CorruptionType type) {
    corruptionTypeCounts.merge(type.name(), 1L, Long::sum);
  }

  public void recordRecoveryCompleted(
      long durationMs, long segmentsScanned, long segmentsRecovered) {
    recoveryCount.incrementAndGet();
    totalRecoveryTimeMs.addAndGet(durationMs);
    lastRecoverySegmentsScanned.set(segmentsScanned);
    lastRecoverySegmentsRecovered.set(segmentsRecovered);
  }

  public long getRecoveryCount() {
    return recoveryCount.get();
  }

  public double getAverageRecoveryTimeMs() {
    long count = recoveryCount.get();
    if (count == 0) {
      return 0.0;
    }
    return (double) totalRecoveryTimeMs.get() / count;
  }

  public long getLastRecoverySegmentsScanned() {
    return lastRecoverySegmentsScanned.get();
  }

  public long getLastRecoverySegmentsRecovered() {
    return lastRecoverySegmentsRecovered.get();
  }

  public Map<String, Long> getFsyncTransientErrorCounts() {
    return new ConcurrentHashMap<>(fsyncTransientErrorCounts);
  }

  public Map<String, Long> getFsyncPermanentErrorCounts() {
    return new ConcurrentHashMap<>(fsyncPermanentErrorCounts);
  }

  public long getSegmentCorruptionCount() {
    return segmentCorruptionCount.get();
  }

  public Map<String, Long> getCorruptionTypeCounts() {
    return new ConcurrentHashMap<>(corruptionTypeCounts);
  }

  public long getFsyncRetrySuccessCount() {
    return fsyncRetrySuccessCount.get();
  }
}
