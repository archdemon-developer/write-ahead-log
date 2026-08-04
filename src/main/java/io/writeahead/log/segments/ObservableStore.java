package io.writeahead.log.segments;

import io.writeahead.log.models.BatchState;
import io.writeahead.log.models.SegmentState;
import io.writeahead.log.models.WalSnapshot;
import java.io.IOException;

public interface ObservableStore {
  WalSnapshot getSnapshot() throws IOException;

  SegmentState getSegmentState(long sequenceNumber) throws IOException;

  BatchState getBatchState();
}
