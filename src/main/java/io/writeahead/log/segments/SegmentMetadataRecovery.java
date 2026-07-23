package io.writeahead.log.segments;

import io.writeahead.log.constants.WalConstants;
import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.models.segment.SegmentFooter;
import io.writeahead.log.models.segment.SegmentHeader;
import io.writeahead.log.models.segment.SegmentMetadata;
import io.writeahead.log.models.wal.WalMetadata;
import io.writeahead.log.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import java.util.List;

public class SegmentMetadataRecovery {

    private static final Logger log = LoggerFactory.getLogger(SegmentMetadataRecovery.class);

    public WalMetadata recover(String logDir) throws IOException {
        List<SegmentMetadata> segments = new ArrayList<>();
        long maxSequenceFromHeaders = 0;

        File logDirectory = new File(logDir);

        if(!logDirectory.exists()){
            log.info("Log directory does not exist: {}", logDir);
            return new WalMetadata(null, segments, 1);
        }

        List<File> logFiles = FileUtils.listLogFiles(logDir);
        if(logFiles.isEmpty()) {
            log.info("No segment found in: {}", logDir);
            return new WalMetadata(null, segments, 1);
        }

        for(File logFile : logFiles) {
            try {
                SegmentMetadata segMeta = validateAndRecoverSegment(logFile);

                if(segMeta != null) {
                    segments.add(segMeta);
                    maxSequenceFromHeaders = Math.max(maxSequenceFromHeaders, segMeta.sequenceNumber());
                    log.debug("Recovered segment: {} (seq: {}, entries: {})",
                            logFile.getName(), segMeta.sequenceNumber(), segMeta.entryCount());
                }
            } catch (IOException ex) {
                log.error("Error recovering segment {}: {}", logFile.getName(), ex.getMessage());
            }
        }
        String lastActiveSegment = segments.isEmpty() ? null : segments.getLast().filename();
        long nextSequence = maxSequenceFromHeaders + 1;

        log.info("Recovery complete: {} valid segments, max sequence: {}, next sequence: {}",
                segments.size(), maxSequenceFromHeaders, nextSequence);

        return new WalMetadata(lastActiveSegment, segments, nextSequence);
    }

    private SegmentMetadata validateAndRecoverSegment(File segmentFile) throws IOException {
        long fileSize = FileUtils.getFileSize(segmentFile);

        if(fileSize < 84) {
            log.warn("Segment too small: {} ({} bytes), skipping", segmentFile.getName(), fileSize);
            return null;
        }

        byte[] headerBytes = FileUtils.readBytes(segmentFile, 0, WalConstants.SEGMENT_HEADER_SIZE);
        SegmentHeader header = SegmentHeader.fromBytes(headerBytes);

        if(!header.isValid()) {
            log.warn("Segment header is invalid (checksum mismatch): {}, skipping", segmentFile.getName());
            return null;
        }

        if(header.magic() != (byte) 0xAA) {
            log.warn("Segment header invalid (magic byte mismatch): {}, skipping", segmentFile.getName());
            return null;
        }

        byte[] footerBytes = FileUtils.readBytes(segmentFile, fileSize - WalConstants.SEGMENT_FOOTER_SIZE,
                WalConstants.SEGMENT_FOOTER_SIZE);
        SegmentFooter footer = SegmentFooter.fromBytes(footerBytes);

        if (!footer.isValid()) {
            log.warn("Segment footer invalid (checksum mismatch): {}, skipping", segmentFile.getName());
            return null;
        }

        return new SegmentMetadata(
                segmentFile.getName(),
                header.segmentSequence(),
                header.createdAt(),
                fileSize,
                footer.entryCount(),
                footer.minTimestamp(),
                footer.maxTimestamp());

    }

}
