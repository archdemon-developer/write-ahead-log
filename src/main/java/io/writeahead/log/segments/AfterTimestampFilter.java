package io.writeahead.log.segments;

import io.writeahead.log.enums.ReadFilterType;
import io.writeahead.log.models.FilterResult;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.SegmentMetadata;

public class AfterTimestampFilter implements ReadFilter {

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
