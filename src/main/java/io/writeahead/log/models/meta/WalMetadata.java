package io.writeahead.log.models.meta;

import java.util.List;

public record WalMetadata(
    String lastActiveSegment, List<SegmentMetadata> segments, long nextSequence) {}
