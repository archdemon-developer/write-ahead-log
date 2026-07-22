package io.writeahead.log.fsync.executor;

import io.writeahead.log.fsync.FsyncExecutor;
import io.writeahead.log.fsync.FsyncRetryStrategy;
import io.writeahead.log.models.file.FileStream;
import io.writeahead.log.utils.FileUtils;
import java.io.IOException;

public class EveryEntryFsyncExecutor implements FsyncExecutor {
  private final FsyncRetryStrategy retryStrategy;
  private final FileStream fileStream;

  public EveryEntryFsyncExecutor(FsyncRetryStrategy fsyncRetryStrategy, FileStream fileStream) {
    this.fileStream = fileStream;
    this.retryStrategy = fsyncRetryStrategy;
  }

  @Override
  public void onEntryWritten() throws IOException {
    retryStrategy.executeWithRetry(() -> FileUtils.fsyncStream(fileStream));
  }
}
