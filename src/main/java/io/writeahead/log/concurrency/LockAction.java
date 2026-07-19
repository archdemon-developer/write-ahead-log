package io.writeahead.log.concurrency;

import java.io.IOException;

@FunctionalInterface
public interface LockAction<T> {
  T execute() throws IOException;
}
