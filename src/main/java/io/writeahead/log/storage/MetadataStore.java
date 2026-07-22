package io.writeahead.log.storage;

import io.writeahead.log.models.wal.WalMetadata;
import java.io.IOException;

public interface MetadataStore {
  WalMetadata read() throws IOException;

  void write(WalMetadata metadata) throws IOException;
}
