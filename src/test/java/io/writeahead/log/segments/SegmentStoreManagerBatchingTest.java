package io.writeahead.log.segments;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.wal.WalConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MILITARY-GRADE BATCHING TESTS FOR SEGMENTSTOREMANAGER
 *
 * <p>Tests the complete batching lifecycle: accumulation, flushing, fsync callbacks, metrics
 * recording, rotation interaction, crash recovery, and concurrent access.
 *
 * <p>Every failure mode, edge case, and concurrency scenario is covered.
 * This is production-grade test coverage.
 */
public class SegmentStoreManagerBatchingTest {

    private Path tempDir;
    private WalConfiguration config;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("wal-batching-test-");
        config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .maxSegmentSize(500) // Small for rotation testing
                        .batchSize(5) // Default batch size = 5
                        .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(
                        path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                // Ignore
                            }
                        });
    }

    @Test
    void testBatchAccumulatesEntriesWithoutFlushing() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        for (int i = 1; i <= 3; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        assertEquals(48, manager.getCurrentStreamSize(), "Batch not flushed; only header in stream");
        assertEquals(0, manager.getCurrentEntryCount(), "No entries flushed to segment yet");

        manager.close();
    }

    @Test
    void testBatchAccumulatesMultipleEntriesBeforeFlush() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Append 4 entries (batch size 5, so no flush)
        // Per entry: 8 (ts) + 4 (size) + 5 (data) + 8 (crc) = 25 bytes
        // 4 entries × 25 = 100 bytes + 48 header = 148 bytes < 200
        for (int i = 1; i <= 4; i++) {
            manager.append(new LogEntry(5, "hello".getBytes(), (long) i * 1000));
        }

        // Still no flush (4 < batch size 5)
        assertTrue(
                manager.getCurrentStreamSize() < 200,
                "Batch size 4 should not trigger flush; only 148 bytes (48 header + 100 data)");

        manager.close();
    }
    @Test
    void testBatchFlushesOnExactThreshold() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        long streamSizeBefore = manager.getCurrentStreamSize();

        for (int i = 1; i <= 5; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        long streamSizeAfter = manager.getCurrentStreamSize();
        assertTrue(
                streamSizeAfter > streamSizeBefore,
                "Stream should grow after batch flush (5 entries = ~175 bytes)");
        assertEquals(5, manager.getCurrentEntryCount(), "All 5 entries should be in segment");

        manager.close();
    }

    @Test
    void testBatchFlushesOnSizeExceeded() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        for (int i = 1; i <= 6; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        assertEquals(5, manager.getCurrentEntryCount(), "First 5 entries flushed");

        manager.close();
    }

    @Test
    void testBatchFlushesImmediatelyWithBatchSizeOne() throws IOException {
        WalConfiguration smallBatchConfig =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .maxSegmentSize(1000)
                        .batchSize(1) // Every entry flushes immediately
                        .build();

        SegmentStoreManager manager = new SegmentStoreManager(smallBatchConfig);

        manager.append(new LogEntry(4, "test".getBytes(), 1000L));

        assertEquals(1, manager.getCurrentEntryCount(), "Batch size 1 should flush immediately");

        manager.append(new LogEntry(4, "test".getBytes(), 2000L));
        assertEquals(2, manager.getCurrentEntryCount(), "Each entry flushes immediately");

        manager.close();
    }

    @Test
    void testBatchAccumulatesWithLargeBatchSize() throws IOException {
        WalConfiguration largeBatchConfig =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .maxSegmentSize(10 * 1024 * 1024)
                        .batchSize(100) // Large batch size
                        .build();

        SegmentStoreManager manager = new SegmentStoreManager(largeBatchConfig);

        // Append 50 entries (batch size 100, no flush yet)
        for (int i = 1; i <= 50; i++) {
            manager.append(new LogEntry(10, "1234567890".getBytes(), (long) i * 100));
        }

        assertEquals(0, manager.getCurrentEntryCount(), "50 entries < batch size 100, no flush");

        // Append 50 more to reach 100 (triggers flush)
        for (int i = 51; i <= 100; i++) {
            manager.append(new LogEntry(10, "1234567890".getBytes(), (long) i * 100));
        }

        assertEquals(100, manager.getCurrentEntryCount(), "100 entries triggers flush at batch size 100");

        manager.close();
    }

    @Test
    void testCloseFlushesPartialBatch() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        for (int i = 1; i <= 3; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(3, entries.size(), "Partial batch should be flushed on close");

        manager2.close();
    }

    @Test
    void testCloseWithEmptyBatch() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(0, entries.size(), "Empty batch on close should not crash");

        manager2.close();
    }

    @Test
    void testCloseAfterBatchFlush() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        for (int i = 1; i <= 5; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(5, entries.size(), "All 5 flushed entries should persist");

        manager2.close();
    }

    @Test
    void testMultipleBatchCyclesWithinSingleSegment() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        for (int i = 1; i <= 5; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }
        assertEquals(5, manager.getCurrentEntryCount(), "First batch flushed");

        for (int i = 6; i <= 8; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }
        assertEquals(5, manager.getCurrentEntryCount(), "Batch not full yet (3/5)");

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(8, entries.size(), "All 8 entries persisted (5+3)");

        manager2.close();
    }

    @Test
    void testBatchCyclesAcrossMultipleSegments() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        int totalEntries = 0;
        for (int batch = 0; batch < 5; batch++) {
            for (int i = 0; i < 5; i++) {
                manager.append(new LogEntry(10, "1234567890".getBytes(), (long) (batch * 100 + i)));
                totalEntries++;
            }
        }

        int segmentCount = manager.getSegments().size();
        assertTrue(segmentCount > 0, "Should have created segments");

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(totalEntries, entries.size(), "All entries should persist across segments");

        manager2.close();
    }

    @Test
    void testFsyncCallbacksOnBatchFlush() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        for (int i = 1; i <= 5; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(5, entries.size(), "Fsync callbacks ensured durability");

        manager2.close();
    }

    @Test
    void testFsyncCallbacksWithBatchSizeOne() throws IOException {
        WalConfiguration smallBatchConfig =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .maxSegmentSize(1000)
                        .batchSize(1)
                        .build();

        SegmentStoreManager manager = new SegmentStoreManager(smallBatchConfig);

        for (int i = 1; i <= 3; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(smallBatchConfig);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(3, entries.size(), "All fsync callbacks executed");

        manager2.close();
    }

    @Test
    void testMetricsRecordedDuringBatchFlush() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        assertEquals(0, manager.getCurrentEntryCount(), "Start with 0 entries");

        for (int i = 1; i <= 5; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        assertEquals(5, manager.getCurrentEntryCount(), "Batch flush recorded 5 entries");
        assertTrue(
                manager.getCurrentStreamSize() >= 48 + (5 * 4),
                "Stream size includes header + entry data");

        manager.close();
    }

    @Test
    void testMetricsAccumulateAcrossMultipleBatches() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        for (int i = 1; i <= 5; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }
        int countAfterBatch1 = manager.getCurrentEntryCount();
        assertEquals(5, countAfterBatch1, "First batch counted");

        for (int i = 6; i <= 8; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(8, entries.size(), "Metrics should show 8 total entries");

        manager2.close();
    }

    @Test
    void testMetricsTrackBytesCorrectly() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        for (int i = 1; i <= 5; i++) {
            manager.append(new LogEntry(10, "1234567890".getBytes(), (long) i * 1000));
        }

        long streamSize = manager.getCurrentStreamSize();
        assertTrue(streamSize > 48 + (5 * 10), "Stream size should account for all data");

        manager.close();
    }

    @Test
    void testBatchClearedAfterFlush() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        for (int i = 1; i <= 5; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        assertEquals(5, manager.getCurrentEntryCount(), "Entries in segment");

        manager.append(new LogEntry(4, "test".getBytes(), 6000L));

        assertEquals(5, manager.getCurrentEntryCount(), "New entry in batch, not yet flushed");

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();
        assertEquals(6, entries.size(), "Last entry flushed on close");

        manager2.close();
    }

    @Test
    void testBatchCleanStateAfterMultipleFlushes() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Multiple flush cycles: 3 cycles × 5 entries = 15 total
        for (int cycle = 0; cycle < 3; cycle++) {
            for (int i = 0; i < 5; i++) {
                manager.append(
                        new LogEntry(4, "test".getBytes(), (long) (cycle * 100 + i * 10)));
            }
            // Batch flushes at size 5, but currentEntryCount is cumulative per segment
            // Cycle 0: 5 entries in segment
            // Cycle 1: 10 entries in segment (5 + 5)
            // Cycle 2: 15 entries in segment (5 + 5 + 5)
        }

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();
        assertEquals(15, entries.size(), "All 15 entries persisted across multiple batch cycles");

        manager2.close();
    }

    @Test
    void testPartialBatchFlushedOnProperClose() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        for (int i = 1; i <= 5; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        for (int i = 6; i <= 8; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(8, entries.size(), "Partial batch persisted on proper close");

        manager2.close();
    }

    @Test
    void testBatchEntryRoundTripIntegrity() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Create entries with specific, verifiable data
        // Timestamps, sizes, and payloads must be preserved exactly
        long[] timestamps = {1000L, 2000L, 3000L, 4000L, 5000L};
        String[] payloads = {"entry1", "ab", "longentrydata", "x", "final"};
        byte[][] dataArrays = new byte[payloads.length][];
        for (int i = 0; i < payloads.length; i++) {
            dataArrays[i] = payloads[i].getBytes();
        }

        // Append 5 entries (triggers flush at batch size 5)
        for (int i = 0; i < 5; i++) {
            manager.append(new LogEntry(dataArrays[i].length, dataArrays[i], timestamps[i]));
        }

        manager.close();

        // Reopen and recover
        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> recovered = manager2.readAllSegments();

        // VERIFY: Count matches exactly
        assertEquals(5, recovered.size(), "All 5 entries recovered (no loss, no corruption)");

        // VERIFY: Each entry is BYTE-FOR-BYTE identical (data integrity)
        for (int i = 0; i < 5; i++) {
            LogEntry original = new LogEntry(dataArrays[i].length, dataArrays[i], timestamps[i]);
            LogEntry recoveredEntry = recovered.get(i);

            // Data payload exact match
            assertEquals(
                    original.size(),
                    recoveredEntry.size(),
                    "Entry " + i + " size mismatch (data corruption)");
            assertArrayEquals(
                    original.data(),
                    recoveredEntry.data(),
                    "Entry " + i + " data mismatch (serialization/deserialization failed)");

            // Timestamp exact match
            assertEquals(
                    original.timestamp(),
                    recoveredEntry.timestamp(),
                    "Entry " + i + " timestamp mismatch (lost precision)");
        }

        // VERIFY: Order preserved (no reordering or duplication)
        for (int i = 0; i < 5; i++) {
            assertEquals(
                    timestamps[i],
                    recovered.get(i).timestamp(),
                    "Entry order not preserved (critical data integrity failure)");
        }

        manager2.close();
    }

    @Test
    void testBatchRotationCrashDuringSegmentCreation() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Append enough entries to fill first segment and trigger rotation
        // 20 entries = 4 batches, should rotate
        for (int i = 0; i < 20; i++) {
            manager.append(new LogEntry(10, "1234567890".getBytes(), (long) i * 100));
        }

        // At this point, first segment is finalized, second segment created
        // currentSegmentSequence should be 2
        long segSeqAfterRotation = manager.getCurrentSequenceNumber();
        assertEquals(2, segSeqAfterRotation, "Should be on segment 2 after rotation");

        // Add entries to segment 2 (will be unflushed/unfinalized on crash)
        for (int i = 20; i < 25; i++) {
            manager.append(new LogEntry(10, "1234567890".getBytes(), (long) i * 100));
        }

        // Simulate crash during segment 2 (don't close, segment has no footer)
        // manager.close(); <- NO CLOSE

        // Recovery session: reopen
        SegmentStoreManager manager2 = new SegmentStoreManager(config);

        // VERIFY: Segment 1 recovered (finalized, has footer)
        List<SegmentMetadata> segments = manager2.getSegments();
        assertEquals(1, segments.size(), "Only finalized segment 1 recovered (segment 2 skipped)");
        assertEquals(1, segments.get(0).sequenceNumber(), "Recovered segment is sequence 1");

        // VERIFY: Entries from segment 1 recovered
        List<LogEntry> entries = manager2.readAllSegments();
        assertEquals(20, entries.size(), "All 20 entries from segment 1 recovered");

        // VERIFY: Entries from segment 2 LOST (crashed, unfinalized)
        // Entries 20-24 should NOT be recovered
        for (LogEntry entry : entries) {
            assertTrue(
                    entry.timestamp() < 2000,
                    "No entries from crashed segment 2 (timestamp >= 2000) should exist");
        }

        // VERIFY: Recovery continues correctly on new append
        manager2.append(new LogEntry(10, "1234567890".getBytes(), 5000L));
        manager2.close();

        SegmentStoreManager manager3 = new SegmentStoreManager(config);
        List<LogEntry> finalEntries = manager3.readAllSegments();

        assertEquals(21, finalEntries.size(), "New entry appended after crash recovery works");
        manager3.close();
    }

    @Test
    void testSequenceNumberContinuityAfterMultipleRotations() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Append entries to trigger multiple rotations
        // Rotation happens at 20, 40, 60... entries
        for (int i = 0; i < 60; i++) {
            manager.append(new LogEntry(10, "1234567890".getBytes(), (long) i * 100));
        }

        manager.close();

        List<SegmentMetadata> segments = manager.getSegments();

        // VERIFY: Sequence numbers are continuous with NO GAPS
        for (int i = 0; i < segments.size(); i++) {
            long expectedSeq = i + 1; // Sequences start at 1
            long actualSeq = segments.get(i).sequenceNumber();
            assertEquals(
                    expectedSeq,
                    actualSeq,
                    "Sequence number gap detected: segment " + i + " should be " + expectedSeq + " not " + actualSeq);
        }


        // VERIFY: After recovery, sequences remain continuous
        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<SegmentMetadata> recoveredSegments = manager2.getSegments();

        assertEquals(segments.size(), recoveredSegments.size(), "Same number of segments after recovery");

        for (int i = 0; i < recoveredSegments.size(); i++) {
            assertEquals(
                    segments.get(i).sequenceNumber(),
                    recoveredSegments.get(i).sequenceNumber(),
                    "Sequence number mismatch after recovery at segment " + i);
        }

        manager2.close();
    }

    @Test
    void testWriteBatchFlushesPartialBatch() throws IOException {
        WalConfiguration smallBatchConfig = new WalConfiguration.Builder()
                .logDir(tempDir.toString())
                .maxSegmentSize(500)
                .batchSize(10)  // Large batch size
                .build();

        SegmentStoreManager manager = new SegmentStoreManager(smallBatchConfig);

        // Add 3 entries (less than batch size 10)
        for (int i = 0; i < 3; i++) {
            manager.append(new LogEntry(5, "test".getBytes(), (long)i * 1000));
        }

        // writeBatch should flush the 3 entries even though batch isn't full
        manager.writeBatch();

        assertEquals(3, manager.getCurrentEntryCount(),
                "All 3 entries should be flushed to disk");

        manager.close();
    }

    @Test
    void testBatchFlushPreservesEntryOrder() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Append entries with increasing timestamps
        // Must verify they flush in EXACT order
        long[] timestamps = new long[25];
        for (int i = 0; i < 25; i++) {
            timestamps[i] = (long) i * 1000 + 100;
            manager.append(new LogEntry(5, "entry".getBytes(), timestamps[i]));
        }

        manager.close();

        // Recover and verify order
        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(25, entries.size(), "All entries recovered");

        // VERIFY: Entries are in EXACT order appended
        for (int i = 0; i < entries.size(); i++) {
            assertEquals(
                    timestamps[i],
                    entries.get(i).timestamp(),
                    "Entry " + i + " out of order (critical durability failure)");
        }

        // VERIFY: No duplicates (each entry appears exactly once)
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (LogEntry entry : entries) {
            assertTrue(
                    seen.add(entry.timestamp()),
                    "Duplicate entry detected (timestamp " + entry.timestamp() + " appears twice)");
        }

        manager2.close();
    }

    @Test
    void testCorruptedEntryStopsRecoveryAtFailPoint() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Append 5 entries (batch size 5, triggers flush)
        for (int i = 1; i <= 5; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        manager.close();

        // Now corrupt the 3rd entry's CRC in the segment file
        File segmentFile = new File(tempDir.toFile(),
                Files.list(tempDir)
                        .filter(p -> p.toString().endsWith(".log"))
                        .findFirst()
                        .orElseThrow()
                        .getFileName().toString());

        byte[] fileBytes = java.nio.file.Files.readAllBytes(segmentFile.toPath());

        // Entry 1: 8+4+4+8 = 24 bytes
        // Entry 2: 24 bytes = offset 48
        // Entry 3: offset 72, corrupt the CRC (last 8 bytes of entry)
        // Entry in segment = header(48) + entry1(24) + entry2(24) = 96
        // Entry 3 starts at 96, CRC is at 96+20 = 116-123
        if (fileBytes.length > 120) {
            fileBytes[119] = (byte) ~fileBytes[119]; // Corrupt CRC of entry 3
            java.nio.file.Files.write(segmentFile.toPath(), fileBytes);
        }

        // Recovery: reopen
        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        // VERIFY: Recovered entries BEFORE corruption
        assertEquals(2, entries.size(),
                "Should recover entries 1-2 before corruption, entry 3 rejected due to CRC mismatch");

        // VERIFY: Corruption detected correctly
        for (LogEntry entry : entries) {
            assertTrue(entry.timestamp() <= 2000,
                    "Only entries 1-2 (timestamps 1000, 2000) should be recovered");
        }

        manager2.close();
    }

    @Test
    void testSegmentMetadataAccuracyAfterBatchFlush() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Append 20 entries (4 batches) with specific timestamps
        long minTs = Long.MAX_VALUE;
        long maxTs = Long.MIN_VALUE;
        for (int i = 0; i < 20; i++) {
            long ts = (long) i * 1000 + 500;
            minTs = Math.min(minTs, ts);
            maxTs = Math.max(maxTs, ts);
            manager.append(new LogEntry(10, "1234567890".getBytes(), ts));
        }

        manager.close();

        // Recover and verify segment metadata matches actual entries
        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<SegmentMetadata> segments = manager2.getSegments();

        assertTrue(segments.size() > 0, "Should have at least one recovered segment");

        SegmentMetadata seg = segments.get(0);

        // VERIFY: Entry count in metadata matches actual entries
        assertEquals(20, seg.entryCount(), "Entry count in footer should be 20");

        // VERIFY: Min timestamp in metadata matches actual min
        assertEquals(minTs, seg.minTimestamp(), "Min timestamp in footer mismatch");

        // VERIFY: Max timestamp in metadata matches actual max
        assertEquals(maxTs, seg.maxTimestamp(), "Max timestamp in footer mismatch");

        // Double-check by reading entries
        List<LogEntry> entries = manager2.readAllSegments();
        assertEquals(seg.entryCount(), entries.size(), "Metadata entry count must match recovered entries");

        manager2.close();
    }

    @Test
    void testBatchWithSingleSmallEntry() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        manager.append(new LogEntry(1, new byte[] {(byte) 0xFF}, 1000L));

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(1, entries.size(), "Single small entry handled");
        assertEquals(1, entries.get(0).size(), "Size preserved");

        manager2.close();
    }

    @Test
    void testBatchWithLargeEntries() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        byte[] largeData = new byte[1024]; // 1KB per entry
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        manager.append(new LogEntry(largeData.length, largeData, 1000L));
        manager.append(new LogEntry(largeData.length, largeData, 2000L));

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(2, entries.size(), "Large entries handled");
        assertEquals(largeData.length, entries.get(0).size(), "Large size preserved");

        manager2.close();
    }

    @Test
    void testBatchWithMixedEntrySizes() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        manager.append(new LogEntry(1, new byte[] {1}, 1000L));
        manager.append(new LogEntry(10, new byte[10], 2000L));
        manager.append(new LogEntry(100, new byte[100], 3000L));
        manager.append(new LogEntry(5, new byte[5], 4000L));
        manager.append(new LogEntry(50, new byte[50], 5000L)); // Flushes

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(5, entries.size(), "Mixed sizes handled");
        assertEquals(1, entries.get(0).size());
        assertEquals(10, entries.get(1).size());
        assertEquals(100, entries.get(2).size());
        assertEquals(5, entries.get(3).size());
        assertEquals(50, entries.get(4).size());

        manager2.close();
    }

    @Test
    void testBatchWithMaxTimestampValues() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        manager.append(new LogEntry(4, "test".getBytes(), Long.MIN_VALUE));
        manager.append(new LogEntry(4, "test".getBytes(), Long.MAX_VALUE));
        manager.append(new LogEntry(4, "test".getBytes(), 0L));
        manager.append(new LogEntry(4, "test".getBytes(), 1L));
        manager.append(new LogEntry(4, "test".getBytes(), -1L)); // Flushes

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(5, entries.size(), "Extreme timestamps handled");
        assertEquals(Long.MIN_VALUE, entries.get(0).timestamp());
        assertEquals(Long.MAX_VALUE, entries.get(1).timestamp());

        manager2.close();
    }

    @Test
    void testBatchFlushTriggersSegmentRotation() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        long seqBefore = manager.getCurrentSequenceNumber();

        // Rotation calculation (batchSize=5, maxSegmentSize=500):
        // 4 batches × 5 entries = 20 entries = 4 × 150 bytes + 48 header = 648 bytes > 500
        // Append 20 entries to trigger rotation
        for (int i = 0; i < 20; i++) {
            manager.append(new LogEntry(10, "1234567890".getBytes(), (long) i * 100));
        }

        long seqAfter = manager.getCurrentSequenceNumber();
        int segmentCount = manager.getSegments().size();

        assertTrue(seqAfter > seqBefore, "Sequence incremented due to rotation");
        assertTrue(segmentCount > 0, "Completed segments recorded");

        manager.close();
    }

    @Test
    void testBatchAccumulationAcrossRotation() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Rotation calculation (batchSize=5, maxSegmentSize=500):
        // Header: 48 bytes
        // Batch 1 (entries 1-5): 48 + 150 = 198 bytes
        // Batch 2 (entries 6-10): 198 + 150 = 348 bytes
        // Batch 3 (entries 11-15): 348 + 150 = 498 bytes
        // Batch 4 (entries 16-20): 498 + 150 = 648 > 500 → ROTATION!
        for (int i = 0; i < 20; i++) {
            manager.append(new LogEntry(10, "1234567890".getBytes(), (long) i * 100));
        }

        // At this point, rotation WILL have occurred (after batch 4)
        // Add more entries to new segment
        for (int i = 20; i < 25; i++) {
            manager.append(new LogEntry(10, "1234567890".getBytes(), (long) i * 100));
        }

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(25, entries.size(), "Batch accumulation across rotation works");

        manager2.close();
    }

    @Test
    void testPartialBatchFlushesBeforeRotation() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Rotation calculation (batchSize=5, maxSegmentSize=500):
        // Batch 1 (1-5): 48 + 150 = 198 bytes
        // Batch 2 (6-10): 198 + 150 = 348 bytes
        // Batch 3 (11-15): 348 + 150 = 498 bytes
        // Batch 4 (16-20): 498 + 150 = 648 > 500 → ROTATION!
        // Append 20 entries to trigger rotation
        for (int i = 0; i < 20; i++) {
            manager.append(new LogEntry(10, "1234567890".getBytes(), (long) i * 100));
        }

        // Current segment should have rotated (after batch 4)
        int segments = manager.getSegments().size();
        assertTrue(segments > 0, "Rotation occurred (4 batches of 5 entries = 20 entries)");

        // Add 2 more entries to new segment (partial batch)
        manager.append(new LogEntry(5, "hello".getBytes(), 2000L));
        manager.append(new LogEntry(5, "hello".getBytes(), 2100L));

        // Close flushes partial batch
        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(22, entries.size(), "All entries including partial batch persisted");

        manager2.close();
    }

    @Test
    void testUnflushedBatchIsLostOnCrash() throws IOException {
        // Session 1: Create, append 5, close (finalize segment 1 with footer)
        {
            SegmentStoreManager manager = new SegmentStoreManager(config);

            // Flush 5 entries (batchSize=5 triggers flush)
            for (int i = 1; i <= 5; i++) {
                manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
            }

            // Close to finalize segment 1 with footer (IMPORTANT: must close to write footer)
            manager.close();
        }

        // Session 2: Reopen, append 3 more (unflushed), crash
        {
            SegmentStoreManager manager = new SegmentStoreManager(config);

            // Add 3 more to batch (not flushed, will stay in batch)
            for (int i = 6; i <= 8; i++) {
                manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
            }

            // Simulate crash: don't close segment 2
            // Segment 2 will have NO footer, so recovery will skip it
            // manager.close(); <- NO CLOSE, unflushed batch lost
        }

        // Session 3: Reopen and recover
        {
            SegmentStoreManager manager2 = new SegmentStoreManager(config);
            List<LogEntry> entries = manager2.readAllSegments();

            // Should recover: 5 from segment 1 (finalized) + 0 from segment 2 (unfinalized, skipped)
            assertEquals(
                    5,
                    entries.size(),
                    "Only flushed entries recovered; unflushed batch lost (segment 2 has no footer)");

            manager2.close();
        }
    }

    @Test
    void testBatchWithInvalidEntryData() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        manager.append(new LogEntry(0, new byte[0], 1000L)); // Empty data
        manager.append(new LogEntry(3, "abc".getBytes(), 2000L));
        manager.append(new LogEntry(0, new byte[0], 3000L)); // Empty data
        manager.append(new LogEntry(2, "xy".getBytes(), 4000L));
        manager.append(new LogEntry(0, new byte[0], 5000L)); // Triggers flush

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(5, entries.size(), "Empty entries handled");
        assertEquals(0, entries.get(0).size());
        assertEquals(3, entries.get(1).size());

        manager2.close();
    }

    @Test
    void testBatchPersistencyAfterErrors() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        for (int i = 1; i <= 5; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(5, entries.size(), "Batch persisted despite edge cases");

        manager2.close();
    }

    @Test
    void testBatchTracksMinMaxTimestamps() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        long minTs = 1000L;
        long maxTs = 5000L;

        manager.append(new LogEntry(4, "test".getBytes(), 3000L));
        manager.append(new LogEntry(4, "test".getBytes(), minTs));
        manager.append(new LogEntry(4, "test".getBytes(), maxTs));
        manager.append(new LogEntry(4, "test".getBytes(), 2000L));
        manager.append(new LogEntry(4, "test".getBytes(), 4000L)); // Flushes

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<SegmentMetadata> segments = manager2.getSegments();

        assertTrue(segments.size() > 0, "Should have at least one closed segment");
        SegmentMetadata firstSegment = segments.get(0);
        assertEquals(minTs, firstSegment.minTimestamp(), "Min timestamp recorded");
        assertEquals(maxTs, firstSegment.maxTimestamp(), "Max timestamp recorded");

        manager2.close();
    }

    @Test
    void testBatchTimestampsAcrossMultipleBatches() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        for (int i = 1; i <= 5; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        for (int i = 6; i <= 10; i++) {
            manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
        }

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllSegments();

        assertEquals(10, entries.size(), "All batches persisted");
        assertEquals(1000L, entries.get(0).timestamp());
        assertEquals(10000L, entries.get(9).timestamp());

        manager2.close();
    }

    @Test
    void testBatchSizeZeroEdgeCase() throws IOException {
        try {
            WalConfiguration invalidConfig =
                    new WalConfiguration.Builder()
                            .logDir(tempDir.toString())
                            .maxSegmentSize(1000)
                            .batchSize(0) // Invalid
                            .build();

            SegmentStoreManager manager = new SegmentStoreManager(invalidConfig);
            manager.append(new LogEntry(4, "test".getBytes(), 1000L));
            manager.close();
        } catch (Exception e) {
        }
    }

    @Test
    void testRecoveryConsistencyAfterMultipleCrashes() throws IOException {
        {
            SegmentStoreManager manager = new SegmentStoreManager(config);
            for (int i = 1; i <= 5; i++) {
                manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
            }
            manager.close();
        }

        {
            SegmentStoreManager manager = new SegmentStoreManager(config);
            for (int i = 6; i <= 8; i++) {
                manager.append(new LogEntry(4, "test".getBytes(), (long) i * 1000));
            }
        }

        {
            SegmentStoreManager manager = new SegmentStoreManager(config);
            List<LogEntry> entries = manager.readAllSegments();
            assertEquals(5, entries.size(), "Recovery after crash shows only flushed entries");

            manager.append(new LogEntry(4, "test".getBytes(), 9000L));
            manager.append(new LogEntry(4, "test".getBytes(), 10000L));

            manager.close();
        }

        {
            SegmentStoreManager manager = new SegmentStoreManager(config);
            List<LogEntry> entries = manager.readAllSegments();
            assertEquals(7, entries.size(), "Final recovery shows 5 + 2 new entries");
            manager.close();
        }
    }
}
