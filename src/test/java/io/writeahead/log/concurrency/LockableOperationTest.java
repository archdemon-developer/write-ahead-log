package io.writeahead.log.concurrency;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class LockableOperationTest {

  private static final int SINGLE_THREAD = 1;
  private static final int THREE_READERS = 3;
  private static final int FIVE_THREADS = 5;
  private static final int TEN_CONCURRENT_WRITERS = 10;
  private static final int FIFTY_OPERATIONS_PER_WRITER = 50;
  private static final int FIVE_READER_THREADS = 5;
  private static final int THREE_WRITER_THREADS = 3;
  private static final int HUNDRED_OPERATIONS_PER_THREAD = 100;
  private static final long LOCK_WAIT_TIMEOUT_MS = 5000L;
  private static final long OPERATION_HOLD_DURATION_MS = 100L;
  private static final int SINGLE_EXECUTION = 1;

  @Test
  void executeOperationWithWriteLockSucceedsOnSingleThread() throws Exception {
    TestableOperation operationUnderTest = new TestableOperation();

    operationUnderTest.executeWithWriteLock();

    assertEquals(SINGLE_EXECUTION, operationUnderTest.executionCount());
  }

  @Test
  void executeOperationWithReadLockAllowsConcurrentReaders() throws Exception {
    TestableOperation operationUnderTest = new TestableOperation();
    CountDownLatch startSignal = new CountDownLatch(1);
    CountDownLatch completionSignal = new CountDownLatch(THREE_READERS);

    for (int readerIndex = 0; readerIndex < THREE_READERS; readerIndex++) {
      new Thread(
              () -> {
                try {
                  startSignal.await();
                  operationUnderTest.executeWithReadLock();
                  completionSignal.countDown();
                } catch (Exception ignored) {
                }
              })
          .start();
    }

    startSignal.countDown();
    boolean completedInTime = completionSignal.await(LOCK_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

    assertTrue(completedInTime);
    assertEquals(THREE_READERS, operationUnderTest.executionCount());
  }

  @Test
  void executeOperationWithWriteLockBlocksConcurrentAccessors() throws Exception {
    TestableOperation operationUnderTest = new TestableOperation();
    CountDownLatch writerHoldsLock = new CountDownLatch(1);
    CountDownLatch writerCanReleaseLock = new CountDownLatch(1);
    CountDownLatch allReaderThreadsCompleted = new CountDownLatch(FIVE_THREADS - 1);

    new Thread(
            () -> {
              try {
                operationUnderTest.executeWithWriteLockAndHold(
                    OPERATION_HOLD_DURATION_MS, writerHoldsLock, writerCanReleaseLock);
              } catch (Exception ignored) {
              }
            })
        .start();

    writerHoldsLock.await();

    for (int threadIndex = 0; threadIndex < FIVE_THREADS - 1; threadIndex++) {
      new Thread(
              () -> {
                try {
                  operationUnderTest.executeWithReadLock();
                  allReaderThreadsCompleted.countDown();
                } catch (Exception ignored) {
                }
              })
          .start();
    }

    Thread.sleep(50);
    writerCanReleaseLock.countDown();

    boolean completedInTime =
        allReaderThreadsCompleted.await(LOCK_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    assertTrue(completedInTime);
  }

  @Test
  void stressTestHighConcurrencyWriteOperations() throws Exception {
    TestableOperation operationUnderTest = new TestableOperation();
    CountDownLatch completionLatch = new CountDownLatch(TEN_CONCURRENT_WRITERS);

    for (int threadIndex = 0; threadIndex < TEN_CONCURRENT_WRITERS; threadIndex++) {
      new Thread(
              () -> {
                try {
                  for (int opIndex = 0; opIndex < FIFTY_OPERATIONS_PER_WRITER; opIndex++) {
                    operationUnderTest.executeWithWriteLock();
                  }
                } catch (Exception ignored) {
                } finally {
                  completionLatch.countDown();
                }
              })
          .start();
    }

    boolean completedInTime = completionLatch.await(LOCK_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    assertTrue(completedInTime);
    int expectedTotalOperations = TEN_CONCURRENT_WRITERS * FIFTY_OPERATIONS_PER_WRITER;
    assertEquals(expectedTotalOperations, operationUnderTest.executionCount());
  }

  @Test
  void stressTestMixedReadWriteOperations() throws Exception {
    TestableOperation operationUnderTest = new TestableOperation();
    CountDownLatch completionLatch = new CountDownLatch(FIVE_READER_THREADS + THREE_WRITER_THREADS);

    for (int readerIndex = 0; readerIndex < FIVE_READER_THREADS; readerIndex++) {
      new Thread(
              () -> {
                try {
                  for (int opIndex = 0; opIndex < HUNDRED_OPERATIONS_PER_THREAD; opIndex++) {
                    operationUnderTest.executeWithReadLock();
                  }
                } catch (Exception ignored) {
                } finally {
                  completionLatch.countDown();
                }
              })
          .start();
    }

    for (int writerIndex = 0; writerIndex < THREE_WRITER_THREADS; writerIndex++) {
      new Thread(
              () -> {
                try {
                  for (int opIndex = 0; opIndex < HUNDRED_OPERATIONS_PER_THREAD; opIndex++) {
                    operationUnderTest.executeWithWriteLock();
                  }
                } catch (Exception ignored) {
                } finally {
                  completionLatch.countDown();
                }
              })
          .start();
    }

    boolean completedInTime = completionLatch.await(LOCK_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    assertTrue(completedInTime);
    int expectedTotalOperations =
        (FIVE_READER_THREADS + THREE_WRITER_THREADS) * HUNDRED_OPERATIONS_PER_THREAD;
    assertEquals(expectedTotalOperations, operationUnderTest.executionCount());
  }

  static class TestableOperation {
    private final AtomicInteger totalExecutionCount = new AtomicInteger(0);
    private final LockableOperation lockableDelegate = new LockableOperation();

    public void executeWithReadLock() throws Exception {
      lockableDelegate.executeWithReadLock(
          () -> {
            totalExecutionCount.incrementAndGet();
            return null;
          });
    }

    public void executeWithWriteLock() throws Exception {
      lockableDelegate.executeWithWriteLock(
          () -> {
            totalExecutionCount.incrementAndGet();
            return null;
          });
    }

    public void executeWithWriteLockAndHold(
        long holdDurationMs, CountDownLatch lockAcquired, CountDownLatch lockCanBeReleased)
        throws Exception {
      lockableDelegate.executeWithWriteLock(
          () -> {
            lockAcquired.countDown();
            try {
              lockCanBeReleased.await();
            } catch (InterruptedException ignored) {
            }
            totalExecutionCount.incrementAndGet();
            return null;
          });
    }

    public int executionCount() {
      return totalExecutionCount.get();
    }
  }
}
