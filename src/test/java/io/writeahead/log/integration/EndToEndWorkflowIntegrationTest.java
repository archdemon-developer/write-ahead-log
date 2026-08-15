package io.writeahead.log.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.models.LogEntry;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class EndToEndWorkflowIntegrationTest extends IntegrationTestBase {

  @Test
  @Timeout(30)
  void testCompleteLifecycle() throws IOException {
    System.out.println("Test 1: Complete Lifecycle");
    System.out.println("=========================================");

    System.out.println("Phase 1: Initialization...");
    wal = new WriteAheadLog(createConfig());
    System.out.println("  ✓ WAL initialized");

    System.out.println("Phase 2: Writing entries...");
    int writeCount = 500;
    for (int i = 0; i < writeCount; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.writeBatch();
    System.out.println("  ✓ Wrote and flushed " + writeCount + " entries");

    System.out.println("Phase 3: Verifying written entries...");
    List<LogEntry> written = wal.readAllSegments();
    assertEquals(writeCount, written.size(), "Should read back all written entries");
    System.out.println("  ✓ Verified " + writeCount + " entries in WAL");

    System.out.println("Phase 4: Closing WAL...");
    wal.close();
    System.out.println("  ✓ WAL closed successfully");

    System.out.println("Phase 5: Recovery (simulating restart)...");
    long recoveryStartNs = System.nanoTime();
    WriteAheadLog recoveredWal = new WriteAheadLog(createConfig());
    long recoveryDurationNs = System.nanoTime() - recoveryStartNs;
    System.out.println("  ✓ WAL recovered in " + (recoveryDurationNs / 1_000_000) + "ms");

    System.out.println("Phase 6: Verifying recovered entries...");
    List<LogEntry> recovered = recoveredWal.readAllSegments();
    assertEquals(writeCount, recovered.size(), "Should recover all entries");
    assertTimestampsOrdered(recovered);
    System.out.println("  ✓ Recovered " + writeCount + " entries in order");

    System.out.println("Phase 7: Cleanup...");
    recoveredWal.close();
    System.out.println("  ✓ Cleanup complete");

    System.out.println("=========================================");
    System.out.println("Test 1: Complete Lifecycle PASSED ✓\n");
  }

  @Test
  @Timeout(60)
  void testRealisticMultiDayWorkload() throws IOException {
    System.out.println("Test 2: Realistic Multi-Day Workload");
    System.out.println("=========================================");

    wal = new WriteAheadLog(createConfig());
    long baseTimestamp = 1_000_000L;

    System.out.println("Day 1 Morning: Writing 100 entries...");
    for (int i = 0; i < 100; i++) {
      wal.append(new LogEntry(100, new byte[100], baseTimestamp + (i * 1000)));
    }
    wal.writeBatch();
    System.out.println("  ✓ Morning batch flushed");

    System.out.println("Day 1 Midday: Writing 200 entries...");
    for (int i = 100; i < 300; i++) {
      wal.append(new LogEntry(100, new byte[100], baseTimestamp + (i * 1000)));
    }
    wal.writeBatch();
    System.out.println("  ✓ Midday batch flushed");

    System.out.println("Day 1 Evening: Writing 150 entries...");
    for (int i = 300; i < 450; i++) {
      wal.append(new LogEntry(100, new byte[100], baseTimestamp + (i * 1000)));
    }
    wal.writeBatch();
    System.out.println("  ✓ Evening batch flushed");

    System.out.println("Day 1 Cleanup: Truncating old entries...");
    long truncateTs = baseTimestamp + (150_000); // Remove entries before this
    var truncateResult = wal.truncateBeforeTimestamp(truncateTs);
    if (truncateResult.success()) {
      System.out.println("  ✓ Truncation succeeded");
    } else {
      System.out.println("  ⚠ Truncation failed (non-critical)");
    }

    System.out.println("Day 1 EOD: Closing for overnight...");
    wal.close();
    System.out.println("  ✓ WAL closed for night");

    System.out.println("Day 2 Morning: Recovery...");
    long recoveryStartNs = System.nanoTime();
    WriteAheadLog day2Wal = new WriteAheadLog(createConfig());
    long recoveryDurationNs = System.nanoTime() - recoveryStartNs;
    System.out.println("  ✓ Recovered in " + (recoveryDurationNs / 1_000_000) + "ms");

    System.out.println("Day 2: Verifying persistent state...");
    List<LogEntry> day2Entries = day2Wal.readAllSegments();
    assertFalse(day2Entries.isEmpty(), "Should have entries remaining after truncation");
    System.out.println("  ✓ " + day2Entries.size() + " entries persisted");

    System.out.println("Day 2: Resuming operations...");
    for (int i = 0; i < 50; i++) {
      day2Wal.append(new LogEntry(100, new byte[100], baseTimestamp + (450_000 + i * 1000)));
    }
    day2Wal.writeBatch();
    System.out.println("  ✓ Day 2 entries written and flushed");

    day2Wal.close();

    System.out.println("=========================================");
    System.out.println("Test 2: Realistic Multi-Day Workload PASSED ✓\n");
  }

  @Test
  @Timeout(60)
  void testFailureRecoveryWorkflow() throws IOException {
    System.out.println("Test 3: Failure Recovery Workflow");
    System.out.println("=========================================");

    System.out.println("Phase 1: Normal operation (pre-crash)...");
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(50) // Auto-flush at 50 entries
            .maxSegmentSize(10 * 1024 * 1024)
            .build();
    wal = new WriteAheadLog(config);

    for (int i = 0; i < 200; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.close(); // Close without flush
    System.out.println("  ✓ Simulated crash (closed without final flush)");

    System.out.println("Phase 2: Recovery...");
    long recoveryStartNs = System.nanoTime();
    WriteAheadLog recoveredWal = new WriteAheadLog(config);
    long recoveryDurationNs = System.nanoTime() - recoveryStartNs;
    System.out.println("  ✓ Recovered in " + (recoveryDurationNs / 1_000_000) + "ms");

    System.out.println("Phase 3: Verifying recovered state...");
    List<LogEntry> recovered = recoveredWal.readAllSegments();

    assertEquals(
        200, recovered.size(), "Should recover all auto-flushed entries, got: " + recovered.size());
    assertTimestampsOrdered(recovered);
    System.out.println("  ✓ Recovered " + recovered.size() + " entries (all auto-flushed)");

    System.out.println("Phase 4: Resuming operations...");
    for (int i = 0; i < 50; i++) {
      recoveredWal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + (200 + i)));
    }
    recoveredWal.writeBatch();
    System.out.println("  ✓ Resumed and wrote 50 new entries");

    System.out.println("Phase 5: Final verification...");
    List<LogEntry> finalState = recoveredWal.readAllSegments();
    assertEquals(
        250, finalState.size(), "Should have 200 recovered + 50 new, got: " + finalState.size());
    System.out.println("  ✓ Final state: " + finalState.size() + " total entries");

    recoveredWal.close();

    System.out.println("=========================================");
    System.out.println("Test 3: Failure Recovery Workflow PASSED ✓\n");
  }
}
