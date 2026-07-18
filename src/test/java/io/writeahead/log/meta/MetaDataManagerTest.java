package io.writeahead.log.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.writeahead.log.models.SegmentMetadata;
import io.writeahead.log.models.WalMetadata;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MetaDataManagerTest {

  @TempDir Path tempDir;

  private MetaDataManager metaDataManager;

  @BeforeEach
  void setUp() throws IOException {
    metaDataManager = new MetaDataManager(tempDir.toString());
  }

  @Test
  void testReadNonExistentFile() throws IOException {
    WalMetadata result = metaDataManager.read();
    assertEquals(new WalMetadata(null, new ArrayList<>()), result);
  }

  @Test
  void testWriteThenRead() throws IOException {
    WalMetadata original =
        new WalMetadata("wal-001.log", List.of(new SegmentMetadata("wal-001.log", 100, 200)));

    metaDataManager.write(original);
    WalMetadata read = metaDataManager.read();

    assertEquals(original, read);
  }
}
