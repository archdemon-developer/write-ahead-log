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
    FileUtils2 fileUtils2 = new FileUtils2(TEST_LOG_PATH);

    byte[] data = "single-entry".getBytes();
    LogEntry entry = new LogEntry(data.length, data, System.currentTimeMillis());
    fileUtils2.writeSingle(entry);

    fileUtils2.close();
    fileUtils2 = new FileUtils2(TEST_LOG_PATH);

    List<LogEntry> entries = fileUtils2.readAll();
    assertEquals(1, entries.size(), "Should have 1 entry");
    assertEquals(data.length, entries.getFirst().size(), "Entry size should match");

    fileUtils2.close();
  }
}
