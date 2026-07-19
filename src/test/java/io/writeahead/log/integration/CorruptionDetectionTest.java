package io.writeahead.log.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.exceptions.CorruptedEntryException;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.WalConfiguration;
import io.writeahead.log.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CorruptionDetectionTest {

  @TempDir Path tempDir;
  private String logPath;

  @BeforeEach
  void setUp() {
    logPath = tempDir.toString();
  }

  @Test
  void testCorruptedEntryDetected() throws IOException {
    {
      WalConfiguration config = new WalConfiguration.Builder().batchSize(5).logDir(logPath).build();
      WriteAheadLog wal = new WriteAheadLog(config);
      wal.append(new LogEntry("entry-1".getBytes().length, "entry-1".getBytes(), 1000));
      wal.append(new LogEntry("entry-2".getBytes().length, "entry-2".getBytes(), 2000));
      wal.append(new LogEntry("entry-3".getBytes().length, "entry-3".getBytes(), 3000));
      wal.close();
    }

    {
      List<File> logFiles = FileUtils.listLogFiles(logPath);
      assertFalse(logFiles.isEmpty(), "Should have at least one log file");
      File logFile = logFiles.getFirst();
      byte[] allBytes = FileUtils.readAllBytes(logFile);
      if (allBytes.length > 50) {
        allBytes[50] = (byte) (~allBytes[50]);
      }
      Files.write(logFile.toPath(), allBytes);
    }

    {
      WalConfiguration config = new WalConfiguration.Builder().batchSize(5).logDir(logPath).build();
      WriteAheadLog wal = new WriteAheadLog(config);
      CorruptedEntryException exception =
          assertThrows(
              CorruptedEntryException.class,
              wal::readFromDisk,
              "Should throw CorruptedEntryException when reading corrupted entry");
      assertNotNull(exception.getSegmentName(), "Exception should have segment name");
      assertNotEquals(
          exception.getComputedCrc(), exception.getStoredCrc(), "CRCs should not match");
      wal.close();
    }
  }

  @Test
  void testPartialEntryAtEofHandled() throws IOException {
    {
      WalConfiguration config = new WalConfiguration.Builder().batchSize(3).logDir(logPath).build();
      WriteAheadLog wal = new WriteAheadLog(config);

      // This will trigger 1 flush after 3rd entry
      for (int i = 0; i < 5; i++) {
        byte[] data = ("entry-" + i).getBytes();
        wal.append(new LogEntry(data.length, data, 1000 + i));
      }

      // Don't close - simulate crash
      // First 3 entries flushed, entries 3-4 in buffer (lost)
    }

    {
      List<File> logFiles = FileUtils.listLogFiles(logPath);
      File logFile = logFiles.getFirst();
      byte[] allBytes = FileUtils.readAllBytes(logFile);

      // Truncate last 20 bytes (partial entry)
      byte[] truncated = new byte[Math.max(0, allBytes.length - 20)];
      System.arraycopy(allBytes, 0, truncated, 0, truncated.length);

      Files.write(logFile.toPath(), truncated);
    }

    {
      WalConfiguration config = new WalConfiguration.Builder().batchSize(3).logDir(logPath).build();
      WriteAheadLog wal = new WriteAheadLog(config);
      List<LogEntry> recovered = wal.readFromDisk();

      assertFalse(recovered.isEmpty(), "Should recover at least some entries before truncation");
      assertTrue(recovered.size() <= 3, "Should not recover more than 3 flushed entries");

      wal.close();
    }
  }

  @Test
  void testNormalWriteReadStillWorks() throws IOException {
    {
      WalConfiguration config = new WalConfiguration.Builder().batchSize(5).logDir(logPath).build();
      WriteAheadLog wal = new WriteAheadLog(config);
      for (int i = 0; i < 5; i++) {
        byte[] data = ("entry-" + i).getBytes();
        wal.append(new LogEntry(data.length, data, 1000 + i));
      }
      wal.close();
    }

    {
      WalConfiguration config = new WalConfiguration.Builder().batchSize(5).logDir(logPath).build();
      WriteAheadLog wal = new WriteAheadLog(config);
      List<LogEntry> recovered = wal.readFromDisk();
      assertEquals(5, recovered.size(), "Should recover all 5 entries");
      assertEquals("entry-0", new String(recovered.get(0).data()));
      assertEquals("entry-4", new String(recovered.get(4).data()));
      assertDoesNotThrow(wal::readFromDisk);
      wal.close();
    }
  }

  @Test
  void testCorruptedCrcValueDetected() throws IOException {
    {
      WalConfiguration config =
          new WalConfiguration.Builder().batchSize(10).logDir(logPath).build();
      WriteAheadLog wal = new WriteAheadLog(config);
      byte[] data = "test-data".getBytes();
      wal.append(new LogEntry(data.length, data, 5000));
      wal.close();
    }

    {
      List<File> logFiles = FileUtils.listLogFiles(logPath);
      File logFile = logFiles.getFirst();
      byte[] allBytes = FileUtils.readAllBytes(logFile);
      if (allBytes.length >= 8) {
        for (int i = 0; i < 8; i++) {
          allBytes[allBytes.length - 8 + i] = (byte) (~allBytes[allBytes.length - 8 + i]);
        }
      }
      Files.write(logFile.toPath(), allBytes);
    }

    {
      WalConfiguration config =
          new WalConfiguration.Builder().batchSize(10).logDir(logPath).build();
      WriteAheadLog wal = new WriteAheadLog(config);
      assertThrows(
          CorruptedEntryException.class,
          wal::readFromDisk,
          "Should throw CorruptedEntryException when CRC is corrupted");
      wal.close();
    }
  }

  @Test
  void testDataCorruptionDetected() throws IOException {
    {
      WalConfiguration config =
          new WalConfiguration.Builder().batchSize(10).logDir(logPath).build();
      WriteAheadLog wal = new WriteAheadLog(config);
      byte[] data = "important-data".getBytes();
      wal.append(new LogEntry(data.length, data, 7000));
      wal.close();
    }

    {
      List<File> logFiles = FileUtils.listLogFiles(logPath);
      File logFile = logFiles.getFirst();
      byte[] allBytes = FileUtils.readAllBytes(logFile);
      if (allBytes.length > 20) {
        allBytes[20] = (byte) (allBytes[20] ^ 0xFF);
      }
      Files.write(logFile.toPath(), allBytes);
    }

    {
      WalConfiguration config =
          new WalConfiguration.Builder().batchSize(10).logDir(logPath).build();
      WriteAheadLog wal = new WriteAheadLog(config);
      CorruptedEntryException exception =
          assertThrows(
              CorruptedEntryException.class,
              wal::readFromDisk,
              "Should throw CorruptedEntryException when data is corrupted");
      assertTrue(
          exception.getComputedCrc() != exception.getStoredCrc(),
          "CRCs should differ due to data corruption");
      wal.close();
    }
  }

  @Test
  void testMultipleEntriesStopsAtCorruption() throws IOException {
    {
      WalConfiguration config = new WalConfiguration.Builder().batchSize(3).logDir(logPath).build();
      WriteAheadLog wal = new WriteAheadLog(config);
      for (int i = 0; i < 6; i++) {
        byte[] data = ("entry-" + i).getBytes();
        wal.append(new LogEntry(data.length, data, 1000 + i));
      }
      wal.close();
    }

    {
      List<File> logFiles = FileUtils.listLogFiles(logPath);
      File logFile = logFiles.getFirst();
      byte[] allBytes = FileUtils.readAllBytes(logFile);
      if (allBytes.length > 100) {
        allBytes[100] = (byte) (~allBytes[100]);
      }
      Files.write(logFile.toPath(), allBytes);
    }

    {
      WalConfiguration config = new WalConfiguration.Builder().batchSize(3).logDir(logPath).build();
      WriteAheadLog wal = new WriteAheadLog(config);
      assertThrows(
          CorruptedEntryException.class,
          wal::readFromDisk,
          "Should throw CorruptedEntryException at corrupted entry");
      wal.close();
    }
  }
}
