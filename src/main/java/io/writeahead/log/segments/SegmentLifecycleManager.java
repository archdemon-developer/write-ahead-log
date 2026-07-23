package io.writeahead.log.segments;

import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.models.file.FileStream;
import io.writeahead.log.models.segment.SegmentFooter;
import io.writeahead.log.models.segment.SegmentHeader;
import io.writeahead.log.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class SegmentLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(SegmentLifecycleManager.class);
    private final String logDir;

    public SegmentLifecycleManager(String logDir) throws IOException {
        File dir = new File(logDir);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("Log directory does not exist or is not a directory: " + logDir);
        }
        this.logDir = logDir;
    }

    public FileStream createNewSegment(long sequence) throws IOException {
        String filename = generateSegmentFilename(sequence);
        File segmentFile = new File(logDir, filename);

        try {
            SegmentHeader header = SegmentHeader.create(System.currentTimeMillis(), sequence);
            byte[] headerBytes = header.toBytes();
            Files.write(segmentFile.toPath(), headerBytes);

            log.info("Created new segment: {} (sequence: {})", filename, sequence);

            return FileUtils.openAppendStream(segmentFile);
        } catch (IOException e) {
            deleteFile(segmentFile, filename);
            throw e;
        }
    }

    public void finalizeSegment(FileStream currentStream, int entryCount,
                                long minTimestamp, long maxTimestamp) throws IOException {
        try {
            SegmentFooter footer = SegmentFooter.create(entryCount, minTimestamp, maxTimestamp);
            byte[] footerBytes = footer.toBytes();
            FileUtils.writeToStream(currentStream, footerBytes);
            FileUtils.fsyncStream(currentStream);
        } finally {
            FileUtils.closeStream(currentStream);  // ALWAYS closes
        }

        log.debug("Finalized segment: entries={}, minTs={}, maxTs={}",
                entryCount, minTimestamp, maxTimestamp);
    }

    public void closeSegment(FileStream currentStream, int entryCount,
                             long minTimestamp, long maxTimestamp) throws IOException {
        finalizeSegment(currentStream, entryCount, minTimestamp, maxTimestamp);
        log.info("Closed segment: entries={}", entryCount);
    }

    public static String generateSegmentFilename(long sequence) {
        long now = System.currentTimeMillis();
        return String.format("wal-%d-%06d.log", now, sequence);
    }

    private static void deleteFile(File segmentFile, String filename) {
        try {
            FileUtils.deleteFile(segmentFile);
            log.warn("Cleaned up incomplete segment file: {}", filename);
        } catch (IOException deleteError) {
            log.warn("Failed to cleanup segment file on create failure: {}", filename);
        }
    }
}