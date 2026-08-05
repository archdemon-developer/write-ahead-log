package io.writeahead.log.fsync.retryers;

import io.writeahead.log.fsync.FsyncOperation;
import java.io.IOException;

public interface FsyncRetryStrategy {
  public void executeWithRetry(FsyncOperation operation) throws IOException;
}
