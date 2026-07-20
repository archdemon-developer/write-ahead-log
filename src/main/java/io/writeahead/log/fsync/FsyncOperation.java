package io.writeahead.log.fsync;

import java.io.IOException;

@FunctionalInterface
public interface FsyncOperation {
  void fsync() throws IOException;
}
