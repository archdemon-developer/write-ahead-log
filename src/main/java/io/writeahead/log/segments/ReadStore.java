package io.writeahead.log.segments;

import io.writeahead.log.models.LogEntry;
import java.io.IOException;
import java.util.List;

public interface ReadStore {
  List<LogEntry> readAllSegments() throws IOException;

  List<LogEntry> readAllMatching(ReadFilter filter) throws IOException;
}
