package io.writeahead.log.segments.stores;

public interface SegmentStore
    extends WriteStore, ReadStore, AdminStore, ObservableStore, MetricStore, ManagedStore {}
