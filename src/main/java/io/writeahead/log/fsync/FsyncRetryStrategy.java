package io.writeahead.log.fsync;

import java.io.IOException;

public interface FsyncRetryStrategy {
  public void executeWithRetry(FsyncOperation operation) throws IOException;
}
