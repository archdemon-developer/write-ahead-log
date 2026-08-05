package io.writeahead.log.segments.stores;

import io.writeahead.log.models.states.BatchState;
import io.writeahead.log.models.states.SegmentState;
import io.writeahead.log.models.states.WalSnapshot;
import java.io.IOException;

public interface ObservableStore {
  WalSnapshot getSnapshot() throws IOException;

  SegmentState getSegmentState(long sequenceNumber) throws IOException;

  BatchState getBatchState();
}
