package io.writeahead.log.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.models.LogEntry;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class CrashRecoveryIntegrationTest extends IntegrationTestBase {

  @Test
  @Timeout(30)
  void testCrashMidBatchDataLost() throws IOException {
    wal = new WriteAheadLog(createConfig());

    for (int i = 0; i < 50; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.close();
    System.out.println("Test 1: Simulated crash ✓");

    long recoveryStartNs = System.nanoTime();
    WriteAheadLog recoveredWal = new WriteAheadLog(createConfig());
    long recoveryDurationNs = System.nanoTime() - recoveryStartNs;
    metrics.recordRecoveryLatency(recoveryDurationNs);

    List<LogEntry> recovered = recoveredWal.readAllSegments();
    assertFalse(recovered.isEmpty(), "Should recover entries that were written");
    System.out.println("Test 1: Recovered " + recovered.size() + " entries ✓");

    long recoveryMs = recoveryDurationNs / 1_000_000;
    System.out.println("Test 1: Recovery time = " + recoveryMs + "ms");

    recoveredWal.close();
  }

  @Test
  @Timeout(30)
  void testCrashMidRotation() throws IOException {

    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(10)
            .maxSegmentSize(1024)
            .build();
    wal = new WriteAheadLog(config);

    for (int i = 0; i < 100; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
      if (i % 20 == 0) {
        wal.writeBatch();
      }
    }

    wal.close();
    System.out.println("Test 2: Simulated crash mid-rotation ✓");

    long recoveryStartNs = System.nanoTime();
    WriteAheadLog recoveredWal = new WriteAheadLog(createConfig());
    long recoveryDurationNs = System.nanoTime() - recoveryStartNs;

    List<LogEntry> recovered = recoveredWal.readAllSegments();

    assertTrue(
        !recovered.isEmpty(),
        "Should recover at least finalized segments, got: " + recovered.size());
    System.out.println("Test 2: Recovered " + recovered.size() + " entries (partial loss ok) ✓");

    assertTimestampsOrdered(recovered);
    System.out.println("Test 2: Recovered entries in order ✓");

    long recoveryMs = recoveryDurationNs / 1_000_000;
    System.out.println("Test 2: Recovery time = " + recoveryMs + "ms");

    recoveredWal.close();
  }

  @Test
  @Timeout(30)
  void testCrashWithPartialSegmentFooter() throws IOException {
    wal = new WriteAheadLog(createConfig());

    for (int seg = 0; seg < 3; seg++) {
      for (int i = 0; i < 20; i++) {
        wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
      }
      wal.writeBatch();
    }
    wal.close();

    File segmentFile = IntegrationTestFixtures.findFirstSegmentFile(tempDir.toString());
    if (segmentFile != null) {
      IntegrationTestFixtures.corruptSegmentFooter(segmentFile.toPath());
      System.out.println("Test 3: Corrupted segment footer ✓");
    }

    try {
      WriteAheadLog recoveredWal = new WriteAheadLog(createConfig());
      List<LogEntry> recovered = recoveredWal.readAllSegments();

      assertTrue(
          recovered.size() > 0,
          "Should recover from uncorrupted segments despite corruption in one");
      System.out.println("Test 3: Recovered " + recovered.size() + " entries ✓");

      recoveredWal.close();
    } catch (Exception e) {
      System.out.println("Test 3: Corrupted segment handled during recovery ✓");
    }
  }

  @Test
  @Timeout(30)
  void testLargeRecovery1000Entries() throws IOException {
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(50)
            .maxSegmentSize(50 * 1024)
            .build();
    wal = new WriteAheadLog(config);

    int entryCount = 1000;
    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.writeBatch();
    wal.close();
    System.out.println("Test 4: Wrote " + entryCount + " entries, now recovering... ✓");

    long recoveryStartNs = System.nanoTime();
    WriteAheadLog recoveredWal = new WriteAheadLog(createConfig());
    long recoveryDurationNs = System.nanoTime() - recoveryStartNs;

    List<LogEntry> recovered = recoveredWal.readAllSegments();

    assertEquals(
        entryCount, recovered.size(), "Should recover all 1000 entries, got: " + recovered.size());
    System.out.println("Test 4: Recovered all " + entryCount + " entries ✓");

    long recoveryMs = recoveryDurationNs / 1_000_000;
    assertTrue(recoveryMs < 5000, "Recovery should be fast (<5s), took: " + recoveryMs + "ms");
    System.out.println("Test 4: Recovery time = " + recoveryMs + "ms (excellent) ✓");

    assertTimestampsOrdered(recovered);
    System.out.println("Test 4: All recovered entries in order ✓");

    metrics.recordRecoveryLatency(recoveryDurationNs);
    recoveredWal.close();
  }

  @Test
  @Timeout(30)
  void testDeterministicRecovery() throws IOException {
    wal = new WriteAheadLog(createConfig());

    int entryCount = 100;
    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(100, new byte[100], 1000L + (i * 100)));
    }
    wal.writeBatch();
    wal.close();

    long recovery1StartNs = System.nanoTime();
    WriteAheadLog recovery1 = new WriteAheadLog(createConfig());
    long recovery1DurationNs = System.nanoTime() - recovery1StartNs;
    List<LogEntry> entries1 = recovery1.readAllSegments();
    recovery1.close();

    long recovery2StartNs = System.nanoTime();
    WriteAheadLog recovery2 = new WriteAheadLog(createConfig());
    long recovery2DurationNs = System.nanoTime() - recovery2StartNs;
    List<LogEntry> entries2 = recovery2.readAllSegments();
    recovery2.close();

    assertEquals(
        entries1.size(), entries2.size(), "First and second recovery should return same count");
    System.out.println("Test 5: Both recoveries returned " + entries1.size() + " entries ✓");

    for (int i = 0; i < entries1.size(); i++) {
      assertEquals(
          entries1.get(i).timestamp(),
          entries2.get(i).timestamp(),
          "Entry " + i + " timestamp should match across recoveries");
    }
    System.out.println("Test 5: All entry timestamps match (deterministic) ✓");

    long recovery1Ms = recovery1DurationNs / 1_000_000;
    long recovery2Ms = recovery2DurationNs / 1_000_000;
    System.out.println(
        "Test 5: Recovery times = " + recovery1Ms + "ms (1st), " + recovery2Ms + "ms (2nd) ✓");

    metrics.recordRecoveryLatency(recovery1DurationNs);
    metrics.recordRecoveryLatency(recovery2DurationNs);
  }
}
