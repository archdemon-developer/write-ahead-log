package io.writeahead.log.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.writeahead.log.enums.LogLevel;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.models.SegmentMetadata;
import io.writeahead.log.models.WalConfiguration;
import io.writeahead.log.models.WalMetadata;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MetaDataStoreManagerTest {

  @TempDir Path tempDir;

  private MetaDataStoreManager metaDataStoreManager;

  @BeforeEach
  void setUp() throws IOException {
    WalConfiguration config = new WalConfiguration.Builder().logDir(tempDir.toString()).build();
    metaDataStoreManager = new MetaDataStoreManager(config);
    LoggerFactory.setLogLevel(LogLevel.ERROR);
  }

  @Test
  void testReadNonExistentFile() throws IOException {
    WalMetadata result = metaDataStoreManager.read();
    assertEquals(new WalMetadata(null, new ArrayList<>()), result);
  }

  @Test
  void testWriteThenRead() throws IOException {
    WalMetadata original =
        new WalMetadata("wal-001.log", List.of(new SegmentMetadata("wal-001.log", 100, 200)));

    metaDataStoreManager.write(original);
    WalMetadata read = metaDataStoreManager.read();

    assertEquals(original, read);
  }
}
