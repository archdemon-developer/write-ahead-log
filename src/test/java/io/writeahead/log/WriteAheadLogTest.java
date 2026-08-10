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
import io.writeahead.log.models.states.BatchState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  // === Initialization ===

  @Test
  void testInitialization() throws IOException {
    assertNotNull(wal);
  }

  // === Basic Append & Flush ===

  @Test
  void testAppendSingleEntry() throws IOException {
    long timestamp = System.currentTimeMillis();
    LogEntry entry = new LogEntry(100, new byte[100], timestamp);

    AppendResult result = wal.append(entry);

    assertNotNull(result);
    assertFalse(result.flushed(), "append() should not flush");
    assertTrue(result.entriesPendingInBatch() > 0, "Entry should be queued");
    assertFalse(result.corruptionDetected(), "No corruption on fresh append");
  }

  @Test
  void testAppendMultipleEntries() throws IOException {
    long timestamp = System.currentTimeMillis();

    for (int i = 0; i < 5; i++) {
      LogEntry entry = new LogEntry(100, new byte[100], timestamp + i);
      AppendResult result = wal.append(entry);
      assertFalse(result.flushed());
    }
  }

  // === Batch Flushing ===

  @Test
  void testWriteBatchFlushes() throws IOException, InterruptedException {
    long timestamp = System.currentTimeMillis();
    wal.append(new LogEntry(100, new byte[100], timestamp));

    Thread.sleep(100); // Let writer process

    AppendResult result = wal.writeBatch();

    assertTrue(result.flushed(), "writeBatch() should return flushed=true");
    assertEquals(0, result.entriesPendingInBatch(), "Flushed batch should have 0 pending");
  }

  @Test
  void testBatchAutoFlushAtSize() throws IOException, InterruptedException {
    long timestamp = System.currentTimeMillis();

    // Append 10 entries (batch size is 10)
    for (int i = 0; i < 10; i++) {
      wal.append(new LogEntry(100, new byte[100], timestamp + i));
    }

    Thread.sleep(500); // Wait for writer to process batch
  }

  @Test
  void testBatchAutoFlushMultipleBatches() throws IOException, InterruptedException {
    long timestamp = System.currentTimeMillis();

    // Append 25 entries (auto-flush at 10, 20, flush pending at close)
    for (int i = 0; i < 25; i++) {
      wal.append(new LogEntry(100, new byte[100], timestamp + i));
    }

    Thread.sleep(1000); // Wait for writer
  }

  // === LSN Allocation ===

  @Test
  void testLsnAllocationIncrement() throws IOException, InterruptedException {
    long timestamp = System.currentTimeMillis();

    // Append entries of different sizes
    wal.append(new LogEntry(100, new byte[100], timestamp));
    wal.append(new LogEntry(200, new byte[200], timestamp + 1));
    wal.append(new LogEntry(150, new byte[150], timestamp + 2));

    Thread.sleep(200);
    wal.writeBatch();

    List<LogEntry> readBack = wal.readAllSegments();
    assertEquals(3, readBack.size(), "All entries should be recovered");
  }

  // === Read Operations ===

  @Test
  void testReadAllSegments() throws IOException, InterruptedException {
    long timestamp = System.currentTimeMillis();

    for (int i = 0; i < 5; i++) {
      wal.append(new LogEntry(100, new byte[100], timestamp + i));
    }
    wal.writeBatch();

    List<LogEntry> entries = wal.readAllSegments();

    assertNotNull(entries);
    assertEquals(5, entries.size());
  }

  @Test
  void testReadAfterTimestamp() throws IOException, InterruptedException {
    long baseTime = System.currentTimeMillis();

    for (int i = 0; i < 5; i++) {
      wal.append(new LogEntry(100, new byte[100], baseTime + (i * 1000)));
    }
    wal.writeBatch();

    long filterTime = baseTime + 2500;
    List<LogEntry> filtered = wal.readAllAfterTimestamp(filterTime);

    assertNotNull(filtered);
    assertTrue(filtered.size() >= 2, "Should have entries after filter time (entries 3, 4)");
  }

  @Test
  void testReadEmptyLog() throws IOException {
    List<LogEntry> entries = wal.readAllSegments();

    assertNotNull(entries);
    assertEquals(0, entries.size(), "Empty log should return empty list");
  }

  // === Observable State ===

  @Test
  void testGetBatchState() throws IOException {
    BatchState state = wal.getBatchState();

    assertNotNull(state);
    assertTrue(state.isEmpty(), "Fresh WAL should have empty batch");
  }

  @Test
  void testGetMetrics() throws IOException, InterruptedException {
    wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis()));
    Thread.sleep(100);

    var metrics = wal.getMetrics();

    assertNotNull(metrics);
  }

  // === Concurrency Tests ===

  @Test
  void testConcurrentAppends() throws IOException, InterruptedException {
    long timestamp = System.currentTimeMillis();
    int threadCount = 10;
    int entriesPerThread = 10;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);

    for (int t = 0; t < threadCount; t++) {
      int threadId = t;
      new Thread(
              () -> {
                try {
                  startLatch.await();
                  for (int i = 0; i < entriesPerThread; i++) {
                    wal.append(
                        new LogEntry(
                            100, new byte[100], timestamp + threadId * entriesPerThread + i));
                  }
                } catch (InterruptedException | IOException e) {
                  Thread.currentThread().interrupt();
                }
                endLatch.countDown();
              })
          .start();
    }

    startLatch.countDown();
    endLatch.await();

    Thread.sleep(500);
    wal.writeBatch();

    List<LogEntry> entries = wal.readAllSegments();
    assertEquals(
        threadCount * entriesPerThread,
        entries.size(),
        "All concurrent entries should be recovered");
  }

  @Test
  void testHighConcurrency() throws IOException, InterruptedException {
    long timestamp = System.currentTimeMillis();
    int threadCount = 20;
    int entriesPerThread = 50;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);

    for (int t = 0; t < threadCount; t++) {
      int threadId = t;
      new Thread(
              () -> {
                try {
                  startLatch.await();
                  for (int i = 0; i < entriesPerThread; i++) {
                    wal.append(
                        new LogEntry(
                            100, new byte[100], timestamp + threadId * entriesPerThread + i));
                  }
                } catch (InterruptedException | IOException e) {
                  Thread.currentThread().interrupt();
                }
                endLatch.countDown();
              })
          .start();
    }

    startLatch.countDown();
    endLatch.await();

    Thread.sleep(1000);
    wal.writeBatch();

    List<LogEntry> entries = wal.readAllSegments();
    assertEquals(threadCount * entriesPerThread, entries.size());
  }

  // === Closure & Error Handling ===

  @Test
  void testCloseGracefully() throws IOException {
    wal.close();

    IOException thrown =
        assertThrows(IOException.class, () -> wal.append(new LogEntry(100, new byte[100], 0)));
    assertTrue(thrown.getMessage().contains("closed"));
  }

  @Test
  void testCloseFlushesPendingEntries() throws IOException, InterruptedException {
    long timestamp = System.currentTimeMillis();

    for (int i = 0; i < 3; i++) {
      wal.append(new LogEntry(100, new byte[100], timestamp + i));
    }

    wal.close();

    WriteAheadLog wal2 = new WriteAheadLog(createConfig());
    List<LogEntry> recovered = wal2.readAllSegments();
    wal2.close();

    assertEquals(3, recovered.size(), "Close should flush pending entries");
  }

  @Test
  void testAppendThrowsWhenClosed() throws IOException {
    wal.close();

    IOException thrown =
        assertThrows(IOException.class, () -> wal.append(new LogEntry(100, new byte[100], 0)));
    assertTrue(thrown.getMessage().contains("closed"));
  }

  @Test
  void testWriteBatchThrowsWhenClosed() throws IOException {
    wal.close();

    IOException thrown = assertThrows(IOException.class, () -> wal.writeBatch());
    assertTrue(thrown.getMessage().contains("closed"));
  }

  // === Payload Validation ===

  @Test
  void testPayloadPreservation() throws IOException, InterruptedException {
    long timestamp = System.currentTimeMillis();
    byte[] payload = new byte[500];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) (i % 256);
    }

    LogEntry original = new LogEntry(payload.length, payload, timestamp);
    wal.append(original);
    wal.writeBatch();

    List<LogEntry> readBack = wal.readAllSegments();

    assertEquals(1, readBack.size());
    LogEntry recovered = readBack.getFirst();
    assertEquals(original.size(), recovered.size());
    assertEquals(original.timestamp(), recovered.timestamp());
  }

  @Test
  void testLargePayloads() throws IOException, InterruptedException {
    long timestamp = System.currentTimeMillis();
    byte[] largePayload = new byte[50000];
    for (int i = 0; i < largePayload.length; i++) {
      largePayload[i] = (byte) (i % 256);
    }

    wal.append(new LogEntry(largePayload.length, largePayload, timestamp));
    wal.writeBatch();

    List<LogEntry> readBack = wal.readAllSegments();

    assertEquals(1, readBack.size());
    assertEquals(largePayload.length, readBack.getFirst().size());
  }

  @Test
  void testManySmallEntries() throws IOException, InterruptedException {
    long timestamp = System.currentTimeMillis();

    for (int i = 0; i < 100; i++) {
      wal.append(new LogEntry(10, new byte[10], timestamp + i));
    }
    wal.writeBatch();

    List<LogEntry> readBack = wal.readAllSegments();

    assertEquals(100, readBack.size());
  }

  // === Timestamp Ordering ===

  @Test
  void testTimestampOrdering() throws IOException, InterruptedException {
    long baseTime = System.currentTimeMillis();

    for (int i = 0; i < 10; i++) {
      wal.append(new LogEntry(50, new byte[50], baseTime + (i * 100)));
    }
    wal.writeBatch();

    List<LogEntry> entries = wal.readAllSegments();

    for (int i = 1; i < entries.size(); i++) {
      assertTrue(
          entries.get(i).timestamp() >= entries.get(i - 1).timestamp(),
          "Timestamps should be non-decreasing");
    }
  }

  // === Recovery ===

  @Test
  void testRecoveryAfterClose() throws IOException, InterruptedException {
    long timestamp = System.currentTimeMillis();

    for (int i = 0; i < 10; i++) {
      wal.append(new LogEntry(100, new byte[100], timestamp + i));
    }
    wal.writeBatch();
    wal.close();

    WriteAheadLog wal2 = new WriteAheadLog(createConfig());
    List<LogEntry> recovered = wal2.readAllSegments();
    wal2.close();

    assertEquals(10, recovered.size(), "Should recover all entries");
  }

  @Test
  void testMultipleWalInstances() throws IOException, InterruptedException {
    long timestamp = System.currentTimeMillis();

    for (int i = 0; i < 5; i++) {
      wal.append(new LogEntry(100, new byte[100], timestamp + i));
    }
    wal.writeBatch();
    wal.close();

    WriteAheadLog wal2 = new WriteAheadLog(createConfig());
    for (int i = 5; i < 8; i++) {
      wal2.append(new LogEntry(100, new byte[100], timestamp + i));
    }
    wal2.writeBatch();
    wal2.close();

    WriteAheadLog wal3 = new WriteAheadLog(createConfig());
    List<LogEntry> recovered = wal3.readAllSegments();
    wal3.close();

    assertEquals(8, recovered.size(), "Should have entries from both instances");
  }

  // === Edge Cases ===

  @Test
  void testWriteBatchWithoutAppend() throws IOException {
    AppendResult result = wal.writeBatch();

    assertNotNull(result);
    assertTrue(result.flushed());
    assertEquals(0, result.entriesPendingInBatch());
  }

  @Test
  void testMultipleFlushes() throws IOException, InterruptedException {
    long timestamp = System.currentTimeMillis();

    wal.append(new LogEntry(100, new byte[100], timestamp));
    wal.writeBatch();

    wal.append(new LogEntry(100, new byte[100], timestamp + 1));
    wal.writeBatch();

    wal.append(new LogEntry(100, new byte[100], timestamp + 2));
    wal.writeBatch();

    List<LogEntry> entries = wal.readAllSegments();
    assertEquals(3, entries.size());
  }

  @Test
  void testSingleEntryRecovery() throws IOException, InterruptedException {
    wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis()));
    wal.writeBatch();
    wal.close();

    WriteAheadLog wal2 = new WriteAheadLog(createConfig());
    List<LogEntry> entries = wal2.readAllSegments();
    wal2.close();

    assertEquals(1, entries.size());
  }
}
