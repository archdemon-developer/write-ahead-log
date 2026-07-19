package io.writeahead.log.utils;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.FileStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileUtilsTest {

  @TempDir Path tempDir;

  private String logDir;

  @BeforeEach
  void setUp() {
    logDir = tempDir.toString();
  }

  @Test
  void testCreateDirectory() throws IOException {
    String dirPath = logDir + "/test-dir";
    FileUtils.createDirectory(dirPath);

    assertTrue(new File(dirPath).exists(), "Directory should be created");
    assertTrue(new File(dirPath).isDirectory(), "Path should be a directory");
  }

  @Test
  void testCreateDirectoryRecursively() throws IOException {
    String dirPath = logDir + "/parent/child/grandchild";
    FileUtils.createDirectory(dirPath);

    assertTrue(new File(dirPath).exists(), "Nested directories should be created");
  }

  @Test
  void testFileExists() throws IOException {
    File file = FileUtils.getLogFile(logDir, "test.log");
    assertFalse(FileUtils.fileExists(file), "File should not exist initially");

    file.createNewFile();
    assertTrue(FileUtils.fileExists(file), "File should exist after creation");
  }

  @Test
  void testOpenAppendStream() throws IOException {
    File file = FileUtils.getLogFile(logDir, "test.log");
    FileStream stream = FileUtils.openAppendStream(file);

    assertNotNull(stream.fileOutputStream(), "FileOutputStream should be created");
    assertNotNull(stream.dataOutputStream(), "DataOutputStream should be created");

    FileUtils.closeStream(stream);
    assertTrue(file.exists(), "File should be created after opening");
  }

  @Test
  void testWriteToStream() throws IOException {
    File file = FileUtils.getLogFile(logDir, "test.log");
    FileStream stream = FileUtils.openAppendStream(file);

    byte[] data = "hello world".getBytes();
    FileUtils.writeToStream(stream, data);
    FileUtils.fsyncStream(stream);
    FileUtils.closeStream(stream);

    byte[] readBack = FileUtils.readAllBytes(file);
    assertArrayEquals(data, readBack, "Written data should match read data");
  }

  @Test
  void testWriteMultipleChunks() throws IOException {
    File file = FileUtils.getLogFile(logDir, "test.log");
    FileStream stream = FileUtils.openAppendStream(file);

    byte[] data1 = "first".getBytes();
    byte[] data2 = "second".getBytes();

    FileUtils.writeToStream(stream, data1);
    FileUtils.writeToStream(stream, data2);
    FileUtils.fsyncStream(stream);
    FileUtils.closeStream(stream);

    byte[] readBack = FileUtils.readAllBytes(file);
    byte[] expected = "firstsecond".getBytes();
    assertArrayEquals(expected, readBack, "Multiple writes should be concatenated");
  }

  @Test
  void testAppendMode() throws IOException {
    File file = FileUtils.getLogFile(logDir, "test.log");

    // First write
    FileStream stream1 = FileUtils.openAppendStream(file);
    FileUtils.writeToStream(stream1, "first".getBytes());
    FileUtils.closeStream(stream1);

    // Second write (append mode)
    FileStream stream2 = FileUtils.openAppendStream(file);
    FileUtils.writeToStream(stream2, "second".getBytes());
    FileUtils.closeStream(stream2);

    byte[] readBack = FileUtils.readAllBytes(file);
    byte[] expected = "firstsecond".getBytes();
    assertArrayEquals(expected, readBack, "Append mode should preserve existing data");
  }

  @Test
  void testGetFileSize() throws IOException {
    File file = FileUtils.getLogFile(logDir, "test.log");
    FileStream stream = FileUtils.openAppendStream(file);

    byte[] data = "1234567890".getBytes();
    FileUtils.writeToStream(stream, data);
    FileUtils.closeStream(stream);

    long size = FileUtils.getFileSize(file);
    assertEquals(data.length, size, "File size should match written data length");
  }

  @Test
  void testDeleteFile() throws IOException {
    File file = FileUtils.getLogFile(logDir, "test.log");
    file.createNewFile();

    assertTrue(FileUtils.fileExists(file), "File should exist before deletion");
    boolean deleted = FileUtils.deleteFile(file);

    assertTrue(deleted, "deleteFile should return true");
    assertFalse(FileUtils.fileExists(file), "File should not exist after deletion");
  }

  @Test
  void testDeleteNonExistentFile() throws IOException {
    File file = FileUtils.getLogFile(logDir, "nonexistent.log");

    boolean deleted = FileUtils.deleteFile(file);
    assertFalse(deleted, "deleteFile should return false for non-existent file");
  }

  @Test
  void testListLogFiles() throws IOException {
    // Create multiple log files
    File file1 = FileUtils.getLogFile(logDir, "001.log");
    File file2 = FileUtils.getLogFile(logDir, "002.log");
    File file3 = FileUtils.getLogFile(logDir, "003.log");
    File notLog = FileUtils.getLogFile(logDir, "metadata.txt");

    file1.createNewFile();
    file2.createNewFile();
    file3.createNewFile();
    notLog.createNewFile();

    var logFiles = FileUtils.listLogFiles(logDir);

    assertEquals(3, logFiles.size(), "Should list only .log files");
    assertEquals("001.log", logFiles.get(0).getName(), "Files should be sorted");
    assertEquals("002.log", logFiles.get(1).getName());
    assertEquals("003.log", logFiles.get(2).getName());
  }

  @Test
  void testListLogFilesEmptyDirectory() throws IOException {
    var logFiles = FileUtils.listLogFiles(logDir);
    assertTrue(logFiles.isEmpty(), "Should return empty list for directory with no log files");
  }

  @Test
  void testListLogFilesNonExistentDirectory() {
    var logFiles = FileUtils.listLogFiles(logDir + "/nonexistent");
    assertTrue(logFiles.isEmpty(), "Should return empty list for non-existent directory");
  }

  @Test
  void testFsyncStream() throws IOException {
    File file = FileUtils.getLogFile(logDir, "test.log");
    FileStream stream = FileUtils.openAppendStream(file);

    FileUtils.writeToStream(stream, "data".getBytes());
    // Should not throw
    FileUtils.fsyncStream(stream);

    FileUtils.closeStream(stream);
  }

  @Test
  void testReadAllBytesWithLargeFile() throws IOException {
    File file = FileUtils.getLogFile(logDir, "test.log");
    FileStream stream = FileUtils.openAppendStream(file);

    // Write 1MB of data
    byte[] largeData = new byte[1024 * 1024];
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }

    FileUtils.writeToStream(stream, largeData);
    FileUtils.closeStream(stream);

    byte[] readBack = FileUtils.readAllBytes(file);
    assertArrayEquals(largeData, readBack, "Large file should be read completely");
  }

  @Test
  void testGetLogFile() {
    File file = FileUtils.getLogFile(logDir, "test.log");
    String expectedPath = logDir + "/test.log";

    assertEquals(expectedPath, file.getAbsolutePath(), "Path should be constructed correctly");
  }

  @Test
  void testListLogFilesWithMixedFiles() throws IOException {
    // Test branch: filter only .log files
    File log1 = FileUtils.getLogFile(logDir, "segment.log");
    File txt1 = FileUtils.getLogFile(logDir, "metadata.txt");
    File log2 = FileUtils.getLogFile(logDir, "another.log");

    log1.createNewFile();
    txt1.createNewFile();
    log2.createNewFile();

    var logFiles = FileUtils.listLogFiles(logDir);

    assertEquals(2, logFiles.size(), "Should filter non-.log files");
    assertTrue(logFiles.stream().allMatch(f -> f.getName().endsWith(".log")));
  }
}
