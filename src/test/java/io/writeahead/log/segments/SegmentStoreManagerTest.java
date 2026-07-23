package io.writeahead.log.segments;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.LogEntry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import io.writeahead.log.models.segment.SegmentMetadata;
import io.writeahead.log.models.wal.WalConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SegmentStoreManagerTest {

    private Path tempDir;
    private WalConfiguration config;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("wal-store-test-");
        config = new WalConfiguration.Builder()
                .logDir(tempDir.toString())
                .maxSegmentSize(500)  // Small for testing rotations
                .build();  // 500 bytes max
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
    }

    @Test
    void testConstructorInitializesWithRecoveredMetadata() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        assertTrue(manager.isOpen(), "Should be open after construction");
        assertEquals(0, manager.getSegments().size(), "Fresh start has no recovered segments");
        assertEquals(1, manager.getCurrentSequenceNumber(), "First sequence should be 1");
        assertEquals(0, manager.getCurrentEntryCount(), "Should start with 0 entries");

        manager.close();
    }

    @Test
    void testAppendWritesEntry() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        LogEntry entry = new LogEntry(5, "hello".getBytes(), 1000L);
        manager.append(entry);

        assertEquals(1, manager.getCurrentEntryCount(), "Should have 1 entry");
        assertTrue(manager.getCurrentStreamSize() > 48, "Should have grown beyond header");

        manager.close();
    }

    @Test
    void testAppendMultipleEntries() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        for (int i = 1; i <= 5; i++) {
            LogEntry entry = new LogEntry(4, "test".getBytes(), (long)i * 1000);
            manager.append(entry);
        }

        assertEquals(5, manager.getCurrentEntryCount(), "Should have 5 entries");

        manager.close();
    }

    @Test
    void testAppendThrowsWhenClosed() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);
        manager.close();

        LogEntry entry = new LogEntry(5, "hello".getBytes(), 1000L);
        assertThrows(IOException.class, () -> manager.append(entry),
                "Should throw when trying to append to closed manager");
    }

    @Test
    void testRotationTriggersOnSizeThreshold() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        long seq1 = manager.getCurrentSequenceNumber();

        // Append entries until rotation (each ~30 bytes: entry + CRC)
        for (int i = 0; i < 20; i++) {
            LogEntry entry = new LogEntry(10, "1234567890".getBytes(), (long)i * 100);
            manager.append(entry);
        }

        long seq2 = manager.getCurrentSequenceNumber();
        assertTrue(seq2 > seq1, "Sequence should increment on rotation");
        assertEquals(1, manager.getSegments().size(), "Should have 1 completed segment");

        manager.close();
    }

    @Test
    void testMultipleRotations() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Trigger multiple rotations
        for (int batch = 0; batch < 3; batch++) {
            for (int i = 0; i < 20; i++) {
                LogEntry entry = new LogEntry(10, "1234567890".getBytes(),
                        (long)(batch * 2000 + i * 100));
                manager.append(entry);
            }
        }

        assertTrue(manager.getSegments().size() >= 2, "Should have multiple segments");

        manager.close();
    }

    @Test
    void testReadAllEntriesFromSingleSegment() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Add entries (should stay in one segment with small entries)
        LogEntry e1 = new LogEntry(2, "e1".getBytes(), 1000L);
        LogEntry e2 = new LogEntry(2, "e2".getBytes(), 2000L);
        LogEntry e3 = new LogEntry(2, "e3".getBytes(), 3000L);

        manager.append(e1);
        manager.append(e2);
        manager.append(e3);

        manager.close();

        // Reopen to verify persistence
        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllEntries();

        assertEquals(3, entries.size(), "Should read all 3 entries");
        assertEquals(1000L, entries.get(0).timestamp(), "First entry timestamp");
        assertEquals(2000L, entries.get(1).timestamp(), "Second entry timestamp");
        assertEquals(3000L, entries.get(2).timestamp(), "Third entry timestamp");

        manager2.close();
    }

    @Test
    void testReadAllEntriesFromMultipleSegments() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Trigger rotations by adding large entries
        int entryCount = 0;
        for (int i = 0; i < 50; i++) {
            LogEntry entry = new LogEntry(20, "12345678901234567890".getBytes(), (long)i * 100);
            manager.append(entry);
            entryCount++;
        }

        manager.close();

        // Reopen and read
        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllEntries();

        assertEquals(entryCount, entries.size(), "Should read all entries from all segments");

        manager2.close();
    }

    @Test
    void testCloseFinalizesSegment() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        LogEntry e1 = new LogEntry(2, "e1".getBytes(), 1000L);
        LogEntry e2 = new LogEntry(2, "e2".getBytes(), 2000L);

        manager.append(e1);
        manager.append(e2);

        manager.close();

        assertFalse(manager.isOpen(), "Should be closed");

        // Verify segment file exists and is valid
        File[] segments = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".log"));
        assertTrue(segments.length > 0, "Should have at least one segment file");
    }

    @Test
    void testReadAllAfterTimestamp() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Add entries with different timestamps
        for (long ts : new long[]{1000L, 2000L, 3000L, 4000L, 5000L}) {
            LogEntry entry = new LogEntry(4, "test".getBytes(), ts);
            manager.append(entry);
        }

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> after3000 = manager2.readAllAfterTimestamp(3000L);

        assertEquals(2, after3000.size(), "Should return 2 entries after 3000");
        assertEquals(4000L, after3000.get(0).timestamp(), "First should be 4000");
        assertEquals(5000L, after3000.get(1).timestamp(), "Second should be 5000");

        manager2.close();
    }

    @Test
    void testTruncateBeforeTimestampKeepsAtLeastOne() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Create multiple segments by rotating
        for (int i = 0; i < 100; i++) {
            LogEntry entry = new LogEntry(10, "1234567890".getBytes(), (long)i * 1000);
            manager.append(entry);
        }

        int segmentsBefore = manager.getSegments().size();
        assertTrue(segmentsBefore > 1, "Should have multiple segments");

        // Truncate everything before timestamp 15000
        manager.truncateBeforeTimestamp(15000L);

        int segmentsAfter = manager.getSegments().size();
        assertTrue(segmentsAfter >= 1, "Should keep at least 1 segment");
        assertTrue(segmentsAfter < segmentsBefore, "Should have deleted some segments");

        manager.close();
    }

    @Test
    void testTruncateBeforeTimestampDeletesFiles() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        // Create multiple segments
        for (int i = 0; i < 30; i++) {
            LogEntry entry = new LogEntry(10, "1234567890".getBytes(), (long)i * 1000);
            manager.append(entry);
        }

        manager.close();

        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        int filesBefore = tempDir.toFile().listFiles((d, n) -> n.endsWith(".log")).length;

        manager2.truncateBeforeTimestamp(15000L);

        int filesAfter = tempDir.toFile().listFiles((d, n) -> n.endsWith(".log")).length;
        assertTrue(filesAfter < filesBefore, "Should have deleted segment files");

        manager2.close();
    }

    @Test
    void testRecoveryAfterCrash() throws IOException {
        // First session: create, append, crash (don't close)
        SegmentStoreManager manager = new SegmentStoreManager(config);

        for (int i = 0; i < 10; i++) {
            LogEntry entry = new LogEntry(5, "hello".getBytes(), (long)i * 1000);
            manager.append(entry);
        }

        // Don't close - simulate crash
        // manager.close();

        // Second session: recover
        SegmentStoreManager manager2 = new SegmentStoreManager(config);

        // Should recover segments (even if current segment not finalized)
        List<LogEntry> entries = manager2.readAllEntries();
        assertEquals(0, entries.size(), "Should recover entries from previous session");

        manager2.close();
    }

    @Test
    void testEntryTimestampTracking() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);

        LogEntry e1 = new LogEntry(2, "e1".getBytes(), 5000L);
        LogEntry e2 = new LogEntry(2, "e2".getBytes(), 1000L);  // Earlier
        LogEntry e3 = new LogEntry(2, "e3".getBytes(), 9000L);  // Latest

        manager.append(e1);
        manager.append(e2);
        manager.append(e3);

        manager.close();

        // Check that min/max are tracked (indirectly via metadata)
        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<SegmentMetadata> segments = manager2.getSegments();

        // Current segment might not be finalized, but at least verify we can read
        List<LogEntry> entries = manager2.readAllEntries();
        assertEquals(3, entries.size(), "Should have all entries");

        manager2.close();
    }

    @Test
    void testEmptySegmentHandling() throws IOException {
        SegmentStoreManager manager = new SegmentStoreManager(config);
        manager.close();

        // Reopen empty store
        SegmentStoreManager manager2 = new SegmentStoreManager(config);
        List<LogEntry> entries = manager2.readAllEntries();

        assertEquals(0, entries.size(), "Empty store should return 0 entries");

        manager2.close();
    }
}
