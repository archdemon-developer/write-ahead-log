package io.writeahead.log.segments;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.wal.WalMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SegmentMetadataRecoveryTest {

    private static final long FIRST_SEGMENT_SEQUENCE = 1L;
    private static final long SECOND_SEGMENT_SEQUENCE = 2L;
    private static final long THIRD_SEGMENT_SEQUENCE = 3L;
    private static final long FIRST_ENTRY_TIMESTAMP = 1000L;
    private static final long SECOND_ENTRY_TIMESTAMP = 2000L;
    private static final long THIRD_ENTRY_TIMESTAMP = 3000L;
    private static final long FOURTH_ENTRY_TIMESTAMP = 4000L;
    private static final long FIFTH_ENTRY_TIMESTAMP = 5000L;
    private static final long SIXTH_ENTRY_TIMESTAMP = 6000L;
    private static final int FIRST_ENTRY_COUNT = 100;
    private static final int SECOND_ENTRY_COUNT = 200;
    private static final int THIRD_ENTRY_COUNT = 300;
    private static final int ZERO_SEGMENTS = 0;
    private static final int SINGLE_SEGMENT = 1;
    private static final int TWO_SEGMENTS = 2;
    private static final int THREE_SEGMENTS = 3;

    private Path tempLogDirectory;
    private SegmentMetadataRecovery recoveryUnderTest;

    @BeforeEach
    void setUp() throws IOException {
        tempLogDirectory = Files.createTempDirectory("segment-recovery-test-");
        recoveryUnderTest = new SegmentMetadataRecovery(tempLogDirectory.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempLogDirectory)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ignored) {
                    }
                });
    }

    @Test
    void recoverFromEmptyDirectoryReturnsEmptySegmentList() throws IOException {
        WalMetadata recoveredMetadata = recoveryUnderTest.recover();

        assertEquals(ZERO_SEGMENTS, recoveredMetadata.segments().size());
        assertEquals(1L, recoveredMetadata.nextSequence());
    }

    @Test
    void recoverSingleValidSegmentMetadata() throws IOException {
        SegmentLifecycleManager lifecycleManager = new SegmentLifecycleManager(tempLogDirectory.toString());
        var segmentStream = lifecycleManager.createNewSegment(FIRST_SEGMENT_SEQUENCE);
        lifecycleManager.finalizeSegment(segmentStream, FIRST_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

        WalMetadata recoveredMetadata = recoveryUnderTest.recover();

        assertEquals(SINGLE_SEGMENT, recoveredMetadata.segments().size());
        assertEquals(FIRST_SEGMENT_SEQUENCE, recoveredMetadata.segments().getFirst().sequenceNumber());
        assertEquals(FIRST_ENTRY_COUNT, recoveredMetadata.segments().getFirst().entryCount());
        assertEquals(FIRST_ENTRY_TIMESTAMP, recoveredMetadata.segments().getFirst().minTimestamp());
        assertEquals(SECOND_ENTRY_TIMESTAMP, recoveredMetadata.segments().getFirst().maxTimestamp());
    }

    @Test
    void recoverMultipleSegmentMetadata() throws IOException {
        SegmentLifecycleManager lifecycleManager = new SegmentLifecycleManager(tempLogDirectory.toString());

        var firstSegmentStream = lifecycleManager.createNewSegment(FIRST_SEGMENT_SEQUENCE);
        lifecycleManager.finalizeSegment(firstSegmentStream, FIRST_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

        var secondSegmentStream = lifecycleManager.createNewSegment(SECOND_SEGMENT_SEQUENCE);
        lifecycleManager.finalizeSegment(secondSegmentStream, SECOND_ENTRY_COUNT, THIRD_ENTRY_TIMESTAMP, FOURTH_ENTRY_TIMESTAMP);

        var thirdSegmentStream = lifecycleManager.createNewSegment(THIRD_SEGMENT_SEQUENCE);
        lifecycleManager.finalizeSegment(thirdSegmentStream, THIRD_ENTRY_COUNT, FIFTH_ENTRY_TIMESTAMP, SIXTH_ENTRY_TIMESTAMP);

        WalMetadata recoveredMetadata = recoveryUnderTest.recover();

        assertEquals(THREE_SEGMENTS, recoveredMetadata.segments().size());
        assertEquals(FIRST_SEGMENT_SEQUENCE, recoveredMetadata.segments().get(0).sequenceNumber());
        assertEquals(SECOND_SEGMENT_SEQUENCE, recoveredMetadata.segments().get(1).sequenceNumber());
        assertEquals(THIRD_SEGMENT_SEQUENCE, recoveredMetadata.segments().get(2).sequenceNumber());
    }

    @Test
    void recoverPreservesSegmentOrderBySequence() throws IOException {
        SegmentLifecycleManager lifecycleManager = new SegmentLifecycleManager(tempLogDirectory.toString());

        var firstSegmentStream = lifecycleManager.createNewSegment(FIRST_SEGMENT_SEQUENCE);
        lifecycleManager.finalizeSegment(firstSegmentStream, FIRST_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

        var secondSegmentStream = lifecycleManager.createNewSegment(SECOND_SEGMENT_SEQUENCE);
        lifecycleManager.finalizeSegment(secondSegmentStream, SECOND_ENTRY_COUNT, THIRD_ENTRY_TIMESTAMP, FOURTH_ENTRY_TIMESTAMP);

        WalMetadata recoveredMetadata = recoveryUnderTest.recover();

        assertTrue(recoveredMetadata.segments().get(0).sequenceNumber() <= recoveredMetadata.segments().get(1).sequenceNumber());
    }

    @Test
    void recoverCalculatesNextSequenceAsMaxPlusOne() throws IOException {
        SegmentLifecycleManager lifecycleManager = new SegmentLifecycleManager(tempLogDirectory.toString());

        var firstSegmentStream = lifecycleManager.createNewSegment(FIRST_SEGMENT_SEQUENCE);
        lifecycleManager.finalizeSegment(firstSegmentStream, FIRST_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

        var secondSegmentStream = lifecycleManager.createNewSegment(SECOND_SEGMENT_SEQUENCE);
        lifecycleManager.finalizeSegment(secondSegmentStream, SECOND_ENTRY_COUNT, THIRD_ENTRY_TIMESTAMP, FOURTH_ENTRY_TIMESTAMP);

        var thirdSegmentStream = lifecycleManager.createNewSegment(THIRD_SEGMENT_SEQUENCE);
        lifecycleManager.finalizeSegment(thirdSegmentStream, THIRD_ENTRY_COUNT, FIFTH_ENTRY_TIMESTAMP, SIXTH_ENTRY_TIMESTAMP);

        WalMetadata recoveredMetadata = recoveryUnderTest.recover();

        long expectedNextSequence = THIRD_SEGMENT_SEQUENCE + 1;
        assertEquals(expectedNextSequence, recoveredMetadata.nextSequence());
    }

    @Test
    void recoverHandlesZeroEntryCountSegment() throws IOException {
        SegmentLifecycleManager lifecycleManager = new SegmentLifecycleManager(tempLogDirectory.toString());
        var segmentStream = lifecycleManager.createNewSegment(FIRST_SEGMENT_SEQUENCE);
        lifecycleManager.finalizeSegment(segmentStream, 0, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

        WalMetadata recoveredMetadata = recoveryUnderTest.recover();

        assertEquals(SINGLE_SEGMENT, recoveredMetadata.segments().size());
        assertEquals(0, recoveredMetadata.segments().getFirst().entryCount());
    }

    @Test
    void recoverReturnsLastActiveSegmentFilename() throws IOException {
        SegmentLifecycleManager lifecycleManager = new SegmentLifecycleManager(tempLogDirectory.toString());

        var firstSegmentStream = lifecycleManager.createNewSegment(FIRST_SEGMENT_SEQUENCE);
        lifecycleManager.finalizeSegment(firstSegmentStream, FIRST_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

        var secondSegmentStream = lifecycleManager.createNewSegment(SECOND_SEGMENT_SEQUENCE);
        lifecycleManager.finalizeSegment(secondSegmentStream, SECOND_ENTRY_COUNT, THIRD_ENTRY_TIMESTAMP, FOURTH_ENTRY_TIMESTAMP);

        WalMetadata recoveredMetadata = recoveryUnderTest.recover();

        assertNotNull(recoveredMetadata.lastActiveSegment());
        assertTrue(recoveredMetadata.lastActiveSegment().contains("000002"));
    }

    @Test
    void recoverDeterministicallyRecreatesMetadataFromHeaders() throws IOException {
        SegmentLifecycleManager lifecycleManager = new SegmentLifecycleManager(tempLogDirectory.toString());
        var segmentStream = lifecycleManager.createNewSegment(FIRST_SEGMENT_SEQUENCE);
        lifecycleManager.finalizeSegment(segmentStream, FIRST_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

        WalMetadata firstRecovery = recoveryUnderTest.recover();
        WalMetadata secondRecovery = recoveryUnderTest.recover();

        assertEquals(firstRecovery.segments().size(), secondRecovery.segments().size());
        assertEquals(
                firstRecovery.segments().getFirst().sequenceNumber(),
                secondRecovery.segments().getFirst().sequenceNumber()
        );
    }
}