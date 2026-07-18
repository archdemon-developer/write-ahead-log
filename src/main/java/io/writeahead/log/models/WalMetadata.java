package io.writeahead.log.models;

import java.util.List;

public record WalMetadata(String lastActiveSegment, List<SegmentMetadata> segments) {}
