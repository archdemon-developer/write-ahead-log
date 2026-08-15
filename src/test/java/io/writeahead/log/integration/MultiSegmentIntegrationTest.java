package io.writeahead.log.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.enums.strategies.RotationPolicyType;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.states.WalSnapshot;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MultiSegmentIntegrationTest extends IntegrationTestBase {

  @Test
  @Timeout(30)
  void testSegmentRotationDuringContinuousWrites() throws IOException {
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(5)
            .maxSegmentSize(1024)
            .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
            .rotationPolicyType(RotationPolicyType.SIZE_BASED)
            .build();
    wal = new WriteAheadLog(config);

    long startNs = System.nanoTime();
    for (int i = 0; i < 50; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.writeBatch();
    long durationNs = System.nanoTime() - startNs;

    List<LogEntry> entries = readAndAssert(50);
    System.out.println("Test 1: Rotation - read 50 entries ✓");

    File logDir = new File(tempDir.toString());
    File[] segments = logDir.listFiles((d, n) -> n.endsWith(".log"));
    assertTrue(
        segments != null && segments.length > 1,
        "Should have multiple segments, got: " + (segments != null ? segments.length : 0));
    System.out.println("Test 1: Created " + segments.length + " segments ✓");

    double throughput = metrics.getAppendThroughput(50, durationNs);
    System.out.println(
        "Test 1: Rotation throughput = " + String.format("%.0f", throughput) + " entries/sec");
  }

  @Test
  @Timeout(30)
  void testSegmentMetadataAccuracyAfterRotation() throws IOException {
    // Setup
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(10)
            .maxSegmentSize(2048) // 2KB segments
            .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
            .build();
    wal = new WriteAheadLog(config);

    long minTs = System.currentTimeMillis();
    for (int i = 0; i < 30; i++) {
      wal.append(new LogEntry(100, new byte[100], minTs + (i * 1000)));
    }
    wal.writeBatch();

    WalSnapshot snapshot = wal.getSnapshot();
    assertNotNull(snapshot, "Snapshot should not be null");
    System.out.println("Test 2: Got snapshot ✓");

    assertTrue(
        snapshot.getTotalSegmentCount() >= 1,
        "Should have at least 1 segment, got: " + snapshot.getTotalSegmentCount());
    System.out.println("Test 2: Segment count = " + snapshot.getTotalSegmentCount() + " ✓");

    long currentMinTs = snapshot.currentSegment().minTimestamp();
    long currentMaxTs = snapshot.currentSegment().maxTimestamp();

    assertTrue(currentMinTs <= currentMaxTs, "Min timestamp should be <= max timestamp");
    assertTrue(currentMinTs >= minTs, "Min timestamp should be >= base timestamp");
    System.out.println("Test 2: Timestamp range = [" + currentMinTs + ", " + currentMaxTs + "] ✓");
  }

  @Test
  @Timeout(30)
  void testSegmentOrderingAfterRecovery() throws IOException {

    wal = new WriteAheadLog(createConfig());

    long baseTimestamp = 1000L;
    for (int seg = 0; seg < 3; seg++) {
      for (int i = 0; i < 10; i++) {
        wal.append(new LogEntry(100, new byte[100], baseTimestamp + (seg * 100) + i));
      }
      wal.writeBatch();
    }
    wal.close();

    long recoveryStartNs = System.nanoTime();
    WriteAheadLog recoveredWal = new WriteAheadLog(createConfig());
    long recoveryDurationNs = System.nanoTime() - recoveryStartNs;
    metrics.recordRecoveryLatency(recoveryDurationNs);

    List<LogEntry> recovered = recoveredWal.readAllSegments();
    assertEquals(30, recovered.size(), "Should recover all 30 entries");
    System.out.println("Test 3: Recovered 30 entries ✓");

    assertTimestampsOrdered(recovered);
    System.out.println("Test 3: Timestamps in order ✓");

    long recoveryMs = recoveryDurationNs / 1_000_000;
    System.out.println("Test 3: Recovery time = " + recoveryMs + "ms");

    recoveredWal.close();
  }

  @Test
  @Timeout(30)
  void testSegmentCountUnderSustainedLoad() throws IOException {

    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(50)
            .maxSegmentSize(10 * 1024)
            .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
            .build();
    wal = new WriteAheadLog(config);

    int entryCount = 1000;
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
        segmentCount >= 2,
        "Should have at least 2 segments with 1000 entries in 10KB segments, got: " + segmentCount);
    assertTrue(
        segmentCount <= 100, "Should not exceed 100 segments (sanity check), got: " + segmentCount);
    System.out.println("Test 4: 1000 entries = " + segmentCount + " segments ✓");

    double throughput = metrics.getAppendThroughput(entryCount, durationNs);
    System.out.println(
        "Test 4: Sustained throughput = " + String.format("%.0f", throughput) + " entries/sec");

    wal.close();
  }

  @Test
  @Timeout(30)
  void testSegmentFileNaming() throws IOException {
    wal = new WriteAheadLog(createConfig());

    for (int i = 0; i < 20; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.writeBatch();
    wal.close();

    File logDir = new File(tempDir.toString());
    File[] segments = logDir.listFiles((d, n) -> n.endsWith(".log"));

    assertTrue(segments != null && segments.length > 0, "Should have at least 1 segment");
    System.out.println("Test 5: Found " + segments.length + " segments ✓");

    for (File segment : segments) {
      String name = segment.getName();

      assertTrue(name.startsWith("wal-"), "Should start with 'wal-': " + name);

      assertTrue(name.endsWith(".log"), "Should end with '.log': " + name);

      String pattern = "wal-\\d{6}\\.log";
      assertTrue(
          name.matches(pattern), "Filename should match pattern wal-{sequence:06d}.log: " + name);
    }
    System.out.println("Test 5: All filenames valid ✓");
  }
}
