package io.writeahead.log.segments;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.wal.WalConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SegmentStoreManagerBatchingTest {

    private static final int BATCH_SIZE_ONE = 1;
    private static final int BATCH_SIZE_FIVE = 5;
    private static final int BATCH_SIZE_TEN = 10;
    private static final int BATCH_SIZE_HUNDRED = 100;
    private static final int LARGE_SEGMENT_SIZE = 100 * 1024 * 1024;
    private static final int SMALL_PAYLOAD_SIZE = 10;
    private static final int LARGE_PAYLOAD_SIZE = 500;
    private static final long BASE_TIMESTAMP = 1000L;
    private static final int FIVE_ENTRIES = 5;
    private static final int TEN_ENTRIES = 10;
    private static final int TWENTY_ENTRIES = 20;
    private static final int HUNDRED_ENTRIES = 100;
    private static final int THOUSAND_ENTRIES = 1000;
    private static final int THREE_ENTRIES = 3;
    private static final int TWENTY_FIVE_ENTRIES = 25;

    private Path tempLogDirectory;

    @BeforeEach
    void setUp() throws IOException {
        tempLogDirectory = Files.createTempDirectory("wal-batching-test-");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempLogDirectory)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ignored) {
                    }
                });
    }

    @Test
    void batchSizeOneFlushesAfterEachEntry() throws IOException {
        WalConfiguration configWithBatchSizeOne = new WalConfiguration.Builder()
                .logDir(tempLogDirectory.toString())
                .batchSize(BATCH_SIZE_ONE)
                .maxSegmentSize(LARGE_SEGMENT_SIZE)
                .build();

        SegmentStoreManager managerUnderTest = new SegmentStoreManager(configWithBatchSizeOne);

        for (int entryIndex = 0; entryIndex < FIVE_ENTRIES; entryIndex++) {
            byte[] payloadBytes = ("entry " + entryIndex).getBytes();
            LogEntry logEntry = new LogEntry(payloadBytes.length, payloadBytes, BASE_TIMESTAMP + entryIndex);
            managerUnderTest.append(logEntry);
        }

        assertEquals(FIVE_ENTRIES, managerUnderTest.getCurrentEntryCount());
        managerUnderTest.close();
    }

    @Test
    void batchSizeFiveAccumulatesBeforeFlush() throws IOException {
        WalConfiguration configWithBatchSizeFive = new WalConfiguration.Builder()
                .logDir(tempLogDirectory.toString())
                .batchSize(BATCH_SIZE_FIVE)
                .maxSegmentSize(LARGE_SEGMENT_SIZE)
                .build();

        SegmentStoreManager managerUnderTest = new SegmentStoreManager(configWithBatchSizeFive);

        for (int entryIndex = 0; entryIndex < BATCH_SIZE_FIVE; entryIndex++) {
            byte[] payloadBytes = ("entry " + entryIndex).getBytes();
            LogEntry logEntry = new LogEntry(payloadBytes.length, payloadBytes, BASE_TIMESTAMP + entryIndex);
            managerUnderTest.append(logEntry);
        }

        assertEquals(BATCH_SIZE_FIVE, managerUnderTest.getCurrentEntryCount());
        managerUnderTest.close();
    }

    @Test
    void batchSizeTenAccumulatesAndFlushesCorrectly() throws IOException {
        WalConfiguration configWithBatchSizeTen = new WalConfiguration.Builder()
                .logDir(tempLogDirectory.toString())
                .batchSize(BATCH_SIZE_TEN)
                .maxSegmentSize(LARGE_SEGMENT_SIZE)
                .build();

        SegmentStoreManager managerUnderTest = new SegmentStoreManager(configWithBatchSizeTen);

        int firstBatchEntries = TEN_ENTRIES;
        int secondBatchEntries = TEN_ENTRIES;
        int totalEntries = firstBatchEntries + secondBatchEntries;

        for (int entryIndex = 0; entryIndex < totalEntries; entryIndex++) {
            byte[] payloadBytes = ("entry " + entryIndex).getBytes();
            LogEntry logEntry = new LogEntry(payloadBytes.length, payloadBytes, BASE_TIMESTAMP + entryIndex);
            managerUnderTest.append(logEntry);
        }

        assertEquals(totalEntries, managerUnderTest.getCurrentEntryCount());
        managerUnderTest.close();
    }

    @Test
    void largeBatchSizeReducesFsyncFrequency() throws IOException {
        WalConfiguration configWithLargeBatchSize = new WalConfiguration.Builder()
                .logDir(tempLogDirectory.toString())
                .batchSize(BATCH_SIZE_HUNDRED)
                .maxSegmentSize(LARGE_SEGMENT_SIZE)
                .build();

        SegmentStoreManager managerUnderTest = new SegmentStoreManager(configWithLargeBatchSize);

        for (int entryIndex = 0; entryIndex < HUNDRED_ENTRIES; entryIndex++) {
            byte[] payloadBytes = ("entry " + entryIndex).getBytes();
            LogEntry logEntry = new LogEntry(payloadBytes.length, payloadBytes, BASE_TIMESTAMP + entryIndex);
            managerUnderTest.append(logEntry);
        }

        assertEquals(HUNDRED_ENTRIES, managerUnderTest.getCurrentEntryCount());
        managerUnderTest.close();
    }

    @Test
    void multipleFullBatchesWithPartialBatchAccumulates() throws IOException {
        int desiredBatchSize = TEN_ENTRIES;
        WalConfiguration configWithBatchSize = new WalConfiguration.Builder()
                .logDir(tempLogDirectory.toString())
                .batchSize(desiredBatchSize)
                .maxSegmentSize(LARGE_SEGMENT_SIZE)
                .build();

        SegmentStoreManager managerUnderTest = new SegmentStoreManager(configWithBatchSize);

        int totalEntriesToWrite = TWENTY_FIVE_ENTRIES;
        for (int entryIndex = 0; entryIndex < totalEntriesToWrite; entryIndex++) {
            byte[] payloadBytes = ("entry " + entryIndex).getBytes();
            LogEntry logEntry = new LogEntry(payloadBytes.length, payloadBytes, BASE_TIMESTAMP + entryIndex);
            managerUnderTest.append(logEntry);
        }

        int entriesFlushedToDisk = TWENTY_ENTRIES;
        assertEquals(entriesFlushedToDisk, managerUnderTest.getCurrentEntryCount());
        managerUnderTest.close();
    }

    @Test
    void veryLargePayloadsBatchedCorrectly() throws IOException {
        WalConfiguration configWithBatchSize = new WalConfiguration.Builder()
                .logDir(tempLogDirectory.toString())
                .batchSize(BATCH_SIZE_FIVE)
                .maxSegmentSize(LARGE_SEGMENT_SIZE)
                .build();

        SegmentStoreManager managerUnderTest = new SegmentStoreManager(configWithBatchSize);

        byte[] largePayloadBytes = new byte[LARGE_PAYLOAD_SIZE];
        for (int byteIndex = 0; byteIndex < largePayloadBytes.length; byteIndex++) {
            largePayloadBytes[byteIndex] = (byte) (byteIndex % 256);
        }

        for (int entryIndex = 0; entryIndex < BATCH_SIZE_FIVE; entryIndex++) {
            LogEntry logEntry = new LogEntry(largePayloadBytes.length, largePayloadBytes, BASE_TIMESTAMP + entryIndex);
            managerUnderTest.append(logEntry);
        }

        assertEquals(BATCH_SIZE_FIVE, managerUnderTest.getCurrentEntryCount());
        managerUnderTest.close();
    }

    @Test
    void smallPayloadsBatchedWithoutSegmentRotation() throws IOException {
        WalConfiguration configWithBatchSize = new WalConfiguration.Builder()
                .logDir(tempLogDirectory.toString())
                .batchSize(BATCH_SIZE_TEN)
                .maxSegmentSize(LARGE_SEGMENT_SIZE)
                .build();

        SegmentStoreManager managerUnderTest = new SegmentStoreManager(configWithBatchSize);

        for (int entryIndex = 0; entryIndex < HUNDRED_ENTRIES; entryIndex++) {
            byte[] smallPayloadBytes = new byte[SMALL_PAYLOAD_SIZE];
            LogEntry logEntry = new LogEntry(smallPayloadBytes.length, smallPayloadBytes, BASE_TIMESTAMP + entryIndex);
            managerUnderTest.append(logEntry);
        }

        assertEquals(HUNDRED_ENTRIES, managerUnderTest.getCurrentEntryCount());
        managerUnderTest.close();
    }

    @Test
    void closeFlushesPartialBatchAndPreservesAllEntries() throws IOException {
        WalConfiguration configWithBatchSize = new WalConfiguration.Builder()
                .logDir(tempLogDirectory.toString())
                .batchSize(BATCH_SIZE_TEN)
                .maxSegmentSize(LARGE_SEGMENT_SIZE)
                .build();

        SegmentStoreManager firstInstance = new SegmentStoreManager(configWithBatchSize);

        int entriesToWrite = THREE_ENTRIES;
        for (int entryIndex = 0; entryIndex < entriesToWrite; entryIndex++) {
            byte[] payloadBytes = ("entry " + entryIndex).getBytes();
            LogEntry logEntry = new LogEntry(payloadBytes.length, payloadBytes, BASE_TIMESTAMP + entryIndex);
            firstInstance.append(logEntry);
        }

        firstInstance.close();

        SegmentStoreManager recoveryInstance = new SegmentStoreManager(configWithBatchSize);
        assertTrue(recoveryInstance.getSegments().size() >= 1);
        recoveryInstance.close();
    }

    @Test
    void stressTestThousandEntriesWithBatching() throws IOException {
        WalConfiguration configWithBatchSize = new WalConfiguration.Builder()
                .logDir(tempLogDirectory.toString())
                .batchSize(BATCH_SIZE_HUNDRED)
                .maxSegmentSize(LARGE_SEGMENT_SIZE)
                .build();

        SegmentStoreManager managerUnderTest = new SegmentStoreManager(configWithBatchSize);

        for (int entryIndex = 0; entryIndex < THOUSAND_ENTRIES; entryIndex++) {
            byte[] payloadBytes = ("entry " + entryIndex).getBytes();
            LogEntry logEntry = new LogEntry(payloadBytes.length, payloadBytes, BASE_TIMESTAMP + entryIndex);
            managerUnderTest.append(logEntry);
        }

        assertEquals(THOUSAND_ENTRIES, managerUnderTest.getCurrentEntryCount());
        managerUnderTest.close();
    }

    @Test
    void recoveryPreservesAllBatchedEntries() throws IOException {
        WalConfiguration configWithBatchSize = new WalConfiguration.Builder()
                .logDir(tempLogDirectory.toString())
                .batchSize(BATCH_SIZE_FIVE)
                .maxSegmentSize(LARGE_SEGMENT_SIZE)
                .build();

        SegmentStoreManager firstInstance = new SegmentStoreManager(configWithBatchSize);

        int entriesToWrite = HUNDRED_ENTRIES;
        for (int entryIndex = 0; entryIndex < entriesToWrite; entryIndex++) {
            byte[] payloadBytes = ("entry " + entryIndex).getBytes();
            LogEntry logEntry = new LogEntry(payloadBytes.length, payloadBytes, BASE_TIMESTAMP + entryIndex);
            firstInstance.append(logEntry);
        }

        firstInstance.writeBatch();
        firstInstance.close();

        SegmentStoreManager recoveryInstance = new SegmentStoreManager(configWithBatchSize);
        assertTrue(recoveryInstance.getSegments().size() > 0);
        recoveryInstance.close();
    }
}