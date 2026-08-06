package io.writeahead.log.segments.filter.reads;

import io.writeahead.log.enums.strategies.ReadFilterType;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.models.results.FilterResult;

public sealed interface ReadFilter permits AfterTimestampFilter {
  FilterResult matches(LogEntry entry);

  ReadFilterType name();

  default boolean canSkipSegment(SegmentMetadata segment) {
    return false;
  }
}
