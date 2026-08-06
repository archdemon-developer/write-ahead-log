package io.writeahead.log.segments.filter.truncate;

import io.writeahead.log.models.meta.SegmentMetadata;

public sealed interface TruncateFilter permits BeforeTimestampTruncateFilter {
  boolean shouldDelete(SegmentMetadata segment);

  String name();
}
