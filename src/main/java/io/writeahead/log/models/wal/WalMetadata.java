package io.writeahead.log.models.wal;

import io.writeahead.log.segments.SegmentMetadata;

import java.util.List;

public record WalMetadata(
        String lastActiveSegment,
        List<SegmentMetadata> segments,
        long nextSequence) {}