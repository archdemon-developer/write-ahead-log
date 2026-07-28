package io.writeahead.log.segments;

import io.writeahead.log.constants.WalConstants;
import io.writeahead.log.exceptions.CorruptionException;
import io.writeahead.log.fsync.FsyncExecutor;
import io.writeahead.log.fsync.FsyncRetryStrategy;
import io.writeahead.log.fsync.factory.FsyncExecutorFactory;
import io.writeahead.log.fsync.factory.FsyncRetryStrategyFactory;
import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.metrics.SimpleWalMetrics;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.file.FileStream;
import io.writeahead.log.models.wal.WalConfiguration;
import io.writeahead.log.models.wal.WalMetadata;
import io.writeahead.log.serdes.EntrySerdes;
import io.writeahead.log.utils.Crc32Utils;
import io.writeahead.log.utils.FileUtils;
import io.writeahead.log.utils.WalErrorClassifier;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SegmentStoreManager implements SegmentStore {

    private static final Logger log = LoggerFactory.getLogger(SegmentStoreManager.class);

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

    private final List<LogEntry> batch;
    private FsyncExecutor fsyncExecutor;
    private final FsyncRetryStrategy fsyncRetryStrategy;
    private final SimpleWalMetrics metrics = new SimpleWalMetrics();

    private boolean isOpen;

    public SegmentStoreManager(WalConfiguration config) throws IOException {
        this.config = config;

        SegmentMetadataRecovery metadataRecovery = new SegmentMetadataRecovery(config.logDir());
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

        this.batch = new ArrayList<>();
        this.fsyncRetryStrategy = FsyncRetryStrategyFactory.create(config, metrics);
        this.fsyncExecutor = FsyncExecutorFactory.create(config.fsyncStrategy(), fsyncRetryStrategy, currentStream);

        this.isOpen = true;

        log.info("SegmentStoreManager initialized: {} segments recovered, next sequence: {}",
                segments.size(), nextSegmentSequence);
    }

    public void append(LogEntry entry) throws IOException {
        if (!isOpen) {
            throw WalErrorClassifier.classifyIOException(
                    new IOException("SegmentStoreManager is closed"), "append to closed WAL");
        }

        batch.add(entry);

        if (batch.size() >= config.batchSize()) {
            flushAndFsync();
        }
    }

    public void writeBatch() throws IOException {
        if (batch.isEmpty()) {
            log.debug("writeBatch called but batch is empty, nothing to flush");
            return;
        }

        flushAndFsync();
    }

    private void flushAndFsync() throws IOException {
        if (batch.isEmpty()) {
            return;
        }

        for (LogEntry entry : batch) {
            long crc = Crc32Utils.computeEntryCrc(entry.timestamp(), entry.data().length, entry.data());
            byte[] entryBytes = EntrySerdes.serializeEntryWithCrc(entry.timestamp(), entry.data().length, entry.data(), crc);
            try {
                FileUtils.writeToStream(currentStream, entryBytes);
            } catch (IOException ex) {
                throw WalErrorClassifier.classifyIOException(ex, "write entry to segment");
            }

            currentStreamSize += entryBytes.length;
            currentEntryCount++;
            currentMinTimestamp = Math.min(currentMinTimestamp, entry.timestamp());
            currentMaxTimestamp = Math.max(currentMaxTimestamp, entry.timestamp());

            try {
                fsyncExecutor.onEntryWritten();
            } catch (IOException ex) {
                throw WalErrorClassifier.classifyIOException(ex, "fsync after entry write");
            }
            metrics.recordEntryWritten(entry.data().length);
        }

        try {
            fsyncExecutor.onBatchComplete();
        } catch (IOException ex) {
            throw WalErrorClassifier.classifyIOException(ex, "fsync after batch complete");
        }
        batch.clear();

        if (currentStreamSize >= config.maxSegmentSize()) {
            rotateSegment();
        }
    }

    public List<LogEntry> readAllSegments() throws IOException {
        List<LogEntry> allEntries = new ArrayList<>();

        for(SegmentMetadata metadata : segments) {
            File segmentFile = new File(config.logDir(), metadata.filename());
            if(!segmentFile.exists()) {
                log.warn("Segment file not found during read: {}", metadata.filename());
                continue;
            }

            byte[] allBytes = FileUtils.readAllBytes(segmentFile);
            byte[] entryRegionBytes = extractEntryRegion(allBytes);

            try {
                SegmentEntriesReader.SegmentReadResult result = segmentReader.readEntriesFromRegion(entryRegionBytes);
                allEntries.addAll(result.entries());
            } catch (CorruptionException ex) {
                log.error("Corruption detected in segment {}: {}", metadata.filename(), ex.getMessage());
            }
        }

        log.debug("Read {} entries from {} segments", allEntries.size(), segments.size());
        return allEntries;
    }

    public void close() throws IOException {
        if (!isOpen) {
            return;
        }

        try {
            writeBatch();
            lifecycleManager.closeSegment(currentStream, currentEntryCount,
                    currentMinTimestamp, currentMaxTimestamp);

            SegmentMetadata currentMetadata = new SegmentMetadata(
                    SegmentLifecycleManager.generateSegmentFilename(currentSequenceNumber),
                    currentSequenceNumber,
                    System.currentTimeMillis(),
                    currentStreamSize,
                    currentEntryCount,
                    currentMinTimestamp,
                    currentMaxTimestamp
            );
            segments.add(currentMetadata);
        } finally {
            isOpen = false;
        }

        log.info("SegmentStoreManager closed: finalized segment {} with {} entries",
                currentSequenceNumber, currentEntryCount);
    }

    private void rotateSegment() throws IOException {
        try {
            lifecycleManager.finalizeSegment(currentStream, currentEntryCount, currentMinTimestamp, currentMaxTimestamp);
        }  catch (IOException ex) {
            throw WalErrorClassifier.classifyIOException(ex, "finalize segment during rotation");
        }

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
        try {
            this.currentStream = lifecycleManager.createNewSegment(currentSequenceNumber);
        } catch (IOException ex) {
            throw WalErrorClassifier.classifyIOException(ex, "create new segment during rotation");
        }
        this.currentStreamSize = WalConstants.SEGMENT_HEADER_SIZE;
        this.currentEntryCount = 0;
        this.currentMinTimestamp = Long.MAX_VALUE;
        this.currentMaxTimestamp = Long.MIN_VALUE;

        this.fsyncExecutor = FsyncExecutorFactory.create(config.fsyncStrategy(), fsyncRetryStrategy, currentStream);

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
        List<LogEntry> allEntries = readAllSegments();
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
            if(segmentMetadata.maxTimestamp() <= timestamp) {
                toDelete.add(segmentMetadata);
            }
        }

        if (toDelete.size() == segments.size()) {
            toDelete.removeLast();
        }

        for(SegmentMetadata segmentMetadata : toDelete) {
            File segmentFile = new File(config.logDir(), segmentMetadata.filename());
            try {
                FileUtils.deleteFile(segmentFile);
                segments.remove(segmentMetadata);
                log.info("Truncated segment: {}", segmentMetadata.filename());
            } catch (IOException ex) {
                log.error("Failed to delete segment {}: {}", segmentMetadata.filename(), ex.getMessage());
            }
        }
    }

    @Override
    public SimpleWalMetrics getMetrics() {
        return metrics;
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
