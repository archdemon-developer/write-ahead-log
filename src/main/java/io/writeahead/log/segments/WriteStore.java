package io.writeahead.log.segments;

import io.writeahead.log.models.AppendResult;
import io.writeahead.log.models.LogEntry;
import java.io.IOException;

public interface WriteStore {
  AppendResult append(LogEntry entry) throws IOException;

  AppendResult writeBatch() throws IOException;
}
