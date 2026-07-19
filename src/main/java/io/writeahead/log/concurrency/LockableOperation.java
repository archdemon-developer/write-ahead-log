package io.writeahead.log.concurrency;

import java.io.IOException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LockableOperation {
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  public <T> T executeWithReadLock(LockAction<T> action) throws IOException {
    lock.readLock().lock();
    try {
      return action.execute();
    } finally {
      lock.readLock().unlock();
    }
  }

  public <T> T executeWithWriteLock(LockAction<T> action) throws IOException {
    lock.writeLock().lock();
    try {
      return action.execute();
    } finally {
      lock.writeLock().unlock();
    }
  }
}
