package io.writeahead.log.segments;

import io.writeahead.log.models.CloseResult;
import java.io.IOException;

public interface ManagedStore {
  CloseResult close() throws IOException;

  boolean isOpen();
}
