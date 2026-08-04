package io.writeahead.log.segments;

public interface SegmentStore
    extends WriteStore, ReadStore, AdminStore, ObservableStore, MetricStore, ManagedStore {}
