package io.writeahead.log.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.models.LogEntry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ConcurrentAccessIntegrationTest extends IntegrationTestBase {

  /**
   * Test 1: Concurrent writers - Multiple threads appending simultaneously. Verify no data loss:
   * all entries should be present. Expected: 10 threads × 100 entries = 1000 entries, all readable.
   */
  @Test
  @Timeout(60)
  void testConcurrentWritersNoDataLoss() throws IOException, InterruptedException {
    // Setup
    wal = new WriteAheadLog(createConfig());

    int threadCount = 10;
    int entriesPerThread = 100;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch finishLatch = new CountDownLatch(threadCount);
    AtomicReference<Exception> error = new AtomicReference<>();

    // Action: Multiple threads appending concurrently
    long startNs = System.nanoTime();

    for (int t = 0; t < threadCount; t++) {
      int threadId = t;
      new Thread(
              () -> {
                try {
                  startLatch.await(); // Synchronize start
                  for (int i = 0; i < entriesPerThread; i++) {
                    long ts = System.currentTimeMillis() + (threadId * 10000) + i;
                    wal.append(new LogEntry(100, new byte[100], ts));
                  }
                } catch (Exception e) {
                  error.set(e);
                } finally {
                  finishLatch.countDown();
                }
              })
          .start();
    }

    startLatch.countDown(); // Start all threads simultaneously
    finishLatch.await(); // Wait for all threads to complete
    wal.writeBatch(); // Final flush

    long durationNs = System.nanoTime() - startNs;

    // Verify: No errors
    assertNull(error.get(), "No exceptions during concurrent write");
    System.out.println("Test 1: No concurrent write errors ✓");

    // Verify: All entries present (no data loss)
    int totalEntries = threadCount * entriesPerThread;
    List<LogEntry> entries = readAndAssert(totalEntries);
    System.out.println("Test 1: All " + totalEntries + " entries present (no data loss) ✓");

    // Performance: Concurrent throughput
    double throughput = metrics.getAppendThroughput(totalEntries, durationNs);
    System.out.println(
        "Test 1: Concurrent throughput ("
            + threadCount
            + " threads) = "
            + String.format("%.0f", throughput)
            + " entries/sec");
  }

  /**
   * Test 2: Reader while writer active. One thread continuously writes, another continuously reads.
   * Verify both operations work without interference.
   */
  @Test
  @Timeout(60)
  void testReaderWhileWriterActive() throws IOException, InterruptedException {
    // Setup
    wal = new WriteAheadLog(createConfig());

    // Pre-populate with 50 entries
    for (int i = 0; i < 50; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.writeBatch();

    // Action: Writer thread + reader thread
    AtomicReference<Exception> writerError = new AtomicReference<>();
    AtomicReference<Exception> readerError = new AtomicReference<>();
    final int[] readerEntryCount = {0};

    Thread writer =
        new Thread(
            () -> {
              try {
                for (int i = 50; i < 150; i++) {
                  wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
                  if (i % 10 == 0) wal.writeBatch();
                }
              } catch (Exception e) {
                writerError.set(e);
              }
            });

    Thread reader =
        new Thread(
            () -> {
              try {
                for (int i = 0; i < 10; i++) {
                  Thread.sleep(10); // Interleave with writer
                  List<LogEntry> entries = wal.readAllSegments();
                  readerEntryCount[0] = entries.size(); // Track latest read count
                }
              } catch (Exception e) {
                readerError.set(e);
              }
            });

    writer.start();
    reader.start();
    writer.join();
    reader.join();
    wal.writeBatch();

    // Verify: No errors
    assertNull(writerError.get(), "No writer errors");
    assertNull(readerError.get(), "No reader errors");
    System.out.println("Test 2: No reader/writer interference ✓");

    // Verify: Reader successfully read entries
    assertTrue(readerEntryCount[0] > 0, "Reader should have read entries");
    System.out.println(
        "Test 2: Reader saw up to " + readerEntryCount[0] + " entries during write ✓");

    // Verify: Final count correct
    assertEquals(150, wal.readAllSegments().size(), "Should have 150 final entries");
    System.out.println("Test 2: Final entry count = 150 ✓");
  }

  /**
   * Test 3: Concurrent readers consistency. Multiple reader threads reading simultaneously. Verify
   * all readers see the same consistent view of the data.
   */
  @Test
  @Timeout(60)
  void testConcurrentReadersConsistency() throws IOException, InterruptedException {
    // Setup
    wal = new WriteAheadLog(createConfig());

    for (int i = 0; i < 200; i++) {
      wal.append(new LogEntry(100, new byte[100], System.currentTimeMillis() + i));
    }
    wal.writeBatch();

    // Action: Multiple readers concurrently reading
    int readerCount = 5;
    CountDownLatch finishLatch = new CountDownLatch(readerCount);
    List<Integer> readCounts = Collections.synchronizedList(new ArrayList<>());

    for (int r = 0; r < readerCount; r++) {
      new Thread(
              () -> {
                try {
                  List<LogEntry> entries = wal.readAllSegments();
                  readCounts.add(entries.size());
                } catch (Exception e) {
                  e.printStackTrace();
                } finally {
                  finishLatch.countDown();
                }
              })
          .start();
    }

    finishLatch.await();

    // Verify: All readers see same count
    assertTrue(
        readCounts.stream().allMatch(count -> count == 200),
        "All readers should see 200 entries, got: " + readCounts);
    System.out.println(
        "Test 3: All " + readerCount + " readers consistent (each saw 200 entries) ✓");
  }

  /**
   * Test 4: Stress test with 20 threads, high throughput. Aggressive configuration: large batch
   * sizes, many threads. Verify system scales without data loss.
   */
  @Test
  @Timeout(60)
  void testStressTest20ThreadsHighThroughput() throws IOException, InterruptedException {
    // Setup: Aggressive configuration for throughput
    config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(100)
            .maxSegmentSize(10 * 1024 * 1024)
            .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
            .build();
    wal = new WriteAheadLog(config);

    int threadCount = 20;
    int entriesPerThread = 500;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch finishLatch = new CountDownLatch(threadCount);
    AtomicReference<Exception> error = new AtomicReference<>();

    long startNs = System.nanoTime();

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
                            System.currentTimeMillis() + (threadId * 100000) + i));
                  }
                } catch (Exception e) {
                  error.set(e);
                } finally {
                  finishLatch.countDown();
                }
              })
          .start();
    }

    startLatch.countDown();
    finishLatch.await();
    wal.writeBatch();

    long durationNs = System.nanoTime() - startNs;

    // Verify: No errors
    assertNull(error.get(), "No errors during high-concurrency stress");
    System.out.println("Test 4: No stress test errors ✓");

    // Verify: All entries
    int totalEntries = threadCount * entriesPerThread;
    assertEquals(
        totalEntries, wal.readAllSegments().size(), "Should have all " + totalEntries + " entries");
    System.out.println(
        "Test 4: All " + totalEntries + " entries from " + threadCount + " threads present ✓");

    // Performance: High-concurrency throughput
    double throughput = metrics.getAppendThroughput(totalEntries, durationNs);
    System.out.println(
        "Test 4: High-concurrency throughput ("
            + threadCount
            + " threads, "
            + entriesPerThread
            + " entries/thread) = "
            + String.format("%.0f", throughput)
            + " entries/sec");

    wal.close();
  }
}
