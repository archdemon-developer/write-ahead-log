package io.writeahead.log.segments.filter.reads;

import io.writeahead.log.enums.strategies.ReadFilterType;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.models.results.FilterResult;

public final class AfterTimestampFilter implements ReadFilter {

  private final long threshold;

  public AfterTimestampFilter(long threshold) {
    this.threshold = threshold;
  }

  @Override
  public FilterResult matches(LogEntry entry) {
    if (entry.timestamp() >= threshold) {
      return FilterResult.accepted(entry, name().toString());
    }
    return FilterResult.rejected(entry, name().toString());
  }

  @Override
  public boolean canSkipSegment(SegmentMetadata segment) {
    return segment.maxTimestamp() < threshold;
  }

  @Override
  public ReadFilterType name() {
    return ReadFilterType.AFTER_TIMESTAMP;
  }
}
