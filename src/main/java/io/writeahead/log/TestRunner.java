package io.writeahead.log;

import io.writeahead.log.models.LogEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class TestRunner {

    public static void main(String[] args) throws IOException {
        String logPath = "test-wal.log";

        System.out.println("=== Phase 3: WAL Testing ===\n");

        // Test 1: Normal append and read
        test1_normalAppendAndRead(logPath);

        // Test 2: Batching (10 entries trigger flush)
        test2_batchingBehavior(logPath);

        // Test 3: Crash simulation (write 12, don't close, recover only 10)
        test3_crashRecovery(logPath);

        // Test 4: Proper close flushes remaining batch
        test4_closeFlushesRemainingBatch(logPath);

        // Test 5: Multiple operations in one session
        test5_multipleOperations(logPath);

        System.out.println("\n✅ All tests completed!");
    }

    /**
     * Test 1: Normal flow - append entries and read them back
     */
    private static void test1_normalAppendAndRead(String logPath) throws IOException {
        System.out.println("Test 1: Normal Append and Read");
        cleanLog(logPath);

        WriteAheadLog wal = new WriteAheadLog(10, logPath);

        // Append 5 entries (less than batch size)
        for (int i = 0; i < 5; i++) {
            byte[] data = ("entry-" + i).getBytes();
            wal.append(new LogEntry(data.length, data));
        }

        // Read from buffer (not yet flushed to disk)
        List<LogEntry> bufferEntries = wal.readBuffer();
        System.out.println("  Buffer entries: " + bufferEntries.size());
        assert bufferEntries.size() == 5 : "Expected 5 entries in buffer";

        // Read from disk (nothing yet)
        List<LogEntry> diskEntries = wal.readFromDisk();
        System.out.println("  Disk entries: " + diskEntries.size());
        assert diskEntries.isEmpty() : "Expected 0 entries on disk (batch not full)";

        wal.close();
        System.out.println("  ✅ Test 1 passed\n");
    }

    /**
     * Test 2: Batching behavior - verify batch triggers at size 10
     */
    private static void test2_batchingBehavior(String logPath) throws IOException {
        System.out.println("Test 2: Batching Behavior (batch size = 10)");
        cleanLog(logPath);

        WriteAheadLog wal = new WriteAheadLog(10, logPath);

        // Append 9 entries
        for (int i = 0; i < 9; i++) {
            byte[] data = ("entry-" + i).getBytes();
            wal.append(new LogEntry(data.length, data));
        }

        System.out.println("  After 9 appends:");
        System.out.println("    Buffer: " + wal.readBuffer().size() + " (expected 9)");
        System.out.println("    Disk: " + wal.readFromDisk().size() + " (expected 0)");
        assert wal.readBuffer().size() == 9;
        assert wal.readFromDisk().isEmpty();

        // Append 10th entry - should trigger batch flush
        byte[] data = ("entry-9").getBytes();
        wal.append(new LogEntry(data.length, data));

        System.out.println("  After 10th append (batch full):");
        System.out.println("    Buffer: " + wal.readBuffer().size() + " (expected 0)");
        System.out.println("    Disk: " + wal.readFromDisk().size() + " (expected 10)");
        assert wal.readBuffer().isEmpty() : "Batch should be cleared after flush";
        assert wal.readFromDisk().size() == 10 : "10 entries should be on disk";

        wal.close();
        System.out.println("  ✅ Test 2 passed\n");
    }

    /**
     * Test 3: Crash simulation
     * - Write 12 entries (10 on disk, 2 in buffer)
     * - Don't call close() (simulate crash)
     * - Reopen and verify only 10 recovered
     */
    private static void test3_crashRecovery(String logPath) throws IOException {
        System.out.println("Test 3: Crash Recovery (write 12, recover 10)");
        cleanLog(logPath);

        // Session 1: Write 12 entries, simulate crash
        {
            WriteAheadLog wal = new WriteAheadLog(10, logPath);
            for (int i = 0; i < 12; i++) {
                byte[] data = ("entry-" + i).getBytes();
                wal.append(new LogEntry(data.length, data));
            }
            // DON'T close - simulate crash
            System.out.println("  Session 1: Wrote 12 entries (10 flushed, 2 in buffer)");
            System.out.println("  Simulating crash - not calling close()");
        }

        // Session 2: Reopen and check recovery
        {
            WriteAheadLog wal = new WriteAheadLog(10, logPath);
            List<LogEntry> recovered = wal.readFromDisk();
            System.out.println("  Session 2: Recovered from disk: " + recovered.size() + " entries");
            assert recovered.size() == 10 : "Should recover exactly 10 entries (batch size)";

            List<LogEntry> all = wal.readAll();
            System.out.println("  All entries (disk + buffer): " + all.size());
            assert all.size() == 10 : "Should see only 10 (2 were lost in crash)";

            wal.close();
        }

        System.out.println("  ✅ Test 3 passed\n");
    }

    /**
     * Test 4: Close properly flushes remaining batch
     */
    private static void test4_closeFlushesRemainingBatch(String logPath) throws IOException {
        System.out.println("Test 4: Close Flushes Remaining Batch");
        cleanLog(logPath);

        // Session 1: Write 15 entries, close properly
        {
            WriteAheadLog wal = new WriteAheadLog(10, logPath);
            for (int i = 0; i < 15; i++) {
                byte[] data = ("entry-" + i).getBytes();
                wal.append(new LogEntry(data.length, data));
            }
            System.out.println("  Session 1: Wrote 15 entries");
            System.out.println("    Before close - Buffer: " + wal.readBuffer().size() + ", Disk: " + wal.readFromDisk().size());

            wal.close();  // Should flush remaining 5
            System.out.println("    After close - all entries should be on disk");
        }

        // Session 2: Verify all 15 survived
        {
            WriteAheadLog wal = new WriteAheadLog(10, logPath);
            List<LogEntry> recovered = wal.readFromDisk();
            System.out.println("  Session 2: Recovered " + recovered.size() + " entries (expected 15)");
            assert recovered.size() == 15 : "All 15 entries should survive clean close";
            wal.close();
        }

        System.out.println("  ✅ Test 4 passed\n");
    }

    /**
     * Test 5: Multiple operations in one session
     */
    private static void test5_multipleOperations(String logPath) throws IOException {
        System.out.println("Test 5: Multiple Operations in One Session");
        cleanLog(logPath);

        WriteAheadLog wal = new WriteAheadLog(10, logPath);

        // Append 7 entries
        System.out.println("  Appending 7 entries...");
        for (int i = 0; i < 7; i++) {
            byte[] data = ("batch1-" + i).getBytes();
            wal.append(new LogEntry(data.length, data));
        }
        System.out.println("    Buffer: " + wal.readBuffer().size() + ", Disk: " + wal.readFromDisk().size());

        // Append 5 more (triggers batch flush)
        System.out.println("  Appending 5 more entries (triggers batch)...");
        for (int i = 0; i < 5; i++) {
            byte[] data = ("batch2-" + i).getBytes();
            wal.append(new LogEntry(data.length, data));
        }
        System.out.println("    Buffer: " + wal.readBuffer().size() + ", Disk: " + wal.readFromDisk().size());

        // Append 3 more
        System.out.println("  Appending 3 more entries...");
        for (int i = 0; i < 3; i++) {
            byte[] data = ("batch3-" + i).getBytes();
            wal.append(new LogEntry(data.length, data));
        }
        System.out.println("    Buffer: " + wal.readBuffer().size() + ", Disk: " + wal.readFromDisk().size());

        System.out.println("  ReadAll: " + wal.readAll().size() + " total entries");
        assert wal.readAll().size() == 15 : "Should see 15 total entries";

        wal.close();
        System.out.println("  ✅ Test 5 passed\n");
    }

    private static void cleanLog(String logPath) throws IOException {
        Files.deleteIfExists(Paths.get(logPath));
    }
}
