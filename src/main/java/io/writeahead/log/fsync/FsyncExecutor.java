package io.writeahead.log.fsync;

import java.io.IOException;

public interface FsyncExecutor {
  default void onEntryWritten() throws IOException {}
  ;

  default void onBatchComplete() throws IOException {}
  ;
}
