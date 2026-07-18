package io.writeahead.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.writeahead.log.models.LogEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WriteAheadLogTest {

  private final String LOG_PATH = "test-wal.log";

  @BeforeEach
  public void setUp() throws IOException {
    Files.deleteIfExists(Paths.get(LOG_PATH));
  }

  @Test
  public void testNormalAppendAndRead() throws IOException {
    System.out.println("Test: Normal Append and Read");
    WriteAheadLog wal = new WriteAheadLog(10, LOG_PATH);

    for (int i = 0; i < 5; i++) {
      byte[] data = ("entry-" + i).getBytes();
      wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
    }

    List<LogEntry> bufferEntries = wal.readBuffer();
    System.out.println("  Buffer entries: " + bufferEntries.size());
    assert bufferEntries.size() == 5 : "Expected 5 entries in buffer";

    List<LogEntry> diskEntries = wal.readFromDisk();
    System.out.println("  Disk entries: " + diskEntries.size());
    assertTrue(diskEntries.isEmpty(), "Expected 0 entries on disk (batch not full)");
    wal.close();
    System.out.println("  ✅ Test passed\n");
  }

  @Test
  public void testBatchingBehavior() throws IOException {
    System.out.println("Test: Batching Behavior (batch size = 10)");

    WriteAheadLog wal = new WriteAheadLog(10, LOG_PATH);

    for (int i = 0; i < 9; i++) {
      byte[] data = ("entry-" + i).getBytes();
      wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
    }

    System.out.println("  After 9 appends:");
    System.out.println("    Buffer: " + wal.readBuffer().size() + " (expected 9)");
    System.out.println("    Disk: " + wal.readFromDisk().size() + " (expected 0)");
    assertEquals(9, wal.readBuffer().size());
    assertTrue(wal.readFromDisk().isEmpty());

    byte[] data = ("entry-9").getBytes();
    wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));

    System.out.println("  After 10th append (batch full):");
    System.out.println("    Buffer: " + wal.readBuffer().size() + " (expected 0)");
    System.out.println("    Disk: " + wal.readFromDisk().size() + " (expected 10)");
    assertTrue(wal.readBuffer().isEmpty(), "Batch should be cleared after flush");
    assertEquals(10, wal.readFromDisk().size(), "10 entries should be on disk");

    wal.close();
    System.out.println("  ✅ Test passed\n");
  }

  @Test
  public void testMultipleOperations() throws IOException {
    System.out.println("Test: Multiple Operations in One Session");

    WriteAheadLog wal = new WriteAheadLog(10, LOG_PATH);

    System.out.println("  Appending 7 entries...");
    for (int i = 0; i < 7; i++) {
      byte[] data = ("batch1-" + i).getBytes();
      wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
    }
    System.out.println(
        "    Buffer: " + wal.readBuffer().size() + ", Disk: " + wal.readFromDisk().size());

    System.out.println("  Appending 5 more entries (triggers batch)...");
    for (int i = 0; i < 5; i++) {
      byte[] data = ("batch2-" + i).getBytes();
      wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
    }
    System.out.println(
        "    Buffer: " + wal.readBuffer().size() + ", Disk: " + wal.readFromDisk().size());

    System.out.println("  Appending 3 more entries...");
    for (int i = 0; i < 3; i++) {
      byte[] data = ("batch3-" + i).getBytes();
      wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
    }
    System.out.println(
        "    Buffer: " + wal.readBuffer().size() + ", Disk: " + wal.readFromDisk().size());

    System.out.println("  ReadAll: " + wal.readAll().size() + " total entries");
    assertEquals(15, wal.readAll().size(), "Should see 15 total entries");

    wal.close();
    System.out.println("  ✅ Test passed\n");
  }

  @Test
  public void testCloseFlushesRemainingBatch() throws IOException {
    System.out.println("Test: Close Flushes Remaining Batch");

    {
      WriteAheadLog wal = new WriteAheadLog(10, LOG_PATH);
      for (int i = 0; i < 15; i++) {
        byte[] data = ("entry-" + i).getBytes();
        wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
      }
      System.out.println("  Session 1: Wrote 15 entries");
      System.out.println(
          "    Before close - Buffer: "
              + wal.readBuffer().size()
              + ", Disk: "
              + wal.readFromDisk().size());

      wal.close(); // Should flush remaining 5
      System.out.println("    After close - all entries should be on disk");
    }

    {
      WriteAheadLog wal = new WriteAheadLog(10, LOG_PATH);
      List<LogEntry> recovered = wal.readFromDisk();
      System.out.println("  Session 2: Recovered " + recovered.size() + " entries (expected 15)");
      assertEquals(15, recovered.size(), "All 15 entries should survive clean close");
      wal.close();
    }

    System.out.println("  ✅ Test passed\n");
  }

  @Test
  public void testCrashRecovery() throws IOException {
    System.out.println("Test: Crash Recovery (write 12, recover 10)");

    {
      WriteAheadLog wal = new WriteAheadLog(10, LOG_PATH);
      for (int i = 0; i < 12; i++) {
        byte[] data = ("entry-" + i).getBytes();
        wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
      }
      // DON'T close - simulate crash
      System.out.println("  Session 1: Wrote 12 entries (10 flushed, 2 in buffer)");
      System.out.println("  Simulating crash - not calling close()");
    }

    {
      WriteAheadLog wal = new WriteAheadLog(10, LOG_PATH);
      List<LogEntry> recovered = wal.readFromDisk();
      System.out.println("  Session 2: Recovered from disk: " + recovered.size() + " entries");
      assertEquals(10, recovered.size(), "Should recover exactly 10 entries (batch size)");

      List<LogEntry> all = wal.readAll();
      System.out.println("  All entries (disk + buffer): " + all.size());
      assertEquals(10, all.size(), "Should see only 10 (2 were lost in crash)");

      wal.close();
    }

    System.out.println("  ✅ Test passed\n");
  }

  @Test
  void testReadBufferWithMixedState() throws IOException {
    WriteAheadLog wal = new WriteAheadLog(10, LOG_PATH);
    for (int i = 0; i < 10; i++) {
      byte[] data = ("entry-flush-" + i).getBytes();
      wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
    }

    for (int i = 0; i < 3; i++) {
      byte[] data = ("entry-buffer-" + i).getBytes();
      wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
    }

    assertEquals(3, wal.readBuffer().size(), "Buffer should have 3 entries");
    assertEquals(10, wal.readFromDisk().size(), "Disk should have 10 entries");
    assertEquals(13, wal.readAll().size(), "Total should be 13 (10 disk + 3 buffer)");

    wal.close();
  }
}
