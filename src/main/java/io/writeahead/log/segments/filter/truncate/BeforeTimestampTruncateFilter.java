package io.writeahead.log.segments.filter.truncate;

import io.writeahead.log.models.meta.SegmentMetadata;

public class BeforeTimestampTruncateFilter implements TruncateFilter {
  private final long threshold;

  public BeforeTimestampTruncateFilter(long threshold) {
    this.threshold = threshold;
  }

  @Override
  public boolean shouldDelete(SegmentMetadata segment) {
    return segment.maxTimestamp() <= threshold;
  }

  @Override
  public String name() {
    return "BEFORE_TIMESTAMP";
  }
}
