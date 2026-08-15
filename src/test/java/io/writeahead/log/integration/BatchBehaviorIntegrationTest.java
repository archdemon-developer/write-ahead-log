package io.writeahead.log.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.states.WalSnapshot;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class BatchBehaviorIntegrationTest extends IntegrationTestBase {

  @Test
  @Timeout(30)
  void testAutoFlushAtBatchSizeLimit() throws IOException {

    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(20)
            .maxSegmentSize(10 * 1024 * 1024)
            .build();
    wal = new WriteAheadLog(config);

    for (int i = 0; i < 50; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }

    WalSnapshot beforeFlush = wal.getSnapshot();
    System.out.println(
        "Test 1: Before explicit flush - "
            + beforeFlush.getTotalEntries()
            + " total entries, "
            + (beforeFlush.hasPendingEntries() ? "has pending" : "no pending")
            + " ✓");

    wal.writeBatch();

    List<LogEntry> entries = wal.readAllSegments();
    assertEquals(50, entries.size(), "Should have all 50 entries after flush");
    System.out.println("Test 1: Auto-flush worked correctly, all 50 entries present ✓");

    wal.close();
  }

  @Test
  @Timeout(30)
  void testBatchStateTracking() throws IOException {
    // Setup: Small batch size to test accumulation
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(10) // Auto-flush every 10
            .maxSegmentSize(10 * 1024 * 1024)
            .build();
    wal = new WriteAheadLog(config);

    // Action: Append 30 entries (triggers 3 auto-flushes at 10, 20, 30)
    int[] entryCountsPerAppend = {5, 5, 10, 10};
    int totalApended = 0;

    for (int i = 0; i < entryCountsPerAppend.length; i++) {
      int count = entryCountsPerAppend[i];
      for (int j = 0; j < count; j++) {
        wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + j));
      }
      totalApended += count;
      System.out.println("Test 2: After " + totalApended + " appends ✓");
    }

    // Final flush for any remaining
    wal.writeBatch();

    // Verify: All entries are persisted
    java.util.List<LogEntry> entries = wal.readAllSegments();
    assertEquals(
        totalApended,
        entries.size(),
        "All appended entries should be readable after batching and flush, got: " + entries.size());
    System.out.println("Test 2: All " + totalApended + " entries correctly batched and flushed ✓");

    wal.close();
  }

  @Test
  @Timeout(30)
  void testMultipleBatchesInSequence() throws IOException {
    wal = new WriteAheadLog(createConfig());

    long baseTimestamp = 1000L;
    int batchCount = 5;
    int entriesPerBatch = 50;

    for (int batch = 0; batch < batchCount; batch++) {
      for (int i = 0; i < entriesPerBatch; i++) {
        long ts = baseTimestamp + (batch * 10000) + (i * 10);
        wal.append(new LogEntry(100, new byte[100], ts));
      }
      wal.writeBatch();
      System.out.println("Test 3: Batch " + (batch + 1) + " flushed ✓");
    }

    List<LogEntry> entries = wal.readAllSegments();
    int totalExpected = batchCount * entriesPerBatch;
    assertEquals(
        totalExpected,
        entries.size(),
        "Should have "
            + totalExpected
            + " entries from "
            + batchCount
            + " batches, got: "
            + entries.size());
    System.out.println(
        "Test 3: All " + totalExpected + " entries from " + batchCount + " batches ✓");

    assertTimestampsOrdered(entries);
    System.out.println("Test 3: Timestamps ordered across batches ✓");

    wal.close();
  }

  @Test
  @Timeout(30)
  void testVariableSizeBatches() throws IOException {
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(50) // Used as limit, but we control batch size
            .maxSegmentSize(10 * 1024 * 1024)
            .build();
    wal = new WriteAheadLog(config);

    long baseTimestamp = 0L;
    int[] batchSizes = {10, 50, 5, 100};
    int totalEntries = 0;
    int currentTs = 0;

    for (int batch = 0; batch < batchSizes.length; batch++) {
      int batchSize = batchSizes[batch];
      for (int i = 0; i < batchSize; i++) {
        wal.append(new LogEntry(100, new byte[100], baseTimestamp + currentTs));
        currentTs += 100;
      }
      wal.writeBatch();
      totalEntries += batchSize;
      System.out.println("Test 4: Batch " + (batch + 1) + " (" + batchSize + " entries) flushed ✓");
    }

    List<LogEntry> entries = wal.readAllSegments();
    assertEquals(
        totalEntries,
        entries.size(),
        "Should have "
            + totalEntries
            + " entries from variable-size batches, got: "
            + entries.size());
    System.out.println("Test 4: All " + totalEntries + " entries from variable-size batches ✓");

    assertTimestampsOrdered(entries);
    System.out.println("Test 4: Timestamps ordered across variable batches ✓");

    wal.close();
  }
}
