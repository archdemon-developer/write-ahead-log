package io.writeahead.log.integration;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.models.LogEntry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class ConcurrentWriteReadTest {

    @TempDir Path tempDir;
    private String logPath;

    @BeforeEach
    void setUp() {
        logPath = tempDir.toString();
    }

    @Test
    void testConcurrentWritesAndReadsNoDataLoss()
            throws IOException, InterruptedException {
        WriteAheadLog wal = new WriteAheadLog(10, logPath);

        int numWriterThreads = 7;
        int entriesPerWriter = 100;
        int numReaderThreads = 3;
        int totalExpectedEntries = numWriterThreads * entriesPerWriter;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch writersDoneLatch = new CountDownLatch(numWriterThreads);
        CountDownLatch readersDoneLatch = new CountDownLatch(numReaderThreads);

        AtomicInteger writerExceptions = new AtomicInteger(0);
        AtomicInteger readerExceptions = new AtomicInteger(0);
        AtomicInteger successfulReads = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(
                numWriterThreads + numReaderThreads);

        // Spawn writer threads
        for (int threadId = 0; threadId < numWriterThreads; threadId++) {
            final int tid = threadId;
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            for (int i = 0; i < entriesPerWriter; i++) {
                                String data = String.format("T%d-E%d", tid, i);
                                wal.append(
                                        new LogEntry(data.getBytes().length, data.getBytes(), 1000L + i));
                            }
                        } catch (Exception e) {
                            writerExceptions.incrementAndGet();
                            e.printStackTrace();
                        } finally {
                            writersDoneLatch.countDown();
                        }
                    });
        }

        // Spawn reader threads (read while writers write)
        for (int threadId = 0; threadId < numReaderThreads; threadId++) {
            final int tid = threadId;
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            while (writersDoneLatch.getCount() > 0) {
                                try {
                                    List<LogEntry> entries = wal.readFromDisk();
                                    if (!entries.isEmpty()) {
                                        successfulReads.incrementAndGet();
                                    }
                                } catch (Exception e) {
                                    // Readers might see inconsistent state temporarily, that's OK
                                }
                                Thread.sleep(10);
                            }
                        } catch (InterruptedException e) {
                            readerExceptions.incrementAndGet();
                        } finally {
                            readersDoneLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();
        writersDoneLatch.await(30, TimeUnit.SECONDS);
        readersDoneLatch.await(5, TimeUnit.SECONDS);

        wal.close();
        executor.shutdown();

        assertEquals(
                0,
                writerExceptions.get(),
                "Writers should not throw exceptions");
        assertEquals(
                0,
                readerExceptions.get(),
                "Readers should not throw InterruptedException");
        assertTrue(
                successfulReads.get() > 0,
                "Readers should have successfully read data while writers were writing");

        // Verify recovery
        WriteAheadLog walRecovered = new WriteAheadLog(10, logPath);
        List<LogEntry> recovered = walRecovered.readFromDisk();
        walRecovered.close();

        assertEquals(
                totalExpectedEntries,
                recovered.size(),
                String.format("Should recover all %d entries", totalExpectedEntries));

        // Verify no duplicates
        Set<String> seenEntries = new HashSet<>();
        for (LogEntry entry : recovered) {
            String entryData = new String(entry.data());
            assertFalse(
                    seenEntries.contains(entryData),
                    "Duplicate entry found: " + entryData);
            seenEntries.add(entryData);
        }

        // Verify entries from each thread are in order
        Map<Integer, Integer> threadLastIndex = new HashMap<>();
        for (LogEntry entry : recovered) {
            String entryData = new String(entry.data());
            String[] parts = entryData.split("-");
            int threadId = Integer.parseInt(parts[0].substring(1));
            int entryIndex = Integer.parseInt(parts[1].substring(1));

            int lastIndex = threadLastIndex.getOrDefault(threadId, -1);
            assertTrue(
                    entryIndex > lastIndex,
                    String.format(
                            "Entries from thread %d are not in order (last: %d, current: %d)",
                            threadId, lastIndex, entryIndex));
            threadLastIndex.put(threadId, entryIndex);
        }
    }

    @Test
    void testHighThroughputConcurrentWrites()
            throws IOException, InterruptedException {
        WriteAheadLog wal = new WriteAheadLog(50, logPath);

        int numThreads = 10;
        int entriesPerThread = 200;
        int totalExpectedEntries = numThreads * entriesPerThread;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger exceptions = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        long startTime = System.currentTimeMillis();

        for (int threadId = 0; threadId < numThreads; threadId++) {
            final int tid = threadId;
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            for (int i = 0; i < entriesPerThread; i++) {
                                String data = String.format("T%d-E%d", tid, i);
                                wal.append(
                                        new LogEntry(data.getBytes().length, data.getBytes(), 1000L + i));
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
        doneLatch.await(60, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        wal.close();
        executor.shutdown();

        assertEquals(0, exceptions.get(), "No exceptions should occur");

        // Verify recovery
        WriteAheadLog walRecovered = new WriteAheadLog(50, logPath);
        List<LogEntry> recovered = walRecovered.readFromDisk();
        walRecovered.close();

        assertEquals(
                totalExpectedEntries,
                recovered.size(),
                String.format("Should recover all %d entries", totalExpectedEntries));

        double throughput = (double) totalExpectedEntries / (duration / 1000.0);
        System.out.println(
                String.format(
                        "Concurrent write throughput: %.0f entries/sec (%d entries in %dms)",
                        throughput, totalExpectedEntries, duration));
    }

    @Test
    void testStressTestWithSegmentRotation()
            throws IOException, InterruptedException {
        WriteAheadLog wal = new WriteAheadLog(20, logPath);

        int numThreads = 5;
        int largeEntriesPerThread = 50;
        int totalExpectedEntries = numThreads * largeEntriesPerThread;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger exceptions = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int threadId = 0; threadId < numThreads; threadId++) {
            final int tid = threadId;
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            for (int i = 0; i < largeEntriesPerThread; i++) {
                                byte[] largeData = new byte[1024 * 100];
                                Arrays.fill(largeData, (byte) tid);
                                String header = String.format("T%d-E%d-", tid, i);
                                System.arraycopy(header.getBytes(), 0, largeData, 0,
                                        Math.min(header.getBytes().length, largeData.length));

                                wal.append(new LogEntry(largeData.length, largeData, 1000L + i));
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
        doneLatch.await(120, TimeUnit.SECONDS);

        wal.close();
        executor.shutdown();

        assertEquals(0, exceptions.get(), "No exceptions should occur");

        // Verify recovery
        WriteAheadLog walRecovered = new WriteAheadLog(20, logPath);
        List<LogEntry> recovered = walRecovered.readFromDisk();
        walRecovered.close();

        assertEquals(
                totalExpectedEntries,
                recovered.size(),
                String.format("Should recover all %d entries even with segment rotation",
                        totalExpectedEntries));

        // Verify no duplicates
        Set<String> seenHeaders = new HashSet<>();
        for (LogEntry entry : recovered) {
            String header = new String(entry.data(), 0, 10);
            assertFalse(
                    seenHeaders.contains(header),
                    "Duplicate entry detected");
            seenHeaders.add(header);
        }
    }
}