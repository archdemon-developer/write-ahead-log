package io.writeahead.log.fileio;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.LogEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FileUtilsTest {

  private static final String TEST_LOG_PATH = "test-fileutils.log";

  @BeforeEach
  void setUp() throws IOException {
    Files.deleteIfExists(Paths.get(TEST_LOG_PATH));
  }

  @Test
  void testWriteSingleBypassesBatchAndPersists() throws IOException {
    FileUtils fileUtils = new FileUtils(TEST_LOG_PATH);

    byte[] data = "single-entry".getBytes();
    LogEntry entry = new LogEntry(data.length, data);
    fileUtils.writeSingle(entry);

    fileUtils.close();
    fileUtils = new FileUtils(TEST_LOG_PATH);

    List<LogEntry> entries = fileUtils.readAll();
    assertEquals(1, entries.size(), "Should have 1 entry");
    assertEquals(data.length, entries.getFirst().getSize(), "Entry size should match");

    fileUtils.close();
  }
}
