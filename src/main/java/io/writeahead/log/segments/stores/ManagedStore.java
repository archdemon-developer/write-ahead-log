package io.writeahead.log.segments.stores;

import io.writeahead.log.models.results.CloseResult;
import java.io.IOException;

public interface ManagedStore {
  CloseResult close() throws IOException;

  boolean isOpen();
}
