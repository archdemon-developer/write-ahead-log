package io.writeahead.log.segments.filter.truncate;

import io.writeahead.log.models.meta.SegmentMetadata;

public interface TruncateFilter {
  boolean shouldDelete(SegmentMetadata segment);

  String name();
}
