package io.writeahead.log.fsync;

import io.writeahead.log.enums.FsyncStrategy;
import io.writeahead.log.models.FileStream;

public class FsyncExecutorFactory {

  public static FsyncExecutor create(
      FsyncStrategy fsyncStrategy, FsyncRetryStrategy retryStrategy, FileStream fileStream) {
    return switch (fsyncStrategy) {
      case FsyncStrategy.FSYNC_EVERY_BATCH ->
          new EveryBatchFsyncExecutor(retryStrategy, fileStream);
      case FsyncStrategy.FSYNC_EVERY_ENTRY ->
          new EveryEntryFsyncExecutor(retryStrategy, fileStream);
    };
  }
}
