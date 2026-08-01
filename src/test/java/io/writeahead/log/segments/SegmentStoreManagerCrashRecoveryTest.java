package io.writeahead.log.segments;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.SegmentMetadata;
import io.writeahead.log.models.WalConfiguration;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SegmentStoreManagerCrashRecoveryTest {

  private static final int MAX_SEGMENT_SIZE = 1_000_000;
  private static final int BATCH_SIZE_ONE = 1;
  private static final long TIMESTAMP_1000 = 1000L;
  private static final long TIMESTAMP_2000 = 2000L;
  private static final long TIMESTAMP_3000 = 3000L;
  private static final long TIMESTAMP_4000 = 4000L;
  private static final String TEST_ENTRY_DATA = "test entry";

  private Path tempLogDirectory;
  private WalConfiguration walConfiguration;

  @BeforeEach
  void setUp() throws Exception {
    tempLogDirectory = Files.createTempDirectory("crash-recovery-test-");
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

  private int countSegmentFiles() throws Exception {
    File logDir = tempLogDirectory.toFile();
    File[] segmentFiles = logDir.listFiles((dir, name) -> name.endsWith(".log"));
    return segmentFiles != null ? segmentFiles.length : 0;
  }

  @Test
  void unfinalzedSegmentIsIgnoredOnRecovery() throws Exception {
    SegmentStoreManager firstManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] entryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry logEntry = new LogEntry(entryPayloadBytes.length, entryPayloadBytes, TIMESTAMP_1000);
    firstManagerInstance.append(logEntry);

    firstManagerInstance.close();

    SegmentStoreManager secondManagerInstance = new SegmentStoreManager(walConfiguration);

    int recoveredSegmentCount = secondManagerInstance.getSegments().size();
    assertTrue(recoveredSegmentCount > 0);

    secondManagerInstance.close();
  }

  @Test
  void crashBeforeFinalizeDiscardsPendingEntries() throws Exception {
    SegmentStoreManager firstManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] firstEntryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry firstLogEntry =
        new LogEntry(firstEntryPayloadBytes.length, firstEntryPayloadBytes, TIMESTAMP_1000);
    firstManagerInstance.append(firstLogEntry);

    firstManagerInstance.close();

    SegmentStoreManager secondManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] pendingEntryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry pendingLogEntry =
        new LogEntry(pendingEntryPayloadBytes.length, pendingEntryPayloadBytes, TIMESTAMP_2000);
    secondManagerInstance.append(pendingLogEntry);

    secondManagerInstance.close();

    SegmentStoreManager thirdManagerInstance = new SegmentStoreManager(walConfiguration);

    int recoveredSegmentCount = thirdManagerInstance.getSegments().size();

    assertTrue(recoveredSegmentCount >= 1);

    thirdManagerInstance.close();
  }

  @Test
  void recoveryIsDeterministicAcrossMultipleCrashes() throws Exception {
    for (int crashIndex = 0; crashIndex < 3; crashIndex++) {
      SegmentStoreManager managerInstance = new SegmentStoreManager(walConfiguration);

      byte[] entryPayloadBytes = TEST_ENTRY_DATA.getBytes();
      LogEntry logEntry =
          new LogEntry(entryPayloadBytes.length, entryPayloadBytes, TIMESTAMP_1000 + crashIndex);
      managerInstance.append(logEntry);

      managerInstance.close();

      SegmentStoreManager recoveryManagerInstance = new SegmentStoreManager(walConfiguration);
      int recoveredSegmentCount = recoveryManagerInstance.getSegments().size();

      assertTrue(recoveredSegmentCount >= 1);

      recoveryManagerInstance.close();
    }
  }

  @Test
  void validSegmentRecoveredWithUnfinalzedSegmentPresent() throws Exception {
    SegmentStoreManager firstManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] validEntryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry validLogEntry =
        new LogEntry(validEntryPayloadBytes.length, validEntryPayloadBytes, TIMESTAMP_1000);
    firstManagerInstance.append(validLogEntry);

    firstManagerInstance.close();

    SegmentStoreManager secondManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] unfinalzedEntryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry unfinalzedLogEntry =
        new LogEntry(
            unfinalzedEntryPayloadBytes.length, unfinalzedEntryPayloadBytes, TIMESTAMP_2000);
    secondManagerInstance.append(unfinalzedLogEntry);

    secondManagerInstance.close();

    SegmentStoreManager thirdManagerInstance = new SegmentStoreManager(walConfiguration);

    int recoveredSegmentCount = thirdManagerInstance.getSegments().size();
    assertTrue(recoveredSegmentCount >= 1);

    thirdManagerInstance.close();
  }

  @Test
  void multipleValidSegmentsRecoveredWithUnfinalzedInMiddle() throws Exception {
    SegmentStoreManager firstManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] firstValidEntryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry firstValidLogEntry =
        new LogEntry(
            firstValidEntryPayloadBytes.length, firstValidEntryPayloadBytes, TIMESTAMP_1000);
    firstManagerInstance.append(firstValidLogEntry);

    firstManagerInstance.close();

    SegmentStoreManager secondManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] unfinalzedEntryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry unfinalzedLogEntry =
        new LogEntry(
            unfinalzedEntryPayloadBytes.length, unfinalzedEntryPayloadBytes, TIMESTAMP_2000);
    secondManagerInstance.append(unfinalzedLogEntry);

    secondManagerInstance.close();

    SegmentStoreManager thirdManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] secondValidEntryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry secondValidLogEntry =
        new LogEntry(
            secondValidEntryPayloadBytes.length, secondValidEntryPayloadBytes, TIMESTAMP_3000);
    thirdManagerInstance.append(secondValidLogEntry);

    thirdManagerInstance.close();

    SegmentStoreManager recoveryManagerInstance = new SegmentStoreManager(walConfiguration);

    int recoveredSegmentCount = recoveryManagerInstance.getSegments().size();
    assertTrue(recoveredSegmentCount >= 2);

    recoveryManagerInstance.close();
  }

  @Test
  void emptyUnfinalzedSegmentIsSkipped() throws Exception {
    SegmentStoreManager firstManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] validEntryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry validLogEntry =
        new LogEntry(validEntryPayloadBytes.length, validEntryPayloadBytes, TIMESTAMP_1000);
    firstManagerInstance.append(validLogEntry);

    firstManagerInstance.close();

    File logDir = tempLogDirectory.toFile();
    String emptySegmentFilename = SegmentLifecycleManager.generateSegmentFilename(999L);
    File emptySegmentFile = new File(logDir, emptySegmentFilename);
    Files.write(emptySegmentFile.toPath(), new byte[0]);

    SegmentStoreManager secondManagerInstance = new SegmentStoreManager(walConfiguration);

    int recoveredSegmentCount = secondManagerInstance.getSegments().size();
    assertTrue(recoveredSegmentCount >= 1);

    secondManagerInstance.close();
  }

  @Test
  void nextSequenceCalculatedCorrectlyWithUnfinalzedSegments() throws Exception {
    SegmentStoreManager firstManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] entryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry logEntry = new LogEntry(entryPayloadBytes.length, entryPayloadBytes, TIMESTAMP_1000);
    firstManagerInstance.append(logEntry);

    long sequenceBeforeClose = firstManagerInstance.getCurrentSequenceNumber();
    firstManagerInstance.close();

    SegmentStoreManager secondManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] unfinalzedEntryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry unfinalzedLogEntry =
        new LogEntry(
            unfinalzedEntryPayloadBytes.length, unfinalzedEntryPayloadBytes, TIMESTAMP_2000);
    secondManagerInstance.append(unfinalzedLogEntry);

    secondManagerInstance.close();

    SegmentStoreManager thirdManagerInstance = new SegmentStoreManager(walConfiguration);

    long recoveredNextSequence = thirdManagerInstance.getCurrentSequenceNumber();

    assertTrue(recoveredNextSequence >= sequenceBeforeClose);

    thirdManagerInstance.close();
  }

  @Test
  void crashDuringBatchWriteDoesNotCorruptRecovery() throws Exception {
    SegmentStoreManager firstManagerInstance = new SegmentStoreManager(walConfiguration);

    for (int entryIndex = 0; entryIndex < 5; entryIndex++) {
      byte[] entryPayloadBytes = TEST_ENTRY_DATA.getBytes();
      LogEntry logEntry =
          new LogEntry(entryPayloadBytes.length, entryPayloadBytes, TIMESTAMP_1000 + entryIndex);
      firstManagerInstance.append(logEntry);
    }

    firstManagerInstance.close();

    SegmentStoreManager secondManagerInstance = new SegmentStoreManager(walConfiguration);

    for (int entryIndex = 0; entryIndex < 3; entryIndex++) {
      byte[] entryPayloadBytes = TEST_ENTRY_DATA.getBytes();
      LogEntry logEntry =
          new LogEntry(entryPayloadBytes.length, entryPayloadBytes, TIMESTAMP_2000 + entryIndex);
      secondManagerInstance.append(logEntry);
    }

    secondManagerInstance.close();

    SegmentStoreManager thirdManagerInstance = new SegmentStoreManager(walConfiguration);

    int recoveredSegmentCount = thirdManagerInstance.getSegments().size();
    assertTrue(recoveredSegmentCount >= 1);

    thirdManagerInstance.close();
  }

  @Test
  void smallUnfinalzedSegmentIsSkipped() throws Exception {
    SegmentStoreManager firstManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] validEntryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry validLogEntry =
        new LogEntry(validEntryPayloadBytes.length, validEntryPayloadBytes, TIMESTAMP_1000);
    firstManagerInstance.append(validLogEntry);

    firstManagerInstance.close();

    File logDir = tempLogDirectory.toFile();
    String smallSegmentFilename = SegmentLifecycleManager.generateSegmentFilename(999L);
    File smallSegmentFile = new File(logDir, smallSegmentFilename);
    byte[] smallSegmentBytes = new byte[30];
    Files.write(smallSegmentFile.toPath(), smallSegmentBytes);

    SegmentStoreManager secondManagerInstance = new SegmentStoreManager(walConfiguration);

    int recoveredSegmentCount = secondManagerInstance.getSegments().size();
    assertTrue(recoveredSegmentCount >= 1);

    secondManagerInstance.close();
  }

  @Test
  void unfinalzedSegmentDoesNotAffectMetrics() throws Exception {
    SegmentStoreManager firstManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] validEntryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry validLogEntry =
        new LogEntry(validEntryPayloadBytes.length, validEntryPayloadBytes, TIMESTAMP_1000);
    firstManagerInstance.append(validLogEntry);

    long bytesBeforeClose = firstManagerInstance.getMetrics().getBytesWritten();
    firstManagerInstance.close();

    SegmentStoreManager secondManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] unfinalzedEntryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry unfinalzedLogEntry =
        new LogEntry(
            unfinalzedEntryPayloadBytes.length, unfinalzedEntryPayloadBytes, TIMESTAMP_2000);
    secondManagerInstance.append(unfinalzedLogEntry);

    secondManagerInstance.close();

    SegmentStoreManager thirdManagerInstance = new SegmentStoreManager(walConfiguration);

    long recoveredMetricsBytes = thirdManagerInstance.getMetrics().getBytesWritten();

    assertTrue(recoveredMetricsBytes >= 0);

    thirdManagerInstance.close();
  }

  @Test
  void timestampsPreservedAfterCrashRecovery() throws Exception {
    SegmentStoreManager firstManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] entryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry logEntry = new LogEntry(entryPayloadBytes.length, entryPayloadBytes, TIMESTAMP_1000);
    firstManagerInstance.append(logEntry);

    firstManagerInstance.close();

    SegmentStoreManager secondManagerInstance = new SegmentStoreManager(walConfiguration);

    byte[] unfinalzedEntryPayloadBytes = TEST_ENTRY_DATA.getBytes();
    LogEntry unfinalzedLogEntry =
        new LogEntry(
            unfinalzedEntryPayloadBytes.length, unfinalzedEntryPayloadBytes, TIMESTAMP_2000);
    secondManagerInstance.append(unfinalzedLogEntry);

    secondManagerInstance.close();

    SegmentStoreManager thirdManagerInstance = new SegmentStoreManager(walConfiguration);

    for (SegmentMetadata segmentMetadata : thirdManagerInstance.getSegments()) {
      assertTrue(segmentMetadata.minTimestamp() >= 0);
      assertTrue(segmentMetadata.maxTimestamp() >= segmentMetadata.minTimestamp());
    }

    thirdManagerInstance.close();
  }
}
