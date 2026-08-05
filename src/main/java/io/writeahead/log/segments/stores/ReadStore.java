package io.writeahead.log.segments.stores;

import io.writeahead.log.models.LogEntry;
import io.writeahead.log.segments.filter.reads.ReadFilter;
import java.io.IOException;
import java.util.List;

public interface ReadStore {
  List<LogEntry> readAllSegments() throws IOException;

  List<LogEntry> readAllMatching(ReadFilter filter) throws IOException;
}
