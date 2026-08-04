package io.writeahead.log.segments;

import io.writeahead.log.models.TruncateResult;
import java.io.IOException;

public interface AdminStore {
  TruncateResult truncateAllMatching(TruncateFilter truncateFilter) throws IOException;
}
