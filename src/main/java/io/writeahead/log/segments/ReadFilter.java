package io.writeahead.log.segments;

import io.writeahead.log.enums.ReadFilterType;
import io.writeahead.log.models.FilterResult;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.SegmentMetadata;

public interface ReadFilter {
  FilterResult matches(LogEntry entry);

  ReadFilterType name();

  default boolean canSkipSegment(SegmentMetadata segment) {
    return false;
  }
}
