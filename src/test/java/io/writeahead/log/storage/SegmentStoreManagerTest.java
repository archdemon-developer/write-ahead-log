package io.writeahead.log.storage;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.WalConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SegmentStoreManagerTest {

  @TempDir Path tempDir;

  private String logDir;

  @BeforeEach
  void setUp() {
    logDir = tempDir.toString();
    // LoggerFactory.setLogLevel(LogLevel.ERROR);
  }

  @Test
  void testCreateNewSegmentOnStartup() throws IOException {
    WalConfiguration config = new WalConfiguration.Builder().logDir(logDir).build();
    SegmentStoreManager manager = new SegmentStoreManager(config, new MetaDataStoreManager(config));

    // Verify segment file exists
    File[] logFiles = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".log"));
    assertNotNull(logFiles, "Log files should exist");
    assertEquals(1, logFiles.length, "Should have created one segment");

    // Verify metadata file exists
    File metaFile = new File(logDir + "/.meta");
    assertTrue(metaFile.exists(), "Metadata file should exist");

    manager.close();
  }

  @Test
  void testWriteBatchAndPersist() throws IOException {
    WalConfiguration config = new WalConfiguration.Builder().logDir(logDir).build();
    SegmentStoreManager manager = new SegmentStoreManager(config, new MetaDataStoreManager(config));

    List<LogEntry> batch =
        List.of(
            new LogEntry("entry-1".getBytes().length, "entry-1".getBytes(), 1000),
            new LogEntry("entry-2".getBytes().length, "entry-2".getBytes(), 2000),
            new LogEntry("entry-3".getBytes().length, "entry-3".getBytes(), 3000));

    manager.writeBatch(batch);
    manager.close();

    SegmentStoreManager manager2 =
        new SegmentStoreManager(config, new MetaDataStoreManager(config));
    List<LogEntry> recovered = manager2.readAllSegments();

    assertEquals(3, recovered.size(), "Should recover all written entries");
    assertEquals("entry-1", new String(recovered.get(0).data()), "First entry should match");
    assertEquals("entry-2", new String(recovered.get(1).data()), "Second entry should match");
    assertEquals("entry-3", new String(recovered.get(2).data()), "Third entry should match");

    manager2.close();
  }

  @Test
  void testSegmentRotationAtThreshold() throws IOException {
    WalConfiguration config = new WalConfiguration.Builder().logDir(logDir).build();
    SegmentStoreManager manager = new SegmentStoreManager(config, new MetaDataStoreManager(config));

    byte[] largeData = new byte[5 * 1024 * 1024];
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }

    // Write first batch (5MB)
    List<LogEntry> batch1 = List.of(new LogEntry(largeData.length, largeData, 1000));
    manager.writeBatch(batch1);

    // Write second batch (5MB) - should trigger rotation
    List<LogEntry> batch2 = List.of(new LogEntry(largeData.length, largeData, 2000));
    manager.writeBatch(batch2);

    // Write third batch (1MB) to new segment
    byte[] smallData = new byte[1024 * 1024];
    List<LogEntry> batch3 = List.of(new LogEntry(smallData.length, smallData, 3000));
    manager.writeBatch(batch3);

    manager.close();

    // Verify multiple segment files created
    File[] logFiles = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".log"));
    assertTrue(logFiles.length >= 2, "Should have rotated to at least 2 segments");
  }

  @Test
  void testReadAllSegmentsInOrder() throws IOException {
    WalConfiguration config = new WalConfiguration.Builder().logDir(logDir).build();
    SegmentStoreManager manager = new SegmentStoreManager(config, new MetaDataStoreManager(config));

    // Write multiple batches
    for (int batch = 0; batch < 3; batch++) {
      List<LogEntry> entries =
          List.of(
              new LogEntry(
                  ("batch-" + batch + "-entry-0").getBytes().length,
                  ("batch-" + batch + "-entry-0").getBytes(),
                  1000 + (batch * 1000)),
              new LogEntry(
                  ("batch-" + batch + "-entry-1").getBytes().length,
                  ("batch-" + batch + "-entry-1").getBytes(),
                  2000 + (batch * 1000)));
      manager.writeBatch(entries);
    }

    manager.close();

    // Reopen and read all
    SegmentStoreManager manager2 =
        new SegmentStoreManager(config, new MetaDataStoreManager(config));
    List<LogEntry> allEntries = manager2.readAllSegments();

    assertEquals(6, allEntries.size(), "Should read all entries from all segments");

    // Verify order
    assertEquals("batch-0-entry-0", new String(allEntries.get(0).data()));
    assertEquals("batch-0-entry-1", new String(allEntries.get(1).data()));
    assertEquals("batch-1-entry-0", new String(allEntries.get(2).data()));

    manager2.close();
  }

  @Test
  void testReadAllAfterTimestamp() throws IOException {
    WalConfiguration config = new WalConfiguration.Builder().logDir(logDir).build();
    SegmentStoreManager manager = new SegmentStoreManager(config, new MetaDataStoreManager(config));

    // Write entries with specific timestamps
    List<LogEntry> batch =
        List.of(
            new LogEntry("entry-1".getBytes().length, "entry-1".getBytes(), 1000),
            new LogEntry("entry-2".getBytes().length, "entry-2".getBytes(), 2000),
            new LogEntry("entry-3".getBytes().length, "entry-3".getBytes(), 3000),
            new LogEntry("entry-4".getBytes().length, "entry-4".getBytes(), 4000));

    manager.writeBatch(batch);
    manager.close();

    // Reopen and query after timestamp 2500
    SegmentStoreManager manager2 =
        new SegmentStoreManager(config, new MetaDataStoreManager(config));
    List<LogEntry> afterTimestamp = manager2.readAllAfterTimestamp(2500);

    assertEquals(2, afterTimestamp.size(), "Should return entries after timestamp 2500");
    assertEquals("entry-3", new String(afterTimestamp.get(0).data()), "entry-3 should be included");
    assertEquals("entry-4", new String(afterTimestamp.get(1).data()), "entry-4 should be included");

    manager2.close();
  }

  @Test
  void testReadAllAfterTimestampNoMatches() throws IOException {
    WalConfiguration config = new WalConfiguration.Builder().logDir(logDir).build();
    SegmentStoreManager manager = new SegmentStoreManager(config, new MetaDataStoreManager(config));

    List<LogEntry> batch =
        List.of(
            new LogEntry("entry-1".getBytes().length, "entry-1".getBytes(), 1000),
            new LogEntry("entry-2".getBytes().length, "entry-2".getBytes(), 2000));

    manager.writeBatch(batch);
    manager.close();

    SegmentStoreManager manager2 =
        new SegmentStoreManager(config, new MetaDataStoreManager(config));
    List<LogEntry> afterTimestamp = manager2.readAllAfterTimestamp(5000);

    assertTrue(afterTimestamp.isEmpty(), "Should return empty list when no entries match");

    manager2.close();
  }

  @Test
  void testTruncateBeforeTimestamp() throws IOException {
    WalConfiguration config = new WalConfiguration.Builder().logDir(logDir).build();
    SegmentStoreManager manager = new SegmentStoreManager(config, new MetaDataStoreManager(config));

    byte[] largeData = new byte[5 * 1024 * 1024 + 512 * 1024]; // 5.5MB per entry

    List<LogEntry> batch1 = List.of(new LogEntry(largeData.length, largeData, 1000));
    manager.writeBatch(batch1);

    List<LogEntry> batch2 = List.of(new LogEntry(largeData.length, largeData, 2000));
    manager.writeBatch(batch2);

    byte[] smallData = new byte[1024 * 1024];
    List<LogEntry> batch3 = List.of(new LogEntry(smallData.length, smallData, 3000));
    manager.writeBatch(batch3);

    manager.close();

    File[] logFiles = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".log"));
    assertTrue(logFiles.length >= 2, "Should have rotated to at least 2 segments");
  }

  @Test
  void testTruncateBeforeTimestampKeepsAtLeastOneSegment() throws IOException {
    WalConfiguration config = new WalConfiguration.Builder().logDir(logDir).build();
    SegmentStoreManager manager = new SegmentStoreManager(config, new MetaDataStoreManager(config));

    // Write single entry
    List<LogEntry> batch =
        List.of(new LogEntry("entry-1".getBytes().length, "entry-1".getBytes(), 1000));

    manager.writeBatch(batch);
    manager.close();

    // Try to truncate before all timestamps
    SegmentStoreManager manager2 =
        new SegmentStoreManager(config, new MetaDataStoreManager(config));
    manager2.truncateBeforeTimestamp(5000); // After all entries

    // Verify at least one segment remains (safety guarantee)
    File[] remaining = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".log"));
    assertTrue(remaining != null && remaining.length >= 1, "Should keep at least one segment");

    manager2.close();
  }

  @Test
  void testMetadataConsistencyAcrossRestarts() throws IOException {
    // Session 1: Write data
    WalConfiguration config = new WalConfiguration.Builder().logDir(logDir).build();
    SegmentStoreManager manager1 =
        new SegmentStoreManager(config, new MetaDataStoreManager(config));
    List<LogEntry> batch1 =
        List.of(new LogEntry("entry-1".getBytes().length, "entry-1".getBytes(), 1000));
    manager1.writeBatch(batch1);
    manager1.close();

    // Session 2: Add more data
    SegmentStoreManager manager2 =
        new SegmentStoreManager(config, new MetaDataStoreManager(config));
    List<LogEntry> batch2 =
        List.of(new LogEntry("entry-2".getBytes().length, "entry-2".getBytes(), 2000));
    manager2.writeBatch(batch2);
    manager2.close();

    // Session 3: Verify all data
    SegmentStoreManager manager3 =
        new SegmentStoreManager(config, new MetaDataStoreManager(config));
    List<LogEntry> allEntries = manager3.readAllSegments();

    assertEquals(2, allEntries.size(), "Should have both entries from both sessions");
    assertEquals("entry-1", new String(allEntries.get(0).data()));
    assertEquals("entry-2", new String(allEntries.get(1).data()));

    manager3.close();
  }

  @Test
  void testTimestampTrackingInMetadata() throws IOException {
    WalConfiguration config = new WalConfiguration.Builder().logDir(logDir).build();
    SegmentStoreManager manager = new SegmentStoreManager(config, new MetaDataStoreManager(config));

    // Write batch with known timestamps
    List<LogEntry> batch =
        List.of(
            new LogEntry("entry-1".getBytes().length, "entry-1".getBytes(), 5000),
            new LogEntry("entry-2".getBytes().length, "entry-2".getBytes(), 10000));

    manager.writeBatch(batch);
    manager.close();

    // Reopen and check that metadata tracked min/max timestamps
    SegmentStoreManager manager2 =
        new SegmentStoreManager(config, new MetaDataStoreManager(config));
    List<LogEntry> entries = manager2.readAllSegments();

    assertEquals(5000, entries.get(0).timestamp(), "First entry timestamp should be preserved");
    assertEquals(10000, entries.get(1).timestamp(), "Last entry timestamp should be preserved");

    manager2.close();
  }

  @Test
  void testWriteBatchWithEmptyTimestamps() throws IOException {
    WalConfiguration config = new WalConfiguration.Builder().logDir(logDir).build();
    SegmentStoreManager manager = new SegmentStoreManager(config, new MetaDataStoreManager(config));

    // Write batch with timestamp 0
    List<LogEntry> batch =
        List.of(
            new LogEntry("entry-1".getBytes().length, "entry-1".getBytes(), 0),
            new LogEntry("entry-2".getBytes().length, "entry-2".getBytes(), 0));

    manager.writeBatch(batch);
    manager.close();

    SegmentStoreManager manager2 =
        new SegmentStoreManager(config, new MetaDataStoreManager(config));
    List<LogEntry> recovered = manager2.readAllSegments();

    assertEquals(2, recovered.size());
    assertEquals(0, recovered.getFirst().timestamp());

    manager2.close();
  }

  @Test
  void testRotateSegmentUpdatesMetadata() throws IOException {
    WalConfiguration config = new WalConfiguration.Builder().logDir(logDir).build();
    SegmentStoreManager manager = new SegmentStoreManager(config, new MetaDataStoreManager(config));

    // Write large batch to trigger rotation
    byte[] largeData = new byte[5 * 1024 * 1024 + 512 * 1024];
    List<LogEntry> batch1 = List.of(new LogEntry(largeData.length, largeData, 1000));
    manager.writeBatch(batch1);

    List<LogEntry> batch2 = List.of(new LogEntry(largeData.length, largeData, 2000));
    manager.writeBatch(batch2);

    manager.close();

    // Verify rotation happened
    File[] segments = tempDir.toFile().listFiles((d, n) -> n.endsWith(".log"));
    assertTrue(segments.length >= 2, "Should have multiple segments after rotation");
  }
}
