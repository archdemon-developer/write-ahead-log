package io.writeahead.log.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.models.LogEntry;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class LargeDataVolumeIntegrationTest extends IntegrationTestBase {

  @Test
  @Timeout(60)
  void testTenThousandSmallEntries() throws IOException {
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(100)
            .maxSegmentSize(1024 * 1024)
            .build();
    wal = new WriteAheadLog(config);

    int entryCount = 10_000;
    byte[] payload = IntegrationTestFixtures.smallPayload();

    long startNs = System.nanoTime();
    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(payload.length, payload, System.currentTimeMillis() + i));
    }
    wal.writeBatch();
    long durationNs = System.nanoTime() - startNs;

    List<LogEntry> entries = wal.readAllSegments();
    assertEquals(
        entryCount,
        entries.size(),
        "Should write all " + entryCount + " entries, got: " + entries.size());
    System.out.println("Test 1: Wrote " + entryCount + " small entries ✓");

    for (LogEntry entry : entries) {
      assertEquals(payload.length, entry.size(), "All entries should have consistent payload size");
    }
    System.out.println("Test 1: All payloads consistent ✓");

    long durationMs = durationNs / 1_000_000;
    double throughput = metrics.getAppendThroughput(entryCount, durationNs);
    System.out.println(
        "Test 1: 10k small entries - "
            + durationMs
            + "ms, "
            + String.format("%.0f", throughput)
            + " entries/sec ✓");

    wal.close();
  }

  @Test
  @Timeout(60)
  void testMixedPayloadSizes() throws IOException {
    wal = new WriteAheadLog(createConfig());

    int smallCount = 3_000;
    int mediumCount = 2_000;
    int largeCount = 1_000;
    int totalCount = smallCount + mediumCount + largeCount;

    byte[] smallPayload = IntegrationTestFixtures.smallPayload();
    byte[] mediumPayload = IntegrationTestFixtures.mediumPayload();
    byte[] largePayload = IntegrationTestFixtures.largePayload();

    long startNs = System.nanoTime();

    for (int i = 0; i < smallCount; i++) {
      wal.append(new LogEntry(smallPayload.length, smallPayload, System.currentTimeMillis() + i));
    }

    for (int i = 0; i < mediumCount; i++) {
      wal.append(
          new LogEntry(
              mediumPayload.length, mediumPayload, System.currentTimeMillis() + smallCount + i));
    }

    for (int i = 0; i < largeCount; i++) {
      wal.append(
          new LogEntry(
              largePayload.length,
              largePayload,
              System.currentTimeMillis() + smallCount + mediumCount + i));
    }

    wal.writeBatch();
    long durationNs = System.nanoTime() - startNs;

    List<LogEntry> entries = wal.readAllSegments();
    assertEquals(
        totalCount,
        entries.size(),
        "Should write all " + totalCount + " mixed entries, got: " + entries.size());
    System.out.println(
        "Test 2: Wrote "
            + smallCount
            + " small + "
            + mediumCount
            + " medium + "
            + largeCount
            + " large ✓");

    int smallVerified = 0, mediumVerified = 0, largeVerified = 0;
    for (LogEntry entry : entries) {
      if (entry.size() == smallPayload.length) smallVerified++;
      else if (entry.size() == mediumPayload.length) mediumVerified++;
      else if (entry.size() == largePayload.length) largeVerified++;
    }

    assertEquals(smallCount, smallVerified, "Should have correct small payload count");
    assertEquals(mediumCount, mediumVerified, "Should have correct medium payload count");
    assertEquals(largeCount, largeVerified, "Should have correct large payload count");
    System.out.println("Test 2: All payload sizes verified ✓");

    double throughput = metrics.getAppendThroughput(totalCount, durationNs);
    System.out.println(
        "Test 2: Mixed payloads - " + String.format("%.0f", throughput) + " entries/sec ✓");

    wal.close();
  }

  @Test
  @Timeout(60)
  void testHundredSegmentsCreation() throws IOException {

    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(10)
            .maxSegmentSize(10 * 1024)
            .build();
    wal = new WriteAheadLog(config);

    int entryCount = 5_000;

    long startNs = System.nanoTime();
    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.writeBatch();
    long durationNs = System.nanoTime() - startNs;

    File logDir = new File(tempDir.toString());
    File[] segments = logDir.listFiles((d, n) -> n.endsWith(".log"));
    int segmentCount = segments != null ? segments.length : 0;

    assertTrue(
        segmentCount >= 30,
        "Should create 30+ segments with 5k entries in 10KB segments, got: " + segmentCount);
    System.out.println("Test 3: Created " + segmentCount + " segments ✓");

    List<LogEntry> entries = wal.readAllSegments();
    assertEquals(
        entryCount,
        entries.size(),
        "Should read all "
            + entryCount
            + " entries from "
            + segmentCount
            + " segments, got: "
            + entries.size());
    System.out.println(
        "Test 3: All " + entryCount + " entries readable from " + segmentCount + " segments ✓");

    assertTimestampsOrdered(entries);
    System.out.println("Test 3: Entries ordered across 30+ segments ✓");

    double throughput = metrics.getAppendThroughput(entryCount, durationNs);
    System.out.println(
        "Test 3: 30+ segment throughput = " + String.format("%.0f", throughput) + " entries/sec ✓");

    wal.close();
  }

  @Test
  @Timeout(60)
  void testRecoveryScalability() throws IOException {
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(50)
            .maxSegmentSize(500 * 1024)
            .build();
    wal = new WriteAheadLog(config);

    int entryCount = 2_000;

    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.writeBatch();
    wal.close();

    System.out.println("Test 4: Wrote " + entryCount + " entries, now recovering... ✓");

    long recoveryStartNs = System.nanoTime();
    WriteAheadLog recoveredWal = new WriteAheadLog(config);
    long recoveryDurationNs = System.nanoTime() - recoveryStartNs;

    List<LogEntry> recovered = recoveredWal.readAllSegments();

    assertEquals(
        entryCount,
        recovered.size(),
        "Should recover all " + entryCount + " entries, got: " + recovered.size());
    System.out.println("Test 4: Recovered all " + entryCount + " entries ✓");

    long recoveryMs = recoveryDurationNs / 1_000_000;
    assertTrue(recoveryMs < 5000, "Recovery should be fast (<5s), took: " + recoveryMs + "ms");
    System.out.println("Test 4: Recovery time = " + recoveryMs + "ms (excellent) ✓");

    assertTimestampsOrdered(recovered);
    System.out.println("Test 4: Recovered entries ordered ✓");

    File logDir = new File(tempDir.toString());
    File[] segments = logDir.listFiles((d, n) -> n.endsWith(".log"));
    int segmentCount = segments != null ? segments.length : 0;
    System.out.println("Test 4: Recovered from " + segmentCount + " segments ✓");

    metrics.recordRecoveryLatency(recoveryDurationNs);
    recoveredWal.close();
  }
}
