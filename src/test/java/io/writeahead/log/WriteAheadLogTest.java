package io.writeahead.log;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.LogEntry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WriteAheadLogTest {

  @TempDir Path tempDir;

  private String logPath;

  @BeforeEach
  void setUp() {
    logPath = tempDir.toString();
  }

  @Test
  void testAppendBelowBatchSize() throws IOException {
    WriteAheadLog wal = new WriteAheadLog(10, logPath);

    // Append 5 entries (less than batch size of 10)
    for (int i = 0; i < 5; i++) {
      byte[] data = ("entry-" + i).getBytes();
      wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
    }

    // Entries should be in buffer, not on disk
    assertEquals(5, wal.readBuffer().size(), "Buffer should have 5 entries");
    assertTrue(wal.readFromDisk().isEmpty(), "Disk should be empty (batch not full)");

    wal.close();
  }

  @Test
  void testBatchFlushAtThreshold() throws IOException {
    WriteAheadLog wal = new WriteAheadLog(10, logPath);

    // Append 9 entries (below threshold)
    for (int i = 0; i < 9; i++) {
      byte[] data = ("entry-" + i).getBytes();
      wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
    }

    assertEquals(9, wal.readBuffer().size(), "Buffer should have 9 entries");
    assertTrue(wal.readFromDisk().isEmpty(), "Disk should be empty");

    // Append 10th entry (triggers flush)
    byte[] data = "entry-9".getBytes();
    wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));

    assertTrue(wal.readBuffer().isEmpty(), "Buffer should be cleared after flush");
    assertEquals(10, wal.readFromDisk().size(), "Disk should have 10 entries");

    wal.close();
  }

  @Test
  void testMultipleBatches() throws IOException {
    WriteAheadLog wal = new WriteAheadLog(5, logPath);

    // Write 3 full batches (15 entries)
    for (int i = 0; i < 15; i++) {
      byte[] data = ("entry-" + i).getBytes();
      wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
    }

    assertEquals(15, wal.readFromDisk().size(), "Should have 15 entries on disk (3 batches)");
    assertTrue(wal.readBuffer().isEmpty(), "Buffer should be empty after 3 full batches");

    wal.close();
  }

  @Test
  void testReadBuffer() throws IOException {
    WriteAheadLog wal = new WriteAheadLog(10, logPath);

    List<LogEntry> entries =
        List.of(
            new LogEntry("entry-1".getBytes().length, "entry-1".getBytes(), 1000),
            new LogEntry("entry-2".getBytes().length, "entry-2".getBytes(), 2000),
            new LogEntry("entry-3".getBytes().length, "entry-3".getBytes(), 3000));

    for (LogEntry entry : entries) {
      wal.append(entry);
    }

    List<LogEntry> buffered = wal.readBuffer();
    assertEquals(3, buffered.size(), "Buffer should have 3 entries");
    assertEquals("entry-1", new String(buffered.getFirst().data()));

    wal.close();
  }

  @Test
  void testReadFromDisk() throws IOException {
    WriteAheadLog wal = new WriteAheadLog(5, logPath);

    // Write 10 entries (2 batches)
    for (int i = 0; i < 10; i++) {
      byte[] data = ("entry-" + i).getBytes();
      wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
    }

    List<LogEntry> fromDisk = wal.readFromDisk();
    assertEquals(10, fromDisk.size(), "Should read all persisted entries");

    wal.close();
  }

  @Test
  void testReadAll() throws IOException {
    WriteAheadLog wal = new WriteAheadLog(10, logPath);

    // Write 15 entries (1 batch flushed + 5 in buffer)
    for (int i = 0; i < 15; i++) {
      byte[] data = ("entry-" + i).getBytes();
      wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
    }

    List<LogEntry> all = wal.readAll();
    assertEquals(15, all.size(), "Should return disk + buffer entries");

    // Verify order: disk entries first, then buffer
    assertEquals("entry-0", new String(all.getFirst().data()));
    assertEquals("entry-14", new String(all.get(14).data()));

    wal.close();
  }

  @Test
  void testCloseFlushesRemainingBatch() throws IOException {
    // Session 1: Write and close (should flush remaining)
    {
      WriteAheadLog wal = new WriteAheadLog(10, logPath);

      // Write 15 entries (1 batch flushed + 5 remaining)
      for (int i = 0; i < 15; i++) {
        byte[] data = ("entry-" + i).getBytes();
        wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
      }

      assertEquals(5, wal.readBuffer().size(), "Buffer should have 5 remaining entries");
      wal.close(); // Should flush remaining 5
    }

    // Session 2: Recover and verify
    {
      WriteAheadLog wal = new WriteAheadLog(10, logPath);
      List<LogEntry> recovered = wal.readFromDisk();

      assertEquals(15, recovered.size(), "All 15 entries should survive close");
      assertEquals("entry-0", new String(recovered.getFirst().data()));
      assertEquals("entry-14", new String(recovered.get(14).data()));

      wal.close();
    }
  }

  @Test
  void testCrashRecovery() throws IOException {
    // Session 1: Write 12 entries without closing (simulate crash)
    {
      WriteAheadLog wal = new WriteAheadLog(10, logPath);

      for (int i = 0; i < 12; i++) {
        byte[] data = ("entry-" + i).getBytes();
        wal.append(new LogEntry(data.length, data, System.currentTimeMillis()));
      }

      // DON'T close - simulate crash
      // 10 entries flushed to disk, 2 in buffer (lost)
    }

    // Session 2: Recover from crash
    {
      WriteAheadLog wal = new WriteAheadLog(10, logPath);

      List<LogEntry> recovered = wal.readFromDisk();
      assertEquals(10, recovered.size(), "Should recover only flushed entries");

      List<LogEntry> all = wal.readAll();
      assertEquals(10, all.size(), "Total should be 10 (2 lost entries in buffer)");

      wal.close();
    }
  }

  @Test
  void testReadAllAfterTimestamp() throws IOException {
    WriteAheadLog wal = new WriteAheadLog(20, logPath);

    // Write entries with specific timestamps
    List<LogEntry> entries =
        List.of(
            new LogEntry("entry-1".getBytes().length, "entry-1".getBytes(), 1000),
            new LogEntry("entry-2".getBytes().length, "entry-2".getBytes(), 2000),
            new LogEntry("entry-3".getBytes().length, "entry-3".getBytes(), 3000),
            new LogEntry("entry-4".getBytes().length, "entry-4".getBytes(), 4000),
            new LogEntry("entry-5".getBytes().length, "entry-5".getBytes(), 5000));

    for (LogEntry entry : entries) {
      wal.append(entry);
    }

    wal.close();

    // Reopen and query
    WriteAheadLog wal2 = new WriteAheadLog(20, logPath);
    List<LogEntry> afterTimestamp = wal2.readAllAfterTimestamp(2500);

    assertEquals(3, afterTimestamp.size(), "Should return 3 entries after timestamp 2500");
    assertEquals("entry-3", new String(afterTimestamp.get(0).data()));
    assertEquals("entry-5", new String(afterTimestamp.get(2).data()));

    wal2.close();
  }

  @Test
  void testTruncateBeforeTimestamp() throws IOException {
    WriteAheadLog wal = new WriteAheadLog(1, logPath);

    byte[] largeData = new byte[5 * 1024 * 1024];

    List<LogEntry> entries =
        List.of(
            new LogEntry(largeData.length, largeData, 1000),
            new LogEntry(largeData.length, largeData, 2000),
            new LogEntry(largeData.length, largeData, 3000));

    for (LogEntry entry : entries) {
      wal.append(entry);
    }

    wal.close();

    // Truncate before 2500 - this removes Segment 1 (maxTs=2000)
    WriteAheadLog wal2 = new WriteAheadLog(1, logPath);
    wal2.truncateBeforeTimestamp(2500);
    wal2.close();

    // Verify only Segment 2+ remain
    WriteAheadLog wal3 = new WriteAheadLog(1, logPath);
    List<LogEntry> remaining = wal3.readFromDisk();

    assertTrue(remaining.size() < 3, "Should have fewer entries after truncation");

    wal3.close();
  }

  @Test
  void testImmutabilityOfReturnedLists() throws IOException {
    WriteAheadLog wal = new WriteAheadLog(10, logPath);

    byte[] data = "entry-1".getBytes();
    wal.append(new LogEntry(data.length, data, 1000));

    List<LogEntry> buffer = wal.readBuffer();
    assertThrows(
        UnsupportedOperationException.class,
        () -> buffer.add(new LogEntry(0, new byte[0], 0)),
        "Returned list should be immutable");

    wal.close();
  }

  @Test
  void testMultipleSessionsPreserveData() throws IOException {
    // Session 1
    {
      WriteAheadLog wal = new WriteAheadLog(5, logPath);
      for (int i = 0; i < 7; i++) {
        byte[] data = ("session1-entry-" + i).getBytes();
        wal.append(new LogEntry(data.length, data, 1000 + i));
      }
      wal.close();
    }

    // Session 2
    {
      WriteAheadLog wal = new WriteAheadLog(5, logPath);
      for (int i = 0; i < 8; i++) {
        byte[] data = ("session2-entry-" + i).getBytes();
        wal.append(new LogEntry(data.length, data, 2000 + i));
      }
      wal.close();
    }

    // Session 3: Verify all data
    {
      WriteAheadLog wal = new WriteAheadLog(5, logPath);
      List<LogEntry> all = wal.readFromDisk();

      assertEquals(15, all.size(), "Should have entries from both sessions");
      assertEquals("session1-entry-0", new String(all.getFirst().data()));
      assertEquals("session2-entry-7", new String(all.get(14).data()));

      wal.close();
    }
  }

  @Test
  void testEmptyWalOnCreation() throws IOException {
    WriteAheadLog wal = new WriteAheadLog(10, logPath);

    assertTrue(wal.readBuffer().isEmpty(), "Buffer should be empty on creation");
    assertTrue(wal.readFromDisk().isEmpty(), "Disk should be empty on creation");
    assertTrue(wal.readAll().isEmpty(), "Total should be empty on creation");

    wal.close();
  }

  @Test
  void testLargeEntries() throws IOException {
    WriteAheadLog wal = new WriteAheadLog(3, logPath);

    // Create large entries (1MB each)
    byte[] largeData = new byte[1024 * 1024];
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }

    List<LogEntry> entries =
        List.of(
            new LogEntry(largeData.length, largeData, 1000),
            new LogEntry(largeData.length, largeData, 2000),
            new LogEntry(largeData.length, largeData, 3000));

    for (LogEntry entry : entries) {
      wal.append(entry);
    }

    List<LogEntry> recovered = wal.readFromDisk();
    assertEquals(3, recovered.size(), "Should handle large entries");

    wal.close();
  }

  @Test
  void testReadMethodAliases() throws IOException {
    WriteAheadLog wal = new WriteAheadLog(10, logPath);

    byte[] data = "entry-1".getBytes();
    wal.append(new LogEntry(data.length, data, 1000));

    // read() and readBuffer() should be equivalent for buffered entries
    List<LogEntry> read = wal.read();
    List<LogEntry> buffer = wal.readBuffer();

    assertEquals(read.size(), buffer.size(), "read() and readBuffer() should match");

    wal.close();
  }
}
