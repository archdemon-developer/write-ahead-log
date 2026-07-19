package io.writeahead.log.stress;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.enums.LogLevel;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.WalConfiguration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LoadStressTest {

  @TempDir Path tempDir;
  private String logPath;

  @BeforeEach
  void setUp() {
    logPath = tempDir.toString();
    LoggerFactory.setLogLevel(LogLevel.ERROR);
  }

  @Test
  void testLightLoad() throws IOException, InterruptedException {
    runLoadTest("Light Load", 2, 500, 10);
  }

  @Test
  void testMediumLoad() throws IOException, InterruptedException {
    runLoadTest("Medium Load", 5, 1000, 10);
  }

  @Test
  void testHeavyLoad() throws IOException, InterruptedException {
    runLoadTest("Heavy Load", 10, 2000, 10);
  }

  @Test
  void testExtremeLoad() throws IOException, InterruptedException {
    runLoadTest("Extreme Load", 20, 5000, 10);
  }

  @Test
  void testVariableBatchSizes() throws IOException, InterruptedException {
    System.out.println("\n========== Variable Batch Size Test ==========");
    runBatchSizeComparison(5, 1000);
  }

  @Test
  void testSmallEntriesVsLargeEntries() throws IOException, InterruptedException {
    System.out.println("\n========== Entry Size Comparison Test ==========");
    compareEntrySizes(5, 1000);
  }

  @Test
  void testWriteReadMix() throws IOException, InterruptedException {
    System.out.println("\n========== Write/Read Mix Test ==========");
    runWriteReadMixTest(5, 100, 5);
  }

  // ============ Core Load Test ============
  private void runLoadTest(String testName, int numThreads, int entriesPerThread, int batchSize)
      throws IOException, InterruptedException {
    System.out.println("\n========== " + testName + " ==========");
    System.out.println("Threads: " + numThreads);
    System.out.println("Entries per thread: " + entriesPerThread);
    System.out.println("Batch size: " + batchSize);
    System.out.println("Total entries: " + (numThreads * entriesPerThread));

    WalConfiguration config =
        new WalConfiguration.Builder().batchSize(batchSize).logDir(logPath).build();
    WriteAheadLog wal = new WriteAheadLog(config);

    int totalEntries = numThreads * entriesPerThread;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(numThreads);

    AtomicLong totalTime = new AtomicLong(0);
    AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
    AtomicLong maxLatency = new AtomicLong(0);
    List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger exceptions = new AtomicInteger(0);

    ExecutorService executor = Executors.newFixedThreadPool(numThreads);

    long testStartTime = System.nanoTime();

    for (int threadId = 0; threadId < numThreads; threadId++) {
      final int tid = threadId;
      executor.submit(
          () -> {
            try {
              startLatch.await();
              for (int i = 0; i < entriesPerThread; i++) {
                String data = String.format("T%d-E%d", tid, i);
                long opStart = System.nanoTime();
                wal.append(new LogEntry(data.getBytes().length, data.getBytes(), 1000L + i));
                long opLatency = System.nanoTime() - opStart;

                latencies.add(opLatency / 1_000_000);
                minLatency.updateAndGet(min -> Math.min(min, opLatency / 1_000_000));
                maxLatency.updateAndGet(max -> Math.max(max, opLatency / 1_000_000));
              }
            } catch (Exception e) {
              exceptions.incrementAndGet();
              e.printStackTrace();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    doneLatch.await(5, TimeUnit.MINUTES);
    long testDuration = System.nanoTime() - testStartTime;

    wal.close();
    executor.shutdown();

    // Calculate statistics
    long durationMs = testDuration / 1_000_000;
    double throughput = (double) totalEntries / (durationMs / 1000.0);
    double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
    long p50 = percentile(latencies, 50);
    long p95 = percentile(latencies, 95);
    long p99 = percentile(latencies, 99);

    // Print results
    System.out.println("\n--- Results ---");
    System.out.println(String.format("Total time: %d ms", durationMs));
    System.out.println(String.format("Throughput: %.0f entries/sec", throughput));
    System.out.println(String.format("Avg latency: %.2f ms", avgLatency));
    System.out.println(String.format("Min latency: %d ms", minLatency.get()));
    System.out.println(String.format("Max latency: %d ms", maxLatency.get()));
    System.out.println(String.format("P50 latency: %d ms", p50));
    System.out.println(String.format("P95 latency: %d ms", p95));
    System.out.println(String.format("P99 latency: %d ms", p99));
    System.out.println(String.format("Exceptions: %d", exceptions.get()));

    long fsyncs = (long) Math.ceil((double) totalEntries / batchSize);
    System.out.println(String.format("Estimated fsyncs: %d", fsyncs));
    System.out.println(String.format("Time per fsync: %.2f ms", (double) durationMs / fsyncs));
    // Verify recovery
    WriteAheadLog walRecovered = new WriteAheadLog(config);
    List<LogEntry> recovered = walRecovered.readFromDisk();
    walRecovered.close();

    assertEquals(totalEntries, recovered.size(), "Should recover all entries");
    assertEquals(0, exceptions.get(), "Should have no exceptions");

    System.out.println("✅ Load test passed");
  }

  // ============ Batch Size Comparison ============
  private void runBatchSizeComparison(int numThreads, int entriesPerThread)
      throws IOException, InterruptedException {
    int[] batchSizes = {1, 5, 10, 25, 50, 100};

    System.out.println(
        String.format(
            "Comparing batch sizes with %d threads, %d entries each",
            numThreads, entriesPerThread));
    System.out.println("\nBatch Size | Throughput (entries/sec) | Avg Latency (ms)");
    System.out.println("-----------|--------------------------|------------------");

    for (int batchSize : batchSizes) {
      WalConfiguration config =
          new WalConfiguration.Builder()
              .batchSize(batchSize)
              .logDir(logPath + "-batch-" + batchSize)
              .build();
      WriteAheadLog wal = new WriteAheadLog(config);

      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(numThreads);

      AtomicLong totalLatency = new AtomicLong(0);
      AtomicLong opCount = new AtomicLong(0);

      ExecutorService executor = Executors.newFixedThreadPool(numThreads);

      long testStart = System.nanoTime();

      for (int threadId = 0; threadId < numThreads; threadId++) {
        final int tid = threadId;
        executor.submit(
            () -> {
              try {
                startLatch.await();
                for (int i = 0; i < entriesPerThread; i++) {
                  String data = String.format("T%d-E%d", tid, i);
                  long opStart = System.nanoTime();
                  wal.append(new LogEntry(data.getBytes().length, data.getBytes(), 1000L + i));
                  long opLatency = System.nanoTime() - opStart;

                  totalLatency.addAndGet(opLatency / 1_000_000);
                  opCount.incrementAndGet();
                }
              } catch (Exception e) {
                e.printStackTrace();
              } finally {
                doneLatch.countDown();
              }
            });
      }

      startLatch.countDown();
      doneLatch.await(5, TimeUnit.MINUTES);
      long testDuration = System.nanoTime() - testStart;

      wal.close();
      executor.shutdown();

      int totalEntries = numThreads * entriesPerThread;
      double durationSec = testDuration / 1_000_000_000.0;
      double throughput = totalEntries / durationSec;
      double avgLatency = (double) totalLatency.get() / opCount.get();

      System.out.println(
          String.format("%-10d | %-24.0f | %.2f", batchSize, throughput, avgLatency));
    }
  }

  // ============ Entry Size Comparison ============
  private void compareEntrySizes(int numThreads, int entriesPerThread)
      throws IOException, InterruptedException {
    int[] entrySizes = {64, 256, 1024, 4096, 16384, 65536};

    System.out.println(
        String.format(
            "Comparing entry sizes with %d threads, %d entries each (batch=10)",
            numThreads, entriesPerThread));
    System.out.println(
        "\nEntry Size | Throughput (entries/sec) | Avg Latency (ms) | Throughput (MB/sec)");
    System.out.println(
        "-----------|--------------------------|------------------|--------------------");

    for (int entrySize : entrySizes) {
      WalConfiguration config =
          new WalConfiguration.Builder()
              .batchSize(10)
              .logDir(logPath + "-size-" + entrySize)
              .build();
      WriteAheadLog wal = new WriteAheadLog(config);

      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(numThreads);

      AtomicLong totalLatency = new AtomicLong(0);
      AtomicLong opCount = new AtomicLong(0);
      AtomicLong bytesWritten = new AtomicLong(0);

      ExecutorService executor = Executors.newFixedThreadPool(numThreads);

      long testStart = System.nanoTime();

      for (int threadId = 0; threadId < numThreads; threadId++) {
        final int tid = threadId;
        executor.submit(
            () -> {
              try {
                startLatch.await();
                byte[] data = new byte[entrySize];
                for (int i = 0; i < entriesPerThread; i++) {
                  Arrays.fill(data, (byte) tid);
                  long opStart = System.nanoTime();
                  wal.append(new LogEntry(data.length, data.clone(), 1000L + i));
                  long opLatency = System.nanoTime() - opStart;

                  totalLatency.addAndGet(opLatency / 1_000_000);
                  opCount.incrementAndGet();
                  bytesWritten.addAndGet(entrySize);
                }
              } catch (Exception e) {
                e.printStackTrace();
              } finally {
                doneLatch.countDown();
              }
            });
      }

      startLatch.countDown();
      doneLatch.await(5, TimeUnit.MINUTES);
      long testDuration = System.nanoTime() - testStart;

      wal.close();
      executor.shutdown();

      int totalEntries = numThreads * entriesPerThread;
      double durationSec = testDuration / 1_000_000_000.0;
      double throughputEntries = totalEntries / durationSec;
      double throughputMB = (bytesWritten.get() / (1024.0 * 1024.0)) / durationSec;
      double avgLatency = (double) totalLatency.get() / opCount.get();

      System.out.println(
          String.format(
              "%-10d | %-24.0f | %-16.2f | %.2f",
              entrySize, throughputEntries, avgLatency, throughputMB));
    }
  }

  // ============ Write/Read Mix Test ============
  private void runWriteReadMixTest(int numWriters, int entriesPerWriter, int numReaders)
      throws IOException, InterruptedException {
    System.out.println(
        String.format(
            "Write/Read mix: %d writers, %d readers, %d entries per writer",
            numWriters, numReaders, entriesPerWriter));

    WalConfiguration config = new WalConfiguration.Builder().batchSize(10).logDir(logPath).build();
    WriteAheadLog wal = new WriteAheadLog(config);

    int totalEntries = numWriters * entriesPerWriter;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch writersDone = new CountDownLatch(numWriters);
    CountDownLatch readersDone = new CountDownLatch(numReaders);

    AtomicLong writeLatencyTotal = new AtomicLong(0);
    AtomicLong readLatencyTotal = new AtomicLong(0);
    AtomicLong writeOps = new AtomicLong(0);
    AtomicLong readOps = new AtomicLong(0);

    ExecutorService executor = Executors.newFixedThreadPool(numWriters + numReaders);

    long testStart = System.nanoTime();

    // Writers
    for (int threadId = 0; threadId < numWriters; threadId++) {
      final int tid = threadId;
      executor.submit(
          () -> {
            try {
              startLatch.await();
              for (int i = 0; i < entriesPerWriter; i++) {
                String data = String.format("T%d-E%d", tid, i);
                long opStart = System.nanoTime();
                wal.append(new LogEntry(data.getBytes().length, data.getBytes(), 1000L + i));
                writeLatencyTotal.addAndGet((System.nanoTime() - opStart) / 1_000_000);
                writeOps.incrementAndGet();
              }
            } catch (Exception e) {
              e.printStackTrace();
            } finally {
              writersDone.countDown();
            }
          });
    }

    // Readers
    for (int threadId = 0; threadId < numReaders; threadId++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              while (writersDone.getCount() > 0) {
                long opStart = System.nanoTime();
                List<LogEntry> entries = wal.readFromDisk();
                if (!entries.isEmpty()) {
                  readLatencyTotal.addAndGet((System.nanoTime() - opStart) / 1_000_000);
                  readOps.incrementAndGet();
                }
                Thread.sleep(5);
              }
            } catch (InterruptedException e) {
              // Expected
            } catch (IOException e) {
              throw new RuntimeException(e);
            } finally {
              readersDone.countDown();
            }
          });
    }

    startLatch.countDown();
    writersDone.await(5, TimeUnit.MINUTES);
    readersDone.await(1, TimeUnit.MINUTES);
    long testDuration = System.nanoTime() - testStart;

    wal.close();
    executor.shutdown();

    double durationSec = testDuration / 1_000_000_000.0;
    double writeOpsPerSec = writeOps.get() / durationSec;
    double readOpsPerSec = readOps.get() / durationSec;
    double avgWriteLatency =
        writeOps.get() > 0 ? (double) writeLatencyTotal.get() / writeOps.get() : 0;
    double avgReadLatency = readOps.get() > 0 ? (double) readLatencyTotal.get() / readOps.get() : 0;

    System.out.println("\n--- Results ---");
    System.out.println(String.format("Total time: %.2f sec", durationSec));
    System.out.println(String.format("Write ops/sec: %.0f", writeOpsPerSec));
    System.out.println(String.format("Read ops/sec: %.0f", readOpsPerSec));
    System.out.println(String.format("Avg write latency: %.2f ms", avgWriteLatency));
    System.out.println(String.format("Avg read latency: %.2f ms", avgReadLatency));
    System.out.println(String.format("Total writes: %d", writeOps.get()));
    System.out.println(String.format("Total reads: %d", readOps.get()));
    System.out.println("✅ Write/Read mix test passed");
  }

  // ============ Utility Methods ============
  private long percentile(List<Long> sortedValues, int percentile) {
    if (sortedValues.isEmpty()) return 0;
    List<Long> sorted = new ArrayList<>(sortedValues);
    Collections.sort(sorted);
    int index = (int) ((percentile / 100.0) * sorted.size());
    return sorted.get(Math.min(index, sorted.size() - 1));
  }
}
