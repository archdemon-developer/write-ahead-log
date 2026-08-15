package io.writeahead.log.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.states.WalSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MetricsAccuracyIntegrationTest extends IntegrationTestBase {

  @Test
  @Timeout(30)
  void testMetricsAccuracyUnderLoad() throws IOException {
    wal = new WriteAheadLog(createConfig());

    int entryCount = 1000;
    long baseTimestamp = 100_000L;
    byte[] payload = new byte[100];

    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(payload.length, payload, baseTimestamp + (i * 1000)));
    }
    wal.writeBatch();

    WalSnapshot snapshot = wal.getSnapshot();

    assertEquals(
        entryCount,
        snapshot.getTotalEntries(),
        "Snapshot entry count should match written entries");
    System.out.println("Test 1: Entry count = " + snapshot.getTotalEntries() + " (correct) ✓");

    long totalBytes = snapshot.getTotalBytes();
    long minExpected = (long) entryCount * payload.length;
    long maxExpected = (long) entryCount * payload.length * 2;

    assertTrue(
        totalBytes >= minExpected && totalBytes <= maxExpected,
        "Byte count should include payload + overhead, got: " + totalBytes);
    System.out.println("Test 1: Total bytes = " + totalBytes + " (includes WAL overhead) ✓");

    long minTs = snapshot.currentSegment().minTimestamp();
    long maxTs = snapshot.currentSegment().maxTimestamp();
    assertTrue(minTs >= baseTimestamp, "Min timestamp should be >= base timestamp");
    assertTrue(
        maxTs <= baseTimestamp + (entryCount - 1) * 1000,
        "Max timestamp should be <= last entry timestamp");
    System.out.println(
        "Test 1: Timestamp range = [" + minTs + ", " + maxTs + "] (within bounds) ✓");

    assertTrue(snapshot.getTotalSegmentCount() >= 1, "Should have at least 1 segment");
    System.out.println("Test 1: Segment count = " + snapshot.getTotalSegmentCount() + " ✓");

    wal.close();
  }

  @Test
  @Timeout(30)
  void testMetricsConsistencyAfterRecovery() throws IOException {
    wal = new WriteAheadLog(createConfig());

    int entryCount = 500;
    long baseTimestamp = 5000L;
    byte[] payload = new byte[100];

    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(payload.length, payload, baseTimestamp + (i * 100)));
    }
    wal.writeBatch();

    WalSnapshot beforeClose = wal.getSnapshot();
    long entriesBefore = beforeClose.getTotalEntries();
    long bytesBefore = beforeClose.getTotalBytes();

    wal.close();
    System.out.println("Test 2: Closed WAL, metrics captured ✓");

    WriteAheadLog recoveredWal = new WriteAheadLog(createConfig());
    WalSnapshot afterRecovery = recoveredWal.getSnapshot();
    long entriesAfter = afterRecovery.getTotalEntries();
    long bytesAfter = afterRecovery.getTotalBytes();

    assertEquals(entriesBefore, entriesAfter, "Entry count should match after recovery");
    System.out.println("Test 2: Entry count after recovery = " + entriesAfter + " (matches) ✓");

    long byteDiff = Math.abs(bytesBefore - bytesAfter);
    assertTrue(
        byteDiff < 1000, "Byte count difference should be minimal overhead, got: " + byteDiff);
    System.out.println("Test 2: Byte count after recovery consistent (within tolerance) ✓");

    recoveredWal.close();
  }

  @Test
  @Timeout(60)
  void testMetricsWithConcurrentWrites() throws IOException, InterruptedException {
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(100)
            .maxSegmentSize(10 * 1024 * 1024)
            .build();
    wal = new WriteAheadLog(config);

    int threadCount = 10;
    int entriesPerThread = 100;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch finishLatch = new CountDownLatch(threadCount);

    for (int t = 0; t < threadCount; t++) {
      int threadId = t;
      new Thread(
              () -> {
                try {
                  startLatch.await();
                  for (int i = 0; i < entriesPerThread; i++) {
                    wal.append(
                        new LogEntry(
                            100,
                            new byte[100],
                            System.currentTimeMillis() + (threadId * 10000) + i));
                  }
                } catch (Exception e) {
                  e.printStackTrace();
                } finally {
                  finishLatch.countDown();
                }
              })
          .start();
    }

    startLatch.countDown();
    finishLatch.await();
    wal.writeBatch();

    WalSnapshot snapshot = wal.getSnapshot();
    long metricEntries = snapshot.getTotalEntries();
    long expectedEntries = (long) threadCount * entriesPerThread;

    assertEquals(
        expectedEntries,
        metricEntries,
        "Metrics should count all concurrent writes, got: " + metricEntries);
    System.out.println(
        "Test 3: Concurrent metrics = "
            + metricEntries
            + " entries from "
            + threadCount
            + " threads ✓");

    List<LogEntry> entries = wal.readAllSegments();
    assertEquals(metricEntries, entries.size(), "Read entries should match metrics count");
    System.out.println("Test 3: Read count matches metrics count ✓");

    long totalBytes = snapshot.getTotalBytes();
    long minExpected = metricEntries * 100;
    long maxExpected = metricEntries * 100 * 2;

    assertTrue(
        totalBytes >= minExpected && totalBytes <= maxExpected,
        "Byte count should be payload + overhead, got: " + totalBytes);
    System.out.println("Test 3: Byte count consistent with entry count ✓");

    wal.close();
  }
}
