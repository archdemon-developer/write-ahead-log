package io.writeahead.log.segments;

import io.writeahead.log.models.SegmentMetadata;

public interface TruncateFilter {
  boolean shouldDelete(SegmentMetadata segment);

  String name();
}
