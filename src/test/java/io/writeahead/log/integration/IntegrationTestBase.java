package io.writeahead.log.integration;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.enums.strategies.RotationPolicyType;
import io.writeahead.log.models.LogEntry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

public abstract class IntegrationTestBase {

  protected Path tempDir;
  protected WriteAheadLog wal;
  protected WalConfiguration config;
  protected IntegrationTestMetrics metrics;

  @BeforeEach
  void setUp(@TempDir Path dir) throws IOException {
    this.tempDir = dir;
    this.metrics = new IntegrationTestMetrics();
    this.config = createConfig();
    this.wal = new WriteAheadLog(config);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (wal != null) {
      try {
        wal.close();
      } catch (IOException e) {

        System.err.println("Error closing WAL during cleanup: " + e.getMessage());
      }
    }
  }

  protected WalConfiguration createConfig() {
    return new WalConfiguration.Builder()
        .logDir(tempDir.toString())
        .batchSize(10)
        .maxSegmentSize(10 * 1024 * 1024)
        .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
        .rotationPolicyType(RotationPolicyType.SIZE_BASED)
        .timestampFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
        .maxRetries(3)
        .retryBackoffMs(10)
        .retryBackoffMultiplier(2.0)
        .build();
  }

  protected void appendNEntries(int count, long baseTimestamp) throws IOException {
    for (int i = 0; i < count; i++) {
      byte[] payload = new byte[100];
      for (int j = 0; j < payload.length; j++) {
        payload[j] = (byte) (j % 256);
      }
      wal.append(new LogEntry(payload.length, payload, baseTimestamp + i));
    }
  }

  protected void appendEntriesAndFlush(int count, long baseTimestamp) throws IOException {
    appendNEntries(count, baseTimestamp);
    wal.writeBatch();
  }

  protected List<LogEntry> readAndAssert(int expectedCount) throws IOException {
    List<LogEntry> entries = wal.readAllSegments();
    if (entries.size() != expectedCount) {
      throw new AssertionError("Expected " + expectedCount + " entries, but got " + entries.size());
    }
    return entries;
  }

  protected List<LogEntry> readAllEntries() throws IOException {
    return wal.readAllSegments();
  }

  protected void assertTimestampsOrdered(List<LogEntry> entries) {
    for (int i = 1; i < entries.size(); i++) {
      if (entries.get(i).timestamp() < entries.get(i - 1).timestamp()) {
        throw new AssertionError(
            "Entry "
                + i
                + " has timestamp "
                + entries.get(i).timestamp()
                + " which is less than entry "
                + (i - 1)
                + " with timestamp "
                + entries.get(i - 1).timestamp());
      }
    }
  }

  protected void assertAllEntriesSize(List<LogEntry> entries, int expectedSize) {
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i).size() != expectedSize) {
        throw new AssertionError(
            "Entry " + i + " has size " + entries.get(i).size() + ", expected " + expectedSize);
      }
    }
  }

  protected IntegrationTestMetrics getMetrics() {
    return metrics;
  }

  protected WriteAheadLog createRecoveredWalInstance() throws IOException {
    if (wal != null) {
      wal.close();
    }
    return new WriteAheadLog(config);
  }
}
