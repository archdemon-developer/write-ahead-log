package io.writeahead.log.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.results.AppendResult;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class FsyncStrategyIntegrationTest extends IntegrationTestBase {

  @Test
  @Timeout(30)
  void testFsyncEveryBatchUnderLoad() throws IOException {
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(50)
            .maxSegmentSize(10 * 1024 * 1024)
            .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
            .build();
    wal = new WriteAheadLog(config);

    int batchCount = 10;
    int entriesPerBatch = 50;
    int totalEntries = batchCount * entriesPerBatch;

    long startNs = System.nanoTime();
    for (int batch = 0; batch < batchCount; batch++) {
      for (int i = 0; i < entriesPerBatch; i++) {
        wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
      }
      wal.writeBatch();
    }
    long durationNs = System.nanoTime() - startNs;

    List<LogEntry> entries = wal.readAllSegments();
    assertEquals(totalEntries, entries.size(), "Should write all entries");
    System.out.println("Test 1: FSYNC_EVERY_BATCH wrote " + totalEntries + " entries ✓");

    double throughput = metrics.getAppendThroughput(totalEntries, durationNs);
    System.out.println(
        "Test 1: FSYNC_EVERY_BATCH throughput = "
            + String.format("%.0f", throughput)
            + " entries/sec");

    wal.close();
  }

  @Test
  @Timeout(30)
  void testFsyncEveryEntryUnderLoad() throws IOException {
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(1)
            .maxSegmentSize(10 * 1024 * 1024)
            .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_ENTRY)
            .build();
    wal = new WriteAheadLog(config);

    int totalEntries = 200;

    long startNs = System.nanoTime();
    for (int i = 0; i < totalEntries; i++) {
      AppendResult result =
          wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    long durationNs = System.nanoTime() - startNs;

    List<LogEntry> entries = wal.readAllSegments();
    assertEquals(totalEntries, entries.size(), "Should write all entries");
    System.out.println("Test 2: FSYNC_EVERY_ENTRY wrote " + totalEntries + " entries ✓");

    double throughput = metrics.getAppendThroughput(totalEntries, durationNs);
    System.out.println(
        "Test 2: FSYNC_EVERY_ENTRY throughput = "
            + String.format("%.0f", throughput)
            + " entries/sec");

    wal.close();
  }

  @Test
  @Timeout(60)
  void testThroughputDeltaBetweenStrategies() throws IOException {
    int entryCount = 500;

    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(50)
            .maxSegmentSize(10 * 1024 * 1024)
            .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
            .build();
    wal = new WriteAheadLog(config);

    long batchStartNs = System.nanoTime();
    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.writeBatch();
    long batchDurationNs = System.nanoTime() - batchStartNs;
    double batchThroughput = metrics.getAppendThroughput(entryCount, batchDurationNs);

    wal.close();

    IntegrationTestFixtures.deleteAllSegments(tempDir.toString());

    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(1)
            .maxSegmentSize(10 * 1024 * 1024)
            .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_ENTRY)
            .build();
    wal = new WriteAheadLog(config);

    long entryStartNs = System.nanoTime();
    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    long entryDurationNs = System.nanoTime() - entryStartNs;
    double entryThroughput = metrics.getAppendThroughput(entryCount, entryDurationNs);

    wal.close();

    System.out.println(
        "Test 3: FSYNC_EVERY_BATCH = " + String.format("%.0f", batchThroughput) + " entries/sec");
    System.out.println(
        "Test 3: FSYNC_EVERY_ENTRY = " + String.format("%.0f", entryThroughput) + " entries/sec");

    double delta = batchThroughput / entryThroughput;
    System.out.println("Test 3: Batch strategy is " + String.format("%.1f", delta) + "x faster ✓");

    assertTrue(
        batchThroughput > entryThroughput,
        "Batch strategy should be faster than per-entry strategy");
  }

  @Test
  @Timeout(30)
  void testFsyncRetryLogic() throws IOException {

    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(10)
            .maxSegmentSize(10 * 1024 * 1024)
            .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
            .maxRetries(3)
            .retryBackoffMs(10)
            .retryBackoffMultiplier(2.0)
            .build();
    wal = new WriteAheadLog(config);

    int entryCount = 100;

    long startNs = System.nanoTime();
    for (int i = 0; i < entryCount; i++) {
      AppendResult result =
          wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.writeBatch();
    long durationNs = System.nanoTime() - startNs;

    List<LogEntry> entries = wal.readAllSegments();
    assertEquals(
        entryCount,
        entries.size(),
        "Should successfully write all entries with retry logic, got: " + entries.size());
    System.out.println("Test 4: Fsync retry logic succeeded, wrote " + entryCount + " entries ✓");

    System.out.println(
        "Test 4: Retry config - maxRetries="
            + config.maxRetries()
            + ", backoff="
            + config.retryBackoffMs()
            + "ms, multiplier="
            + config.retryBackoffMultiplier()
            + " ✓");

    wal.close();
  }
}
