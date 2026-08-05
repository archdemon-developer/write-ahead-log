package io.writeahead.log.metrics;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.segments.SegmentStoreManager;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MetricsInstrumentationTest {

  private static final long SMALL_SEGMENT_SIZE = 5 * 1024;
  private static final int BATCH_SIZE_ONE = 1;
  private static final int LARGE_ENTRY_SIZE = 1024;
  private static final int ENTRIES_TO_FORCE_ROTATION = 10;
  private static final long BASE_TIMESTAMP = 1000L;

  private Path tempLogDirectory;
  private SegmentStoreManager walManager;

  @BeforeEach
  void setUp() throws Exception {
    tempLogDirectory = Files.createTempDirectory("metrics-instrumentation-test-");
  }

  @AfterEach
  void tearDown() throws Exception {
    if (walManager != null && walManager.isOpen()) {
      walManager.close();
    }
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
  void metricsRecordSegmentRotation() throws Exception {
    WalConfiguration configWithSmallSegmentSize =
        new WalConfiguration.Builder()
            .logDir(tempLogDirectory.toString())
            .maxSegmentSize(SMALL_SEGMENT_SIZE)
            .batchSize(BATCH_SIZE_ONE)
            .build();

    walManager = new SegmentStoreManager(configWithSmallSegmentSize);

    for (int entryIndex = 0; entryIndex < ENTRIES_TO_FORCE_ROTATION; entryIndex++) {
      byte[] largePayload = new byte[LARGE_ENTRY_SIZE];
      for (int i = 0; i < largePayload.length; i++) {
        largePayload[i] = (byte) (entryIndex % 256);
      }
      LogEntry entry = new LogEntry(largePayload.length, largePayload, BASE_TIMESTAMP + entryIndex);
      walManager.append(entry);
    }

    walManager.close();

    SimpleWalMetrics metrics = walManager.getMetrics();

    assertTrue(metrics.getLastRotationTimeMs() > 0, "Rotation time should be recorded");
    assertTrue(metrics.getSegmentCount() > 1, "Multiple segments should exist after rotation");
    assertEquals(
        ENTRIES_TO_FORCE_ROTATION, metrics.getEntriesWritten(), "All entries should be recorded");
    assertTrue(metrics.getBytesWritten() > 0, "Bytes written should be tracked");
  }
}
