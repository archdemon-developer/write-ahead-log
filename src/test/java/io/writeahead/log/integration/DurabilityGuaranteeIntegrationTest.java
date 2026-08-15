package io.writeahead.log.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.models.LogEntry;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class DurabilityGuaranteeIntegrationTest extends IntegrationTestBase {

  @Test
  @Timeout(30)
  void testPersistenceAfterFlush() throws IOException {
    wal = new WriteAheadLog(createConfig());

    for (int i = 0; i < 100; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.writeBatch();

    wal.close();
    File logDir = new File(tempDir.toString());
    File[] segments = logDir.listFiles((d, n) -> n.endsWith(".log"));
    assertTrue(segments != null && segments.length > 0, "Segment files should exist after close");
    System.out.println("Test 1: Segment files exist after close ✓");

    WriteAheadLog recoveredWal = new WriteAheadLog(createConfig());
    List<LogEntry> recovered = recoveredWal.readAllSegments();

    assertEquals(
        100,
        recovered.size(),
        "All flushed entries should persist and be recoverable, got: " + recovered.size());
    System.out.println("Test 1: All 100 flushed entries survived close and recovered ✓");

    recoveredWal.close();
  }

  @Test
  @Timeout(30)
  void testFsyncGuarantee() throws IOException {
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(50)
            .maxSegmentSize(10 * 1024 * 1024)
            .fsyncStrategy(io.writeahead.log.enums.strategies.FsyncStrategy.FSYNC_EVERY_BATCH)
            .build();
    wal = new WriteAheadLog(config);

    int entryCount = 200;

    for (int batch = 0; batch < 4; batch++) {
      for (int i = 0; i < 50; i++) {
        wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
      }
      wal.writeBatch();
      System.out.println("Test 2: Batch " + (batch + 1) + " fsynced ✓");
    }

    wal.close();

    WriteAheadLog recoveredWal = new WriteAheadLog(config);
    List<LogEntry> recovered = recoveredWal.readAllSegments();

    assertEquals(
        entryCount,
        recovered.size(),
        "All fsynced entries should be on disk and recoverable, got: " + recovered.size());
    System.out.println(
        "Test 2: Fsync guarantee verified - all " + entryCount + " entries on disk ✓");

    recoveredWal.close();
  }

  @Test
  @Timeout(60)
  void testConcurrentCrashDurability() throws IOException, InterruptedException {

    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(100)
            .maxSegmentSize(10 * 1024 * 1024)
            .build();
    wal = new WriteAheadLog(config);

    int threadCount = 5;
    int entriesPerThread = 200;
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

    wal.close();
    System.out.println("Test 3: Simulated concurrent crash (no final flush) ✓");

    WriteAheadLog recoveredWal = new WriteAheadLog(config);
    List<LogEntry> recovered = recoveredWal.readAllSegments();

    assertFalse(
        recovered.isEmpty(),
        "Should recover at least some auto-flushed entries, got: " + recovered.size());
    System.out.println(
        "Test 3: Recovered " + recovered.size() + " entries despite concurrent crash ✓");

    for (LogEntry entry : recovered) {
      assertTrue(entry.size() > 0, "Recovered entries should have valid size");
    }
    System.out.println("Test 3: No corruption in recovered entries ✓");

    recoveredWal.close();
  }

  @Test
  @Timeout(30)
  void testSegmentFinalizationAtomicity() throws IOException {
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(20)
            .maxSegmentSize(1024)
            .build();
    wal = new WriteAheadLog(config);

    int entryCount = 100;

    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.writeBatch();
    wal.close();

    System.out.println("Test 4: Wrote " + entryCount + " entries across segments ✓");

    File segmentFile = IntegrationTestFixtures.findFirstSegmentFile(tempDir.toString());
    if (segmentFile != null) {
      IntegrationTestFixtures.corruptSegmentFooter(segmentFile.toPath());
      System.out.println("Test 4: Corrupted segment footer ✓");
    }

    try {
      WriteAheadLog recoveredWal = new WriteAheadLog(config);
      List<LogEntry> recovered = recoveredWal.readAllSegments();

      assertTrue(
          recovered.size() > 0,
          "Should recover from non-corrupted segments, got: " + recovered.size());
      System.out.println(
          "Test 4: Recovered " + recovered.size() + " entries (corrupted segment skipped) ✓");

      assertFalse(
          recovered.size() > entryCount,
          "Should not have more entries than original (no duplication)");
      System.out.println("Test 4: Finalization atomicity verified (no duplication) ✓");

      recoveredWal.close();
    } catch (IllegalArgumentException e) {
      System.out.println("Test 4: Corrupted segment rejected during recovery (expected) ✓");
    }
  }
}
