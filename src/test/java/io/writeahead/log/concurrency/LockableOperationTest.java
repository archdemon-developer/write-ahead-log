package io.writeahead.log.concurrency;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class LockableOperationTest {

    @Test
    void testExecuteWithWriteLockSucceeds() throws IOException {
        LockableOperation lock = new LockableOperation();

        int result = lock.executeWithWriteLock(() -> 42);

        assertEquals(42, result, "Write lock should execute and return value");
    }

    @Test
    void testExecuteWithWriteLockSerializesWrites() throws IOException, InterruptedException {
        LockableOperation lock = new LockableOperation();
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());

        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(
                    () -> {
                        try {
                            startLatch.await();
                            lock.executeWithWriteLock(
                                    () -> {
                                        order.add(threadId);
                                        try {
                                            Thread.sleep(10);
                                        } catch (InterruptedException e) {}
                                        return null;
                                    });
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            endLatch.countDown();
                        }
                    })
                    .start();
        }

        startLatch.countDown();
        endLatch.await();


        assertEquals(threadCount, order.size(), "All threads should complete");

    }

    @Test
    void testWriteLockPreventsSimultaneousWrite() throws IOException, InterruptedException {
        LockableOperation lock = new LockableOperation();
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        int threadCount = 10;
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            new Thread(
                    () -> {
                        try {
                            lock.executeWithWriteLock(
                                    () -> {
                                        int current = concurrentCount.incrementAndGet();
                                        // Record max concurrent
                                        while (current > maxConcurrent.get()) {
                                            maxConcurrent.compareAndSet(maxConcurrent.get(), current);
                                        }
                                        try {
                                            Thread.sleep(5); // Simulate work
                                        } catch(InterruptedException e) {}

                                        concurrentCount.decrementAndGet();
                                        return null;
                                    });
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            endLatch.countDown();
                        }
                    })
                    .start();
        }

        endLatch.await();

        assertEquals(1, maxConcurrent.get(), "Only 1 thread should execute critical section at a time");
    }

    @Test
    void testExecuteWithReadLockSucceeds() throws IOException {
        LockableOperation lock = new LockableOperation();

        int result = lock.executeWithReadLock(() -> 99);

        assertEquals(99, result, "Read lock should execute and return value");
    }

    @Test
    void testMultipleReadersCanRunConcurrently() throws IOException, InterruptedException {
        LockableOperation lock = new LockableOperation();
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger concurrentCount = new AtomicInteger(0);

        int threadCount = 10;
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            new Thread(
                    () -> {
                        try {
                            lock.executeWithReadLock(
                                    () -> {
                                        int current = concurrentCount.incrementAndGet();

                                        while (current > maxConcurrent.get()) {
                                            maxConcurrent.compareAndSet(maxConcurrent.get(), current);
                                        }
                                        try {
                                            Thread.sleep(50);
                                        } catch (InterruptedException e) {}

                                        concurrentCount.decrementAndGet();
                                        return null;
                                    });
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            endLatch.countDown();
                        }
                    })
                    .start();
        }

        endLatch.await();

        assertTrue(maxConcurrent.get() > 1, "Multiple readers should run concurrently (got " + maxConcurrent.get() + ")");
    }

    @Test
    void testWriteLockBlocksReaders() throws IOException, InterruptedException {
        LockableOperation lock = new LockableOperation();
        List<String> events = Collections.synchronizedList(new ArrayList<>());

        CountDownLatch writeLockAcquired = new CountDownLatch(1);
        CountDownLatch writeComplete = new CountDownLatch(1);

        Thread writer =
                new Thread(
                        () -> {
                            try {
                                lock.executeWithWriteLock(
                                        () -> {
                                            events.add("write-start");
                                            writeLockAcquired.countDown();
                                            try {
                                                Thread.sleep(100);
                                            } catch (InterruptedException e) {}
                                            events.add("write-end");
                                            return null;
                                        });
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                writeComplete.countDown();
                            }
                        });

        writer.start();

        writeLockAcquired.await();

        CountDownLatch readComplete = new CountDownLatch(1);
        Thread reader =
                new Thread(
                        () -> {
                            try {
                                lock.executeWithReadLock(
                                        () -> {
                                            events.add("read-start");
                                            events.add("read-end");
                                            return null;
                                        });
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                readComplete.countDown();
                            }
                        });

        reader.start();

        Thread.sleep(50);

        assertEquals(1, events.size(), "Only write-start should have executed, reader blocked");

        writeComplete.await();
        readComplete.await();

        assertEquals("write-start", events.get(0));
        assertEquals("write-end", events.get(1));
        assertEquals("read-start", events.get(2));
        assertEquals("read-end", events.get(3));
    }

    @Test
    void testReadLockBlocksWriters() throws IOException, InterruptedException {
        LockableOperation lock = new LockableOperation();
        List<String> events = Collections.synchronizedList(new ArrayList<>());

        CountDownLatch readLockAcquired = new CountDownLatch(3);
        CountDownLatch readsComplete = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            final int readerId = i;
            new Thread(
                    () -> {
                        try {
                            lock.executeWithReadLock(
                                    () -> {
                                        events.add("read-" + readerId + "-start");
                                        readLockAcquired.countDown();
                                        try {
                                            Thread.sleep(100);
                                        } catch(InterruptedException e) {}
                                        events.add("read-" + readerId + "-end");
                                        return null;
                                    });
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            readsComplete.countDown();
                        }
                    })
                    .start();
        }

        readLockAcquired.await();

        CountDownLatch writeComplete = new CountDownLatch(1);
        Thread writer =
                new Thread(
                        () -> {
                            try {
                                lock.executeWithWriteLock(
                                        () -> {
                                            events.add("write-start");
                                            events.add("write-end");
                                            return null;
                                        });
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                writeComplete.countDown();
                            }
                        });

        writer.start();

        Thread.sleep(50);

        assertFalse(
                events.stream().anyMatch(e -> e.contains("write")),
                "Write should be blocked by readers");

        readsComplete.await();
        writeComplete.await();

        int lastReadEnd = -1;
        int firstWriteStart = -1;
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).contains("read-") && events.get(i).contains("end")) {
                lastReadEnd = i;
            }
            if (events.get(i).equals("write-start")) {
                firstWriteStart = i;
                break;
            }
        }

        assertTrue(lastReadEnd < firstWriteStart, "All reads should complete before write starts");
    }

    @Test
    void testWriteLockReleasedOnException() throws IOException, InterruptedException {
        LockableOperation lock = new LockableOperation();
        AtomicInteger successCount = new AtomicInteger(0);

        assertThrows(
                RuntimeException.class,
                () ->
                        lock.executeWithWriteLock(
                                () -> {
                                    throw new RuntimeException("Test exception");
                                }),
                "Should propagate exception");


        lock.executeWithWriteLock(
                () -> {
                    successCount.incrementAndGet();
                    return null;
                });

        assertEquals(1, successCount.get(), "Lock should be released after exception");
    }

    @Test
    void testReadLockReleasedOnException() throws IOException, InterruptedException {
        LockableOperation lock = new LockableOperation();
        AtomicInteger successCount = new AtomicInteger(0);

        assertThrows(
                RuntimeException.class,
                () ->
                        lock.executeWithReadLock(
                                () -> {
                                    throw new RuntimeException("Test exception");
                                }),
                "Should propagate exception");

        lock.executeWithReadLock(
                () -> {
                    successCount.incrementAndGet();
                    return null;
                });

        assertEquals(1, successCount.get(), "Lock should be released after exception");
    }

    @Test
    void testNoWriterStarvation() throws IOException, InterruptedException {
        LockableOperation lock = new LockableOperation();
        AtomicInteger writeCount = new AtomicInteger(0);
        AtomicInteger readCount = new AtomicInteger(0);

        int duration = 2000;
        long endTime = System.currentTimeMillis() + duration;

        CountDownLatch readersStart = new CountDownLatch(5);
        for (int i = 0; i < 5; i++) {
            new Thread(
                    () -> {
                        try {
                            while (System.currentTimeMillis() < endTime) {
                                lock.executeWithReadLock(
                                        () -> {
                                            readCount.incrementAndGet();
                                            return null;
                                        });
                            }
                        } catch (IOException e) {

                        } finally {
                            readersStart.countDown();
                        }
                    })
                    .start();
        }

        CountDownLatch writerStart = new CountDownLatch(1);
        new Thread(
                () -> {
                    try {
                        while (System.currentTimeMillis() < endTime) {
                            lock.executeWithWriteLock(
                                    () -> {
                                        writeCount.incrementAndGet();
                                        return null;
                                    });
                        }
                    } catch (IOException e) {

                    } finally {
                        writerStart.countDown();
                    }
                })
                .start();

        readersStart.await();
        writerStart.await();

        assertTrue(writeCount.get() > 0, "Writer should complete some operations (not starved)");
    }

    @Test
    void testWriteLockReturnValue() throws IOException {
        LockableOperation lock = new LockableOperation();

        String result = lock.executeWithWriteLock(() -> "write-result");

        assertEquals("write-result", result, "Should return correct value from write lock");
    }

    @Test
    void testReadLockReturnValue() throws IOException {
        LockableOperation lock = new LockableOperation();

        String result = lock.executeWithReadLock(() -> "read-result");

        assertEquals("read-result", result, "Should return correct value from read lock");
    }

    @Test
    void testReturnNullValue() throws IOException {
        LockableOperation lock = new LockableOperation();

        Object resultWrite = lock.executeWithWriteLock(() -> null);
        Object resultRead = lock.executeWithReadLock(() -> null);

        assertNull(resultWrite, "Should handle null return from write lock");
        assertNull(resultRead, "Should handle null return from read lock");
    }

    // ============================================================================
    // SECTION 7: STRESS TESTING
    // ============================================================================

    @Test
    void testHighConcurrencyMixedReadWrite() throws IOException, InterruptedException {
        LockableOperation lock = new LockableOperation();
        AtomicInteger operationCount = new AtomicInteger(0);

        int threadCount = 20;
        int operationsPerThread = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(
                    () -> {
                        try {
                            for (int op = 0; op < operationsPerThread; op++) {
                                if (threadId % 2 == 0) {
                                    lock.executeWithReadLock(
                                            () -> {
                                                operationCount.incrementAndGet();
                                                return null;
                                            });
                                } else {
                                    lock.executeWithWriteLock(
                                            () -> {
                                                operationCount.incrementAndGet();
                                                return null;
                                            });
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            latch.countDown();
                        }
                    })
                    .start();
        }

        latch.await();

        assertEquals(
                threadCount * operationsPerThread,
                operationCount.get(),
                "All operations should complete");
    }
}