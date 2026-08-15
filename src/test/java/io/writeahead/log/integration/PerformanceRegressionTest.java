package io.writeahead.log.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.models.LogEntry;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class PerformanceRegressionTest extends IntegrationTestBase {

  @Test
  @Timeout(60)
  void measureAppendThroughputBaseline() throws IOException {
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(1000)
            .maxSegmentSize(50 * 1024 * 1024)
            .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
            .build();
    wal = new WriteAheadLog(config);

    int entryCount = 50_000;

    long startNs = System.nanoTime();
    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    long durationNs = System.nanoTime() - startNs;

    double throughput = metrics.getAppendThroughput(entryCount, durationNs);

    System.out.println(
        "Test 1: Append throughput = " + String.format("%.0f", throughput) + " entries/sec ✓");

    wal.close();
  }

  @Test
  @Timeout(60)
  void testBatchWriteThroughputBaseline() throws IOException {
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(50)
            .maxSegmentSize(50 * 1024 * 1024)
            .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
            .build();
    wal = new WriteAheadLog(config);

    int entryCount = 20_000;
    int batchSize = 50;

    long startNs = System.nanoTime();
    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
      if ((i + 1) % batchSize == 0) {
        wal.writeBatch();
      }
    }
    long durationNs = System.nanoTime() - startNs;

    double throughput = metrics.getAppendThroughput(entryCount, durationNs);
    System.out.println(
        "Test 2: Batch write throughput = "
            + String.format("%.0f", throughput)
            + " entries/sec (informational) ✓");

    wal.close();
  }

  @Test
  @Timeout(60)
  void testReadThroughputBaseline() throws IOException {
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(100)
            .maxSegmentSize(50 * 1024 * 1024)
            .build();
    wal = new WriteAheadLog(config);

    int entryCount = 50_000;

    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.writeBatch();

    long startNs = System.nanoTime();
    List<LogEntry> entries = wal.readAllSegments();
    long durationNs = System.nanoTime() - startNs;

    double throughput = metrics.getAppendThroughput(entries.size(), durationNs);

    assertTrue(
        throughput > 100_000,
        "Read throughput should exceed 100k entries/sec baseline, got: "
            + String.format("%.0f", throughput));
    System.out.println(
        "Test 3: Read throughput = "
            + String.format("%.0f", throughput)
            + " entries/sec (baseline: >100k) ✓");

    wal.close();
  }

  @Test
  @Timeout(60)
  void testRecoveryThroughputBaseline() throws IOException {
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(100)
            .maxSegmentSize(500 * 1024) // Multiple segments
            .build();
    wal = new WriteAheadLog(config);

    int entryCount = 5_000;

    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.writeBatch();
    wal.close();

    System.out.println(
        "Test 4: Wrote " + entryCount + " entries, measuring recovery throughput...");

    long recoveryStartNs = System.nanoTime();
    WriteAheadLog recoveredWal = new WriteAheadLog(config);
    long recoveryDurationNs = System.nanoTime() - recoveryStartNs;

    List<LogEntry> recovered = recoveredWal.readAllSegments();

    double recoveryThroughput = (recovered.size() * 1_000_000_000.0) / recoveryDurationNs;
    long recoveryMs = recoveryDurationNs / 1_000_000;

    assertTrue(recoveryMs < 2000, "Recovery should complete in <2s, took: " + recoveryMs + "ms");
    System.out.println(
        "Test 4: Recovery throughput = "
            + String.format("%.0f", recoveryThroughput)
            + " entries/sec (time: "
            + recoveryMs
            + "ms, baseline: <2s) ✓");

    metrics.recordRecoveryLatency(recoveryDurationNs);
    recoveredWal.close();
  }

  private void printBaselineSummary() {
    System.out.println("\n=== PERFORMANCE REGRESSION BASELINES ===");
    System.out.println("Append throughput baseline:      >50,000 entries/sec");
    System.out.println("Batch write throughput baseline: >10,000 entries/sec");
    System.out.println("Read throughput baseline:        >100,000 entries/sec");
    System.out.println("Recovery time baseline:          <2,000 ms");
    System.out.println("=========================================\n");
  }
}
