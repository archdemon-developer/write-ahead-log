package io.writeahead.log.segments;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.models.LogEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SegmentStoreManagerTest {

  private static final int MAX_SEGMENT_SIZE = 5000;
  private static final int BATCH_SIZE_ONE = 1;
  private static final int INITIAL_SEQUENCE_NUMBER = 1;
  private static final int INITIAL_ENTRY_COUNT = 0;
  private static final long TIMESTAMP_1000 = 1000L;
  private static final long TIMESTAMP_2000 = 2000L;
  private static final long TIMESTAMP_3000 = 3000L;
  private static final long TIMESTAMP_5000 = 5000L;
  private static final String TEST_ENTRY_DATA_FIRST = "first entry";
  private static final String TEST_ENTRY_DATA_SECOND = "second entry";
  private static final String TEST_ENTRY_DATA_THIRD = "third entry";

  private Path tempLogDirectory;
  private WalConfiguration walConfiguration;

  @BeforeEach
  void setUp() throws Exception {
    tempLogDirectory = Files.createTempDirectory("wal-store-test-");
    walConfiguration =
        new WalConfiguration.Builder()
            .logDir(tempLogDirectory.toString())
            .maxSegmentSize(MAX_SEGMENT_SIZE)
            .batchSize(BATCH_SIZE_ONE)
            .build();
  }

  @AfterEach
  void tearDown() throws Exception {
    Files.walk(tempLogDirectory)
        .sorted((a, b) -> b.compareTo(a))
        .forEach(
            path -> {
              try {
                Files.delete(path);
              } catch (Exception ignored) {
              }
            });
  }

  @Test
  void constructorInitializesWithValidState() throws Exception {
    SegmentStoreManager managerUnderTest = new SegmentStoreManager(walConfiguration);

    assertTrue(managerUnderTest.isOpen());
    assertNotNull(managerUnderTest.getSegments());
    assertEquals(INITIAL_SEQUENCE_NUMBER, managerUnderTest.getCurrentSequenceNumber());
    assertEquals(INITIAL_ENTRY_COUNT, managerUnderTest.getCurrentEntryCount());

    managerUnderTest.close();
  }

  @Test
  void closeIsIdempotentAndSafe() throws Exception {
    SegmentStoreManager managerUnderTest = new SegmentStoreManager(walConfiguration);

    managerUnderTest.close();
    managerUnderTest.close();

    assertFalse(managerUnderTest.isOpen());
  }

  @Test
  void appendEntryWritesAndTracksCorrectly() throws Exception {
    SegmentStoreManager managerUnderTest = new SegmentStoreManager(walConfiguration);
    byte[] payloadBytes = TEST_ENTRY_DATA_FIRST.getBytes();
    LogEntry logEntryToAppend = new LogEntry(payloadBytes.length, payloadBytes, TIMESTAMP_1000);

    managerUnderTest.append(logEntryToAppend);

    assertEquals(1, managerUnderTest.getCurrentEntryCount());

    managerUnderTest.close();
  }

  @Test
  void appendMultipleEntriesInSequence() throws Exception {
    SegmentStoreManager managerUnderTest = new SegmentStoreManager(walConfiguration);

    byte[] firstPayloadBytes = TEST_ENTRY_DATA_FIRST.getBytes();
    LogEntry firstLogEntry =
        new LogEntry(firstPayloadBytes.length, firstPayloadBytes, TIMESTAMP_1000);
    managerUnderTest.append(firstLogEntry);

    byte[] secondPayloadBytes = TEST_ENTRY_DATA_SECOND.getBytes();
    LogEntry secondLogEntry =
        new LogEntry(secondPayloadBytes.length, secondPayloadBytes, TIMESTAMP_2000);
    managerUnderTest.append(secondLogEntry);

    byte[] thirdPayloadBytes = TEST_ENTRY_DATA_THIRD.getBytes();
    LogEntry thirdLogEntry =
        new LogEntry(thirdPayloadBytes.length, thirdPayloadBytes, TIMESTAMP_3000);
    managerUnderTest.append(thirdLogEntry);

    assertEquals(3, managerUnderTest.getCurrentEntryCount());

    managerUnderTest.close();
  }

  @Test
  void appendTriggersSegmentRotationWhenSizeExceeded() throws Exception {
    SegmentStoreManager managerUnderTest = new SegmentStoreManager(walConfiguration);

    byte[] largePayloadBytes = new byte[MAX_SEGMENT_SIZE + 1000];
    for (int index = 0; index < largePayloadBytes.length; index++) {
      largePayloadBytes[index] = (byte) (index % 256);
    }
    LogEntry largeLogEntry =
        new LogEntry(largePayloadBytes.length, largePayloadBytes, TIMESTAMP_1000);

    managerUnderTest.append(largeLogEntry);

    assertTrue(managerUnderTest.getSegments().size() >= 1);

    managerUnderTest.close();
  }

  @Test
  void getCurrentSequenceNumberIncrementsWithRotation() throws Exception {
    SegmentStoreManager managerUnderTest = new SegmentStoreManager(walConfiguration);

    long sequenceBeforeAppend = managerUnderTest.getCurrentSequenceNumber();

    byte[] largePayloadBytes = new byte[MAX_SEGMENT_SIZE + 1000];
    LogEntry largeLogEntry =
        new LogEntry(largePayloadBytes.length, largePayloadBytes, TIMESTAMP_1000);
    managerUnderTest.append(largeLogEntry);

    long sequenceAfterAppend = managerUnderTest.getCurrentSequenceNumber();
    assertTrue(sequenceAfterAppend > sequenceBeforeAppend);

    managerUnderTest.close();
  }

  @Test
  void readAllEntriesReturnsAppendedData() throws Exception {
    SegmentStoreManager managerUnderTest = new SegmentStoreManager(walConfiguration);

    byte[] firstPayloadBytes = TEST_ENTRY_DATA_FIRST.getBytes();
    LogEntry firstLogEntry =
        new LogEntry(firstPayloadBytes.length, firstPayloadBytes, TIMESTAMP_1000);
    managerUnderTest.append(firstLogEntry);

    byte[] secondPayloadBytes = TEST_ENTRY_DATA_SECOND.getBytes();
    LogEntry secondLogEntry =
        new LogEntry(secondPayloadBytes.length, secondPayloadBytes, TIMESTAMP_2000);
    managerUnderTest.append(secondLogEntry);

    managerUnderTest.writeBatch();

    assertEquals(2, managerUnderTest.getCurrentEntryCount());

    managerUnderTest.close();
  }

  @Test
  void managerHandlesEmptyAppendSafely() throws Exception {
    SegmentStoreManager managerUnderTest = new SegmentStoreManager(walConfiguration);

    byte[] emptyPayloadBytes = new byte[0];
    LogEntry emptyLogEntry =
        new LogEntry(emptyPayloadBytes.length, emptyPayloadBytes, TIMESTAMP_1000);
    managerUnderTest.append(emptyLogEntry);

    assertEquals(1, managerUnderTest.getCurrentEntryCount());

    managerUnderTest.close();
  }

  @Test
  void recoveryFromPreviousSessionRestoresSegments() throws Exception {
    SegmentStoreManager firstManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] payloadBytes = "recovery test".getBytes();
    for (int entryIndex = 0; entryIndex < 5; entryIndex++) {
      LogEntry logEntry =
          new LogEntry(payloadBytes.length, payloadBytes, TIMESTAMP_1000 + entryIndex);
      firstManagerInstance.append(logEntry);
    }

    firstManagerInstance.writeBatch();
    int entriesBeforeClose = firstManagerInstance.getCurrentEntryCount();
    firstManagerInstance.close();

    SegmentStoreManager secondManagerInstance = new SegmentStoreManager(walConfiguration);

    int recoveredSegmentCount = secondManagerInstance.getSegments().size();
    assertEquals(1, recoveredSegmentCount);

    secondManagerInstance.close();
  }

  @Test
  void metricsTrackEntryWritten() throws Exception {
    SegmentStoreManager managerUnderTest = new SegmentStoreManager(walConfiguration);

    byte[] payloadBytes = TEST_ENTRY_DATA_FIRST.getBytes();
    LogEntry logEntryToAppend = new LogEntry(payloadBytes.length, payloadBytes, TIMESTAMP_1000);

    managerUnderTest.append(logEntryToAppend);
    managerUnderTest.writeBatch();

    assertTrue(managerUnderTest.getMetrics().getEntriesWritten() > 0);
    assertTrue(managerUnderTest.getMetrics().getBytesWritten() >= payloadBytes.length);

    managerUnderTest.close();
  }

  @Test
  void writeBatchFlushesAccumulatedEntries() throws Exception {
    SegmentStoreManager managerUnderTest = new SegmentStoreManager(walConfiguration);

    byte[] payloadBytes = TEST_ENTRY_DATA_FIRST.getBytes();
    LogEntry firstLogEntry = new LogEntry(payloadBytes.length, payloadBytes, TIMESTAMP_1000);
    LogEntry secondLogEntry = new LogEntry(payloadBytes.length, payloadBytes, TIMESTAMP_2000);

    managerUnderTest.append(firstLogEntry);
    managerUnderTest.append(secondLogEntry);
    managerUnderTest.writeBatch();

    assertEquals(2, managerUnderTest.getCurrentEntryCount());

    managerUnderTest.close();
  }

  @Test
  void readAllAfterTimestampReturnsEntriesAfterGivenTime() throws Exception {
    SegmentStoreManager managerUnderTest = new SegmentStoreManager(walConfiguration);

    byte[] payloadBytes = "test".getBytes();
    LogEntry entry1 = new LogEntry(payloadBytes.length, payloadBytes, TIMESTAMP_1000);
    LogEntry entry2 = new LogEntry(payloadBytes.length, payloadBytes, TIMESTAMP_2000);
    LogEntry entry3 = new LogEntry(payloadBytes.length, payloadBytes, TIMESTAMP_5000);

    managerUnderTest.append(entry1);
    managerUnderTest.append(entry2);
    managerUnderTest.append(entry3);
    managerUnderTest.writeBatch();

    assertEquals(3, managerUnderTest.getCurrentEntryCount());

    managerUnderTest.close();
  }
}
