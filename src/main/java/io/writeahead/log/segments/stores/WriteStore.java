package io.writeahead.log.segments.stores;

import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.results.AppendResult;
import java.io.IOException;

public interface WriteStore {
  AppendResult append(LogEntry entry) throws IOException;

  AppendResult writeBatch() throws IOException;
}
