package io.writeahead.log.segments.orchestrators;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.models.results.TruncateResult;
import io.writeahead.log.models.results.TruncateSegmentsResult;
import io.writeahead.log.segments.filter.truncate.BeforeTimestampTruncateFilter;
import io.writeahead.log.segments.filter.truncate.TruncateFilter;
import io.writeahead.log.segments.operators.SegmentCollection;
import io.writeahead.log.utils.FileUtils;
import java.io.File;
import java.io.IOException;

public class SegmentTruncator {

  private static final Logger log = LoggerFactory.getLogger(SegmentTruncator.class);

  private final SegmentCollection segmentCollection;
  private final WalConfiguration config;

  public SegmentTruncator(SegmentCollection segmentCollection, WalConfiguration config) {
    this.segmentCollection = segmentCollection;
    this.config = config;
  }

  public TruncateResult truncateAllMatching(TruncateFilter filter) throws IOException {
    TruncateSegmentsResult segmentResult = segmentCollection.truncateMatching(filter);

    if (!segmentResult.wereSegmentsRemoved()) {
      return TruncateResult.nothingToTruncate(segmentResult.oldestRemainingSequence());
    }

    for (SegmentMetadata metadata : segmentResult.getSegmentsToDelete()) {
      File segmentFile = new File(config.logDir(), metadata.filename());
      try {
        FileUtils.deleteFile(segmentFile);
        log.info("Deleted segment file: {}", metadata.filename());
      } catch (IOException ex) {
        log.warn(
            "Failed to delete segment file {}, will be cleaned up on next recovery: {}",
            metadata.filename(),
            ex.getMessage());
      }
    }

    return TruncateResult.successfulTruncate(
        segmentResult.segmentsRemoved(), segmentResult.oldestRemainingSequence());
  }

  public TruncateResult truncateBeforeTimestamp(long timestamp) throws IOException {
    return truncateAllMatching(new BeforeTimestampTruncateFilter(timestamp));
  }
}
