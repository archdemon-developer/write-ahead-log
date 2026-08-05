package io.writeahead.log.segments.stores;

import io.writeahead.log.models.results.TruncateResult;
import io.writeahead.log.segments.filter.truncate.TruncateFilter;
import java.io.IOException;

public interface AdminStore {
  TruncateResult truncateAllMatching(TruncateFilter truncateFilter) throws IOException;
}
