package io.writeahead.log.segments;

import io.writeahead.log.constants.WalConstants;
import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.file.FileStream;
import io.writeahead.log.models.segment.SegmentMetadata;
import io.writeahead.log.models.wal.WalConfiguration;
import io.writeahead.log.models.wal.WalMetadata;
import io.writeahead.log.serdes.EntrySerdes;
import io.writeahead.log.utils.Crc32Utils;
import io.writeahead.log.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SegmentStoreManager {

    private static final Logger log = LoggerFactory.getLogger(SegmentStoreManager.class);

    private final SegmentMetadataRecovery metadataRecovery;
    private final SegmentLifecycleManager lifecycleManager;
    private final SegmentEntriesReader segmentReader;
    private final WalConfiguration config;

    private final List<SegmentMetadata> segments;
    private long nextSegmentSequence;

    private long currentSequenceNumber;
    private FileStream currentStream;
    private long currentStreamSize;
    private int currentEntryCount;
    private long currentMinTimestamp;
    private long currentMaxTimestamp;

    private boolean isOpen;

    public SegmentStoreManager(WalConfiguration config) throws IOException {
        this.config = config;

        this.metadataRecovery = new SegmentMetadataRecovery(config.logDir());
        this.lifecycleManager = new SegmentLifecycleManager(config.logDir());
        this.segmentReader = new SegmentEntriesReader();

        WalMetadata walMetadata = metadataRecovery.recover();
        this.segments = new ArrayList<>(walMetadata.segments());
        this.nextSegmentSequence = walMetadata.nextSequence();

        this.currentSequenceNumber = nextSegmentSequence++;
        this.currentStream = lifecycleManager.createNewSegment(currentSequenceNumber);
        this.currentStreamSize = 48;
        this.currentEntryCount = 0;
        this.currentMinTimestamp = Long.MAX_VALUE;
        this.currentMaxTimestamp = Long.MIN_VALUE;

        this.isOpen = true;

        log.info("SegmentStoreManager initialized: {} segments recovered, next sequence: {}",
                segments.size(), nextSegmentSequence);
    }

    public void append(LogEntry entry) throws IOException {
        if(!isOpen) {
            throw new IOException("SegmentStoreManager is closed");
        }

        long crc = Crc32Utils.computeEntryCrc(entry.timestamp(), entry.data().length, entry.data());

        byte[] entryBytes = EntrySerdes.serializeEntryWithCrc(entry.timestamp(), entry.data().length, entry.data(), crc);

        FileUtils.writeToStream(currentStream, entryBytes);
        currentStreamSize += entryBytes.length;
        currentEntryCount += 1;
        currentMinTimestamp = Math.min(currentMinTimestamp, entry.timestamp());
        currentMaxTimestamp = Math.max(currentMaxTimestamp, entry.timestamp());

        if(currentStreamSize >= config.maxSegmentSize()) {
            log.info("DEBUG: Triggering rotation - size {} >= max {}",
                    currentStreamSize, config.maxSegmentSize());
            rotateSegment();
        }
    }

    public List<LogEntry> readAllEntries() throws IOException {
        List<LogEntry> allEntries = new ArrayList<>();

        for(SegmentMetadata metadata : segments) {
            File segmentFile = new File(config.logDir(), metadata.filename());
            if(!segmentFile.exists()) {
                log.warn("Segment file not found during read: {}", metadata.filename());
                continue;
            }

            byte[] allBytes = FileUtils.readAllBytes(segmentFile);
            byte[] entryRegionBytes = extractEntryRegion(allBytes);

            SegmentEntriesReader.SegmentReadResult result = segmentReader.readEntriesFromRegion(entryRegionBytes);
            allEntries.addAll(result.entries());

            if(result.hasCorruption()) {
                log.warn("Corruption detected in segment {} at entry {}", metadata.filename(), result.corruptionAtEntry());
            }
        }

        log.debug("Read {} entries from {} segments", allEntries.size(), segments.size());
        return allEntries;
    }

    public void close() throws IOException {
        if (!isOpen) {
            return;
        }

        lifecycleManager.closeSegment(currentStream, currentEntryCount,
                currentMinTimestamp, currentMaxTimestamp);
        isOpen = false;

        log.info("SegmentStoreManager closed: finalized segment {} with {} entries",
                currentSequenceNumber, currentEntryCount);
    }

    private void rotateSegment() throws IOException {
        lifecycleManager.finalizeSegment(currentStream, currentEntryCount, currentMinTimestamp, currentMaxTimestamp);

        SegmentMetadata completedMetadata = new SegmentMetadata(
                SegmentLifecycleManager.generateSegmentFilename(currentSequenceNumber),
                currentSequenceNumber,
                System.currentTimeMillis(),
                currentStreamSize,
                currentEntryCount,
                currentMinTimestamp,
                currentMaxTimestamp
        );

        segments.add(completedMetadata);
        currentSequenceNumber = nextSegmentSequence++;
        this.currentStream = lifecycleManager.createNewSegment(currentSequenceNumber);
        this.currentStreamSize = WalConstants.SEGMENT_HEADER_SIZE;
        this.currentEntryCount = 0;
        this.currentMinTimestamp = Long.MAX_VALUE;
        this.currentMaxTimestamp = Long.MIN_VALUE;

        log.info("Rotated segment: {} -> {}", currentSequenceNumber - 1, currentSequenceNumber);
    }

    private byte[] extractEntryRegion(byte[] allBytes) {
        int headerSize = WalConstants.SEGMENT_HEADER_SIZE;
        int footerSize = WalConstants.SEGMENT_FOOTER_SIZE;

        int entryRegionEnd = allBytes.length - footerSize;

        if(entryRegionEnd <= headerSize) {
            return new byte[0];
        }

        byte[] entryRegion = new byte[entryRegionEnd - headerSize];
        System.arraycopy(allBytes, headerSize, entryRegion, 0, entryRegion.length);
        return entryRegion;
    }

    public List<LogEntry> readAllAfterTimestamp(long timestamp) throws IOException {
        List<LogEntry> allEntries = readAllEntries();
        List<LogEntry> filtered = new ArrayList<>();

        for(LogEntry entry : allEntries) {
            if(entry.timestamp() > timestamp) {
                filtered.add(entry);
            }
        }

        return filtered;
    }

    public void truncateBeforeTimestamp(long timestamp) throws IOException {
        List<SegmentMetadata> toDelete = new ArrayList<>();

        for(SegmentMetadata segmentMetadata : segments) {
            log.info("DEBUG: Checking segment {}: maxTs={}, threshold={}, delete={}",
                    segmentMetadata.filename(), segmentMetadata.maxTimestamp(), timestamp, segmentMetadata.maxTimestamp() < timestamp);
            if(segmentMetadata.maxTimestamp() <= timestamp) {
                toDelete.add(segmentMetadata);
            }
        }

        if (toDelete.size() == segments.size()) {
            toDelete.removeLast();
        }

        for(SegmentMetadata segmentMetadata : toDelete) {
            File segmentFile = new File(config.logDir(), segmentMetadata.filename());
            FileUtils.deleteFile(segmentFile);
            segments.remove(segmentMetadata);
            log.info("Truncated segment: {}", segmentMetadata.filename());
        }
    }

    public List<SegmentMetadata> getSegments() {
        return new ArrayList<>(segments);
    }

    public long getCurrentSequenceNumber() {
        return currentSequenceNumber;
    }

    public int getCurrentEntryCount() {
        return currentEntryCount;
    }

    public long getCurrentStreamSize() {
        return currentStreamSize;
    }

    public boolean isOpen() {
        return isOpen;
    }
}
