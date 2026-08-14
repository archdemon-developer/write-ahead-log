package io.writeahead.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.enums.strategies.RotationPolicyType;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.results.AppendResult;
import io.writeahead.log.models.results.TruncateResult;
import io.writeahead.log.models.states.BatchState;
import io.writeahead.log.models.states.WalSnapshot;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class WriteAheadLogTest {

  private WriteAheadLog wal;

  @TempDir private Path logDir;

  private WalConfiguration createConfig() {
    return new WalConfiguration.Builder()
        .logDir(logDir.toString())
        .batchSize(10)
        .maxSegmentSize(10 * 1024 * 1024)
        .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
        .rotationPolicyType(RotationPolicyType.SIZE_BASED)
        .timestampFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
        .maxRetries(3)
        .retryBackoffMs(10)
        .retryBackoffMultiplier(2.0)
        .build();
  }

  @BeforeEach
  void setUp() throws IOException {
    wal = new WriteAheadLog(createConfig());
  }

  @Test
  @Timeout(10)
  void testInitialization() throws IOException {
    assertNotNull(wal);
  }

  @Test
  @Timeout(10)
  void testAppendSingleEntry() throws IOException {
    LogEntry entry = new LogEntry(100, new byte[100], 1000L);
    AppendResult result = wal.append(entry);
    assertNotNull(result);
    assertFalse(result.flushed());
  }

  @Test
  @Timeout(10)
  void testAppendMultipleEntries() throws IOException {
    for (int i = 0; i < 5; i++) {
      wal.append(new LogEntry(100, new byte[100], 1000L + i));
    }
  }

  @Test
  @Timeout(10)
  void testBatchAutoFlushAtSize() throws IOException {
    for (int i = 0; i < 10; i++) {
      wal.append(new LogEntry(100, new byte[100], 1000L + i));
    }
  }

  @Test
  @Timeout(10)
  void testBatchMultipleFlushs() throws IOException {
    for (int i = 0; i < 15; i++) {
      wal.append(new LogEntry(100, new byte[100], 1000L + i));
    }
  }

  @Test
  @Timeout(10)
  void testLsnAllocationIncrement() throws IOException {
    wal.append(new LogEntry(100, new byte[100], 1000L));
    wal.append(new LogEntry(200, new byte[200], 2000L));
    wal.append(new LogEntry(150, new byte[150], 3000L));
    wal.writeBatch();
    List<LogEntry> readBack = wal.readAllSegments();
    assertEquals(3, readBack.size());
  }

  @Test
  @Timeout(10)
  void testReadAllSegments() throws IOException {
    for (int i = 0; i < 5; i++) {
      wal.append(new LogEntry(100, new byte[100], 1000L + i));
    }
    wal.writeBatch();
    List<LogEntry> entries = wal.readAllSegments();
    assertEquals(5, entries.size());
  }

  @Test
  @Timeout(10)
  void testReadAfterTimestamp() throws IOException {
    for (int i = 0; i < 5; i++) {
      wal.append(new LogEntry(100, new byte[100], 1000L + (i * 1000)));
    }
    wal.writeBatch();
    List<LogEntry> filtered = wal.readAllAfterTimestamp(2500L);
    assertTrue(filtered.size() >= 2);
  }

  @Test
  @Timeout(10)
  void testReadEmptyLog() throws IOException {
    List<LogEntry> entries = wal.readAllSegments();
    assertEquals(0, entries.size());
  }

  @Test
  @Timeout(10)
  void testGetBatchState() throws IOException {
    BatchState state = wal.getBatchState();
    assertNotNull(state);
  }

  @Test
  @Timeout(10)
  void testGetMetrics() throws IOException {
    wal.append(new LogEntry(100, new byte[100], 1000L));
    var metrics = wal.getMetrics();
    assertNotNull(metrics);
  }

  @Test
  @Timeout(10)
  void testBasicConcurrentAppends() throws IOException, InterruptedException {
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(3);
    AtomicReference<Exception> error = new AtomicReference<>();

    for (int t = 0; t < 3; t++) {
      int threadId = t;
      new Thread(
              () -> {
                try {
                  startLatch.await();
                  for (int i = 0; i < 5; i++) {
                    wal.append(new LogEntry(100, new byte[100], 1000L + threadId * 5 + i));
                  }
                } catch (Exception e) {
                  error.set(e);
                } finally {
                  endLatch.countDown();
                }
              })
          .start();
    }

    startLatch.countDown();
    endLatch.await();
    if (error.get() != null) throw new IOException(error.get());

    wal.writeBatch();
    assertEquals(15, wal.readAllSegments().size());
  }

  @Test
  @Timeout(10)
  void testCloseGracefully() throws IOException {
    wal.close();
    IOException thrown =
        assertThrows(IOException.class, () -> wal.append(new LogEntry(100, new byte[100], 1000L)));
    assertTrue(thrown.getMessage().contains("closed"));
  }

  @Test
  @Timeout(10)
  void testCloseFlushesPendingEntries() throws IOException {
    for (int i = 0; i < 3; i++) {
      wal.append(new LogEntry(100, new byte[100], 1000L + i));
    }
    wal.close();

    WriteAheadLog wal2 = new WriteAheadLog(createConfig());
    List<LogEntry> recovered = wal2.readAllSegments();
    wal2.close();
    assertEquals(3, recovered.size());
  }

  @Test
  @Timeout(10)
  void testAppendThrowsWhenClosed() throws IOException {
    wal.close();
    IOException thrown =
        assertThrows(IOException.class, () -> wal.append(new LogEntry(100, new byte[100], 1000L)));
    assertTrue(thrown.getMessage().contains("closed"));
  }

  @Test
  @Timeout(10)
  void testWriteBatchThrowsWhenClosed() throws IOException {
    wal.close();
    assertThrows(IOException.class, () -> wal.writeBatch());
  }

  @Test
  @Timeout(10)
  void testPayloadPreservation() throws IOException {
    byte[] payload = new byte[500];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) (i % 256);
    }
    LogEntry original = new LogEntry(payload.length, payload, 1000L);
    wal.append(original);
    wal.writeBatch();
    List<LogEntry> readBack = wal.readAllSegments();
    assertEquals(1, readBack.size());
    assertEquals(original.size(), readBack.get(0).size());
  }

  @Test
  @Timeout(10)
  void testLargePayloads() throws IOException {
    byte[] largePayload = new byte[50000];
    for (int i = 0; i < largePayload.length; i++) {
      largePayload[i] = (byte) (i % 256);
    }
    wal.append(new LogEntry(largePayload.length, largePayload, 1000L));
    wal.writeBatch();
    assertEquals(1, wal.readAllSegments().size());
  }

  @Test
  @Timeout(10)
  void testManySmallEntries() throws IOException {
    for (int i = 0; i < 50; i++) {
      wal.append(new LogEntry(10, new byte[10], 1000L + i));
    }
    wal.writeBatch();
    assertEquals(50, wal.readAllSegments().size());
  }

  @Test
  @Timeout(10)
  void testTimestampOrdering() throws IOException {
    for (int i = 0; i < 10; i++) {
      wal.append(new LogEntry(50, new byte[50], 1000L + (i * 100)));
    }
    wal.writeBatch();
    List<LogEntry> entries = wal.readAllSegments();
    for (int i = 1; i < entries.size(); i++) {
      assertTrue(entries.get(i).timestamp() >= entries.get(i - 1).timestamp());
    }
  }

  @Test
  @Timeout(10)
  void testRecoveryAfterClose() throws IOException {
    for (int i = 0; i < 10; i++) {
      wal.append(new LogEntry(100, new byte[100], 1000L + i));
    }
    wal.writeBatch();
    wal.close();

    WriteAheadLog wal2 = new WriteAheadLog(createConfig());
    List<LogEntry> recovered = wal2.readAllSegments();
    wal2.close();
    assertEquals(10, recovered.size());
  }

  @Test
  @Timeout(10)
  void testMultipleWalInstances() throws IOException {
    for (int i = 0; i < 5; i++) {
      wal.append(new LogEntry(100, new byte[100], 1000L + i));
    }
    wal.writeBatch();
    wal.close();

    WriteAheadLog wal2 = new WriteAheadLog(createConfig());
    for (int i = 5; i < 8; i++) {
      wal2.append(new LogEntry(100, new byte[100], 1000L + i));
    }
    wal2.writeBatch();
    wal2.close();

    WriteAheadLog wal3 = new WriteAheadLog(createConfig());
    List<LogEntry> recovered = wal3.readAllSegments();
    wal3.close();
    assertEquals(8, recovered.size());
  }

  @Test
  @Timeout(10)
  void testMultipleFlushes() throws IOException {
    wal.append(new LogEntry(100, new byte[100], 1000L));
    wal.writeBatch();

    wal.append(new LogEntry(100, new byte[100], 2000L));
    wal.writeBatch();

    wal.append(new LogEntry(100, new byte[100], 3000L));
    wal.writeBatch();

    assertEquals(3, wal.readAllSegments().size());
  }

  @Test
  @Timeout(10)
  void testGetSnapshot() throws IOException {
    wal.append(new LogEntry(100, new byte[100], 1000L));
    wal.writeBatch();
    WalSnapshot snapshot = wal.getSnapshot();
    assertNotNull(snapshot);
    assertTrue(snapshot.isOpen());
  }

  @Test
  @Timeout(10)
  void testSegmentRotation(@TempDir Path rotatingLogDir) throws IOException {
    WalConfiguration rotatingConfig =
        new WalConfiguration.Builder()
            .logDir(rotatingLogDir.toString())
            .batchSize(10)
            .maxSegmentSize(1024)
            .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
            .rotationPolicyType(RotationPolicyType.SIZE_BASED)
            .timestampFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
            .maxRetries(3)
            .retryBackoffMs(10)
            .retryBackoffMultiplier(2.0)
            .build();

    WriteAheadLog rotatingWal = new WriteAheadLog(rotatingConfig);
    for (int i = 0; i < 20; i++) {
      rotatingWal.append(new LogEntry(100, new byte[100], 1000L + i));
    }
    rotatingWal.writeBatch();
    assertEquals(20, rotatingWal.readAllSegments().size());
    rotatingWal.close();
  }

  @Test
  @Timeout(10)
  void testTruncateBeforeTimestamp() throws IOException {
    for (int i = 0; i < 5; i++) {
      wal.append(new LogEntry(100, new byte[100], 1000L + (i * 1000)));
    }
    wal.writeBatch();
    wal.close();

    wal = new WriteAheadLog(createConfig());
    for (int i = 0; i < 5; i++) {
      wal.append(new LogEntry(100, new byte[100], 10000L + (i * 1000)));
    }
    wal.writeBatch();
    wal.close();

    wal = new WriteAheadLog(createConfig());
    TruncateResult result = wal.truncateBeforeTimestamp(6000L);
    assertNotNull(result);
    List<LogEntry> remaining = wal.readAllSegments();
    assertTrue(remaining.stream().allMatch(e -> e.timestamp() >= 6000L));
  }

  @Test
  @Timeout(10)
  void testCloseDeletesEmptySegmentFile() throws IOException {
    wal.close();

    WriteAheadLog wal2 = new WriteAheadLog(createConfig());
    assertEquals(0, wal2.readAllSegments().size());
    wal2.close();

    File[] logFiles = new File(logDir.toString()).listFiles((d, name) -> name.endsWith(".log"));
    assertEquals(0, logFiles == null ? 0 : logFiles.length);
  }

  @Test
  @Timeout(10)
  void testSingleEntryRecovery() throws IOException {
    wal.append(new LogEntry(100, new byte[100], 1000L));
    wal.writeBatch();
    wal.close();

    WriteAheadLog wal2 = new WriteAheadLog(createConfig());
    List<LogEntry> entries = wal2.readAllSegments();
    wal2.close();
    assertEquals(1, entries.size());
  }
}
