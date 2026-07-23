package io.writeahead.log.segments;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.segment.SegmentFooter;
import io.writeahead.log.models.segment.SegmentHeader;
import io.writeahead.log.models.segment.SegmentMetadata;
import io.writeahead.log.models.wal.WalMetadata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SegmentMetadataRecoveryTest {

    private Path tempDir;
    private SegmentMetadataRecovery recovery;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("wal-recovery-test-");
        recovery = new SegmentMetadataRecovery(tempDir.toString());
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
    void testRecoverEmptyDirectory() throws IOException {
        WalMetadata metadata = recovery.recover();

        assertEquals(0, metadata.segments().size(), "Should have no segments");
        assertEquals(1, metadata.nextSequence(), "Next sequence should be 1 (fresh start)");
        assertNull(metadata.lastActiveSegment(), "Last active segment should be null");
    }

    @Test
    void testRecoverNonExistentDirectory() throws IOException {
        String nonExistent = "/tmp/does-not-exist-" + System.currentTimeMillis();
        SegmentMetadataRecovery recoveryNonExistent = new SegmentMetadataRecovery(nonExistent);
        WalMetadata metadata = recoveryNonExistent.recover();

        assertEquals(0, metadata.segments().size(), "Should handle missing directory");
        assertEquals(1, metadata.nextSequence(), "Next sequence should default to 1");
    }

    @Test
    void testRecoverValidSegment() throws IOException {
        File segment = new File(tempDir.toFile(), "wal-2026-07-23-001.log");
        createValidSegmentFile(segment, 1, 1000L, 5000L, 100);

        WalMetadata metadata = recovery.recover();

        assertEquals(1, metadata.segments().size(), "Should recover 1 segment");
        assertEquals(2, metadata.nextSequence(), "Next sequence should be 2");
        assertEquals("wal-2026-07-23-001.log", metadata.lastActiveSegment(), "Last active segment should match");

        SegmentMetadata seg = metadata.segments().get(0);
        assertEquals(1, seg.sequenceNumber(), "Sequence should match");
        assertEquals(100, seg.entryCount(), "Entry count should match");
        assertEquals(1000L, seg.minTimestamp(), "Min timestamp should match");
        assertEquals(5000L, seg.maxTimestamp(), "Max timestamp should match");
    }

    @Test
    void testRecoverMultipleSegments() throws IOException {
        createValidSegmentFile(new File(tempDir.toFile(), "wal-001.log"), 1, 1000L, 2000L, 10);
        createValidSegmentFile(new File(tempDir.toFile(), "wal-002.log"), 2, 3000L, 4000L, 20);
        createValidSegmentFile(new File(tempDir.toFile(), "wal-003.log"), 3, 5000L, 6000L, 30);

        WalMetadata metadata = recovery.recover();

        assertEquals(3, metadata.segments().size(), "Should recover 3 segments");
        assertEquals(4, metadata.nextSequence(), "Next sequence should be 4");
        assertEquals("wal-003.log", metadata.lastActiveSegment(), "Last segment should be most recent");
    }

    @Test
    void testRecoverSkipsCorruptedHeader() throws IOException {
        File valid = new File(tempDir.toFile(), "wal-001.log");
        createValidSegmentFile(valid, 1, 1000L, 2000L, 10);

        File corrupted = new File(tempDir.toFile(), "wal-002.log");
        createSegmentFileWithCorruptedHeader(corrupted, 2);

        WalMetadata metadata = recovery.recover();

        assertEquals(1, metadata.segments().size(), "Should skip corrupted segment, recover 1");
        assertEquals(2, metadata.nextSequence(), "Next sequence based on valid segment only");
    }

    @Test
    void testRecoverSkipsCorruptedFooterChecksum() throws IOException {
        File valid = new File(tempDir.toFile(), "wal-001.log");
        createValidSegmentFile(valid, 1, 1000L, 2000L, 10);

        File corrupted = new File(tempDir.toFile(), "wal-002.log");
        createSegmentFileWithCorruptedFooter(corrupted, 2);

        WalMetadata metadata = recovery.recover();

        assertEquals(1, metadata.segments().size(), "Should skip footer corruption via CRC");
        assertEquals(2, metadata.nextSequence(), "Next sequence based on valid segment only");
    }

    @Test
    void testRecoverSkipsTooSmallFile() throws IOException {
        File valid = new File(tempDir.toFile(), "wal-001.log");
        createValidSegmentFile(valid, 1, 1000L, 2000L, 10);

        File tooSmall = new File(tempDir.toFile(), "wal-002.log");
        Files.write(tooSmall.toPath(), new byte[50]);

        WalMetadata metadata = recovery.recover();

        assertEquals(1, metadata.segments().size(), "Should skip too-small file");
    }

    @Test
    void testRecoverMaxSequenceNumber() throws IOException {
        createValidSegmentFile(new File(tempDir.toFile(), "wal-001.log"), 1, 1000L, 2000L, 10);
        createValidSegmentFile(new File(tempDir.toFile(), "wal-005.log"), 5, 3000L, 4000L, 20);
        createValidSegmentFile(new File(tempDir.toFile(), "wal-003.log"), 3, 5000L, 6000L, 30);

        WalMetadata metadata = recovery.recover();

        assertEquals(3, metadata.segments().size(), "Should recover all 3");
        assertEquals(6, metadata.nextSequence(), "Next sequence should be 5 + 1 (max + 1)");
    }

    @Test
    void testRecoverPreventsDuplicateSequences() throws IOException {
        createValidSegmentFile(new File(tempDir.toFile(), "wal-001.log"), 5, 1000L, 2000L, 10);

        WalMetadata metadata = recovery.recover();

        assertEquals(6, metadata.nextSequence(), "Next sequence should prevent duplicates");
    }

    @Test
    void testRecoverPreservesSortOrder() throws IOException {
        createValidSegmentFile(new File(tempDir.toFile(), "wal-003.log"), 3, 5000L, 6000L, 30);
        createValidSegmentFile(new File(tempDir.toFile(), "wal-001.log"), 1, 1000L, 2000L, 10);
        createValidSegmentFile(new File(tempDir.toFile(), "wal-002.log"), 2, 3000L, 4000L, 20);

        WalMetadata metadata = recovery.recover();

        assertEquals(3, metadata.segments().size(), "Should recover all 3");
        assertEquals(1, metadata.segments().get(0).sequenceNumber(), "First should be seq 1");
        assertEquals(2, metadata.segments().get(1).sequenceNumber(), "Second should be seq 2");
        assertEquals(3, metadata.segments().get(2).sequenceNumber(), "Third should be seq 3");
    }

    @Test
    void testRecoverWithMixedValidCorrupted() throws IOException {
        createValidSegmentFile(new File(tempDir.toFile(), "wal-001.log"), 1, 1000L, 2000L, 10);
        createSegmentFileWithCorruptedHeader(new File(tempDir.toFile(), "wal-002.log"), 2);
        createValidSegmentFile(new File(tempDir.toFile(), "wal-003.log"), 3, 3000L, 4000L, 20);
        createSegmentFileWithCorruptedFooter(new File(tempDir.toFile(), "wal-004.log"), 4);
        createValidSegmentFile(new File(tempDir.toFile(), "wal-005.log"), 5, 5000L, 6000L, 30);

        WalMetadata metadata = recovery.recover();

        assertEquals(3, metadata.segments().size(), "Should recover only 3 valid segments");
        assertEquals(6, metadata.nextSequence(), "Next sequence based on max (5) + 1");
        assertEquals("wal-005.log", metadata.lastActiveSegment(), "Last active should be the latest valid");
    }

    @Test
    void testRecoverDetectsCorruptedFooterData() throws IOException {
        // Verify that any footer corruption (marker or other fields) fails CRC
        File valid = new File(tempDir.toFile(), "wal-001.log");
        createValidSegmentFile(valid, 1, 1000L, 2000L, 10);

        File corruptedMarker = new File(tempDir.toFile(), "wal-002.log");
        createSegmentFileWithCorruptedMarker(corruptedMarker, 2);

        WalMetadata metadata = recovery.recover();

        assertEquals(1, metadata.segments().size(), "Should skip segment with corrupted marker");
    }

    // Helpers

    private void createValidSegmentFile(File file, long sequence, long minTs, long maxTs, int entryCount) throws IOException {
        SegmentHeader header = SegmentHeader.create(System.currentTimeMillis(), sequence);
        SegmentFooter footer = SegmentFooter.create(entryCount, minTs, maxTs);

        byte[] headerBytes = header.toBytes();
        byte[] footerBytes = footer.toBytes();
        byte[] entryRegion = new byte[100];

        byte[] combined = new byte[headerBytes.length + entryRegion.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, combined, 0, headerBytes.length);
        System.arraycopy(entryRegion, 0, combined, headerBytes.length, entryRegion.length);
        System.arraycopy(footerBytes, 0, combined, headerBytes.length + entryRegion.length, footerBytes.length);

        Files.write(file.toPath(), combined);
    }

    private void createSegmentFileWithCorruptedHeader(File file, long sequence) throws IOException {
        SegmentHeader header = SegmentHeader.create(System.currentTimeMillis(), sequence);
        byte[] headerBytes = header.toBytes();

        // Corrupt header (change magic byte)
        headerBytes[0] = (byte) 0xBB;

        SegmentFooter footer = SegmentFooter.create(10, 1000L, 2000L);
        byte[] footerBytes = footer.toBytes();
        byte[] entryRegion = new byte[100];

        byte[] combined = new byte[headerBytes.length + entryRegion.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, combined, 0, headerBytes.length);
        System.arraycopy(entryRegion, 0, combined, headerBytes.length, entryRegion.length);
        System.arraycopy(footerBytes, 0, combined, headerBytes.length + entryRegion.length, footerBytes.length);

        Files.write(file.toPath(), combined);
    }

    private void createSegmentFileWithCorruptedFooter(File file, long sequence) throws IOException {
        SegmentHeader header = SegmentHeader.create(System.currentTimeMillis(), sequence);
        byte[] headerBytes = header.toBytes();

        SegmentFooter footer = SegmentFooter.create(10, 1000L, 2000L);
        byte[] footerBytes = footer.toBytes();

        // Corrupt footer checksum (any bit flip makes CRC invalid)
        footerBytes[footerBytes.length - 1] = (byte) ~footerBytes[footerBytes.length - 1];

        byte[] entryRegion = new byte[100];

        byte[] combined = new byte[headerBytes.length + entryRegion.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, combined, 0, headerBytes.length);
        System.arraycopy(entryRegion, 0, combined, headerBytes.length, entryRegion.length);
        System.arraycopy(footerBytes, 0, combined, headerBytes.length + entryRegion.length, footerBytes.length);

        Files.write(file.toPath(), combined);
    }

    private void createSegmentFileWithCorruptedMarker(File file, long sequence) throws IOException {
        SegmentHeader header = SegmentHeader.create(System.currentTimeMillis(), sequence);
        byte[] headerBytes = header.toBytes();

        SegmentFooter footer = SegmentFooter.create(10, 1000L, 2000L);
        byte[] footerBytes = footer.toBytes();

        // Corrupt entryCount field (included in checksum)
        // This will fail CRC validation
        footerBytes[0] = (byte) ~footerBytes[0];

        byte[] entryRegion = new byte[100];

        byte[] combined = new byte[headerBytes.length + entryRegion.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, combined, 0, headerBytes.length);
        System.arraycopy(entryRegion, 0, combined, headerBytes.length, entryRegion.length);
        System.arraycopy(footerBytes, 0, combined, headerBytes.length + entryRegion.length, footerBytes.length);

        Files.write(file.toPath(), combined);
    }
}