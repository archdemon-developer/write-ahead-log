package io.writeahead.log.utils;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.FileStream;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("FileUtils - File I/O Operations")
public class FileUtilsTest {

  @TempDir Path tempDir;

  private File testFile;
  private String testDirPath;

  @BeforeEach
  void setUp() {
    testDirPath = tempDir.toString();
    testFile = new File(testDirPath, "test.log");
  }

  @AfterEach
  void tearDown() throws IOException {
    if (testFile != null && testFile.exists()) {
      Files.deleteIfExists(testFile.toPath());
    }
  }

  @Test
  @DisplayName("openAppendStream creates new file stream in append mode")
  void testOpenAppendStreamCreatesNewFile() throws IOException {
    assertFalse(testFile.exists());

    FileStream fileStream = FileUtils.openAppendStream(testFile);

    assertNotNull(fileStream);
    assertNotNull(fileStream.fileOutputStream());
    assertNotNull(fileStream.dataOutputStream());
    assertTrue(testFile.exists());

    fileStream.closeAll();
  }

  @Test
  @DisplayName("openAppendStream appends to existing file without truncating")
  void testOpenAppendStreamAppendsToExistingFile() throws IOException {
    FileStream firstStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(firstStream, new byte[] {1, 2, 3});
    firstStream.closeAll();

    long sizeAfterFirstWrite = FileUtils.getFileSize(testFile);
    assertEquals(3, sizeAfterFirstWrite);

    FileStream secondStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(secondStream, new byte[] {4, 5, 6});
    secondStream.closeAll();

    long sizeAfterSecondWrite = FileUtils.getFileSize(testFile);
    assertEquals(6, sizeAfterSecondWrite);

    byte[] allData = FileUtils.readAllBytes(testFile);
    assertArrayEquals(new byte[] {1, 2, 3, 4, 5, 6}, allData);
  }

  @Test
  @DisplayName("openAppendStream handles IO exception when file path is invalid")
  void testOpenAppendStreamWithInvalidPath() {
    File invalidFile = new File("/root/invalid/path/that/does/not/exist/file.log");

    assertThrows(IOException.class, () -> FileUtils.openAppendStream(invalidFile));
  }

  @Test
  @DisplayName("writeToStream writes data to stream correctly")
  void testWriteToStreamWritesData() throws IOException {
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    byte[] testData = {10, 20, 30, 40, 50};

    FileUtils.writeToStream(fileStream, testData);
    fileStream.closeAll();

    byte[] readData = FileUtils.readAllBytes(testFile);
    assertArrayEquals(testData, readData);
  }

  @Test
  @DisplayName("writeToStream handles empty byte array")
  void testWriteToStreamWithEmptyArray() throws IOException {
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    byte[] emptyData = {};

    FileUtils.writeToStream(fileStream, emptyData);
    fileStream.closeAll();

    assertEquals(0, FileUtils.getFileSize(testFile));
  }

  @Test
  @DisplayName("writeToStream handles large byte array")
  void testWriteToStreamWithLargeArray() throws IOException {
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    byte[] largeData = new byte[10000];
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }

    FileUtils.writeToStream(fileStream, largeData);
    fileStream.closeAll();

    byte[] readData = FileUtils.readAllBytes(testFile);
    assertArrayEquals(largeData, readData);
  }

  @Test
  @DisplayName("writeToStream multiple times accumulates data")
  void testWriteToStreamMultipleTimes() throws IOException {
    FileStream fileStream = FileUtils.openAppendStream(testFile);

    FileUtils.writeToStream(fileStream, new byte[] {1});
    FileUtils.writeToStream(fileStream, new byte[] {2});
    FileUtils.writeToStream(fileStream, new byte[] {3});

    fileStream.closeAll();

    byte[] readData = FileUtils.readAllBytes(testFile);
    assertArrayEquals(new byte[] {1, 2, 3}, readData);
  }

  @Test
  @DisplayName("fsyncStream syncs data to disk without error")
  void testFsyncStreamSuccessfully() throws IOException {
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, new byte[] {1, 2, 3});

    FileUtils.fsyncStream(fileStream);

    fileStream.closeAll();

    assertEquals(3, FileUtils.getFileSize(testFile));
  }

  @Test
  @DisplayName("fsyncStream can be called multiple times")
  void testFsyncStreamMultipleTimes() throws IOException {
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, new byte[] {1, 2, 3});

    FileUtils.fsyncStream(fileStream);
    FileUtils.fsyncStream(fileStream);
    FileUtils.fsyncStream(fileStream);

    fileStream.closeAll();

    assertEquals(3, FileUtils.getFileSize(testFile));
  }

  @Test
  @DisplayName("fsyncStream before and after writing data")
  void testFsyncStreamBeforeAndAfterWrite() throws IOException {
    FileStream fileStream = FileUtils.openAppendStream(testFile);

    FileUtils.writeToStream(fileStream, new byte[] {1, 2, 3});
    FileUtils.fsyncStream(fileStream);

    FileUtils.writeToStream(fileStream, new byte[] {4, 5});
    FileUtils.fsyncStream(fileStream);

    fileStream.closeAll();

    assertEquals(5, FileUtils.getFileSize(testFile));
  }

  @Test
  @DisplayName("closeStream closes file stream without error")
  void testCloseStreamSuccessfully() throws IOException {
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, new byte[] {1, 2, 3});

    FileUtils.closeStream(fileStream);

    byte[] data = FileUtils.readAllBytes(testFile);
    assertArrayEquals(new byte[] {1, 2, 3}, data);
  }

  @Test
  @DisplayName("closeStream prevents further writes to closed stream")
  void testCloseStreamPreventsWriting() throws IOException {
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, new byte[] {1, 2, 3});
    FileUtils.closeStream(fileStream);

    assertThrows(IOException.class, () -> FileUtils.writeToStream(fileStream, new byte[] {4}));
  }

  @Test
  @DisplayName("readAllBytes reads entire file content correctly")
  void testReadAllBytesReadsEntireFile() throws IOException {
    byte[] testData = {10, 20, 30, 40, 50};
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, testData);
    fileStream.closeAll();

    byte[] readData = FileUtils.readAllBytes(testFile);
    assertArrayEquals(testData, readData);
  }

  @Test
  @DisplayName("readAllBytes handles empty file")
  void testReadAllBytesHandlesEmptyFile() throws IOException {
    testFile.createNewFile();

    byte[] readData = FileUtils.readAllBytes(testFile);
    assertEquals(0, readData.length);
  }

  @Test
  @DisplayName("readAllBytes throws IOException for non-existent file")
  void testReadAllBytesThrowsForNonExistentFile() {
    File nonExistentFile = new File(testDirPath, "non_existent.log");

    assertThrows(IOException.class, () -> FileUtils.readAllBytes(nonExistentFile));
  }

  @Test
  @DisplayName("readAllBytes handles large file")
  void testReadAllBytesHandlesLargeFile() throws IOException {
    byte[] largeData = new byte[100000];
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }

    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, largeData);
    fileStream.closeAll();

    byte[] readData = FileUtils.readAllBytes(testFile);
    assertArrayEquals(largeData, readData);
  }

  @Test
  @DisplayName("getFileSize returns correct size for non-empty file")
  void testGetFileSizeForNonEmptyFile() throws IOException {
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, new byte[] {1, 2, 3, 4, 5});
    fileStream.closeAll();

    long size = FileUtils.getFileSize(testFile);
    assertEquals(5, size);
  }

  @Test
  @DisplayName("getFileSize returns zero for empty file")
  void testGetFileSizeForEmptyFile() throws IOException {
    testFile.createNewFile();

    long size = FileUtils.getFileSize(testFile);
    assertEquals(0, size);
  }

  @Test
  @DisplayName("getFileSize returns zero for non-existent file")
  void testGetFileSizeForNonExistentFile() {
    File nonExistentFile = new File(testDirPath, "non_existent.log");

    long size = FileUtils.getFileSize(nonExistentFile);
    assertEquals(0, size);
  }

  @Test
  @DisplayName("getFileSize tracks size after multiple writes")
  void testGetFileSizeTracksMultipleWrites() throws IOException {
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, new byte[] {1, 2, 3});
    fileStream.closeAll();

    assertEquals(3, FileUtils.getFileSize(testFile));

    FileStream secondStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(secondStream, new byte[] {4, 5});
    secondStream.closeAll();

    assertEquals(5, FileUtils.getFileSize(testFile));
  }

  @Test
  @DisplayName("deleteFile removes existing file successfully")
  void testDeleteFileRemovesExistingFile() throws IOException {
    testFile.createNewFile();
    assertTrue(testFile.exists());

    boolean deleted = FileUtils.deleteFile(testFile);

    assertTrue(deleted);
    assertFalse(testFile.exists());
  }

  @Test
  @DisplayName("deleteFile returns false for non-existent file")
  void testDeleteFileReturnsFalseForNonExistent() throws IOException {
    File nonExistentFile = new File(testDirPath, "non_existent.log");

    boolean deleted = FileUtils.deleteFile(nonExistentFile);

    assertFalse(deleted);
  }

  @Test
  @DisplayName("deleteFile removes file with data")
  void testDeleteFileRemovesFileWithData() throws IOException {
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, new byte[] {1, 2, 3});
    fileStream.closeAll();

    assertTrue(testFile.exists());
    boolean deleted = FileUtils.deleteFile(testFile);

    assertTrue(deleted);
    assertFalse(testFile.exists());
  }

  @Test
  @DisplayName("fileExists returns true for existing file")
  void testFileExistsReturnsTrueForExistingFile() throws IOException {
    testFile.createNewFile();

    boolean exists = FileUtils.fileExists(testFile);

    assertTrue(exists);
  }

  @Test
  @DisplayName("fileExists returns false for non-existent file")
  void testFileExistsReturnsFalseForNonExistent() {
    File nonExistentFile = new File(testDirPath, "non_existent.log");

    boolean exists = FileUtils.fileExists(nonExistentFile);

    assertFalse(exists);
  }

  @Test
  @DisplayName("fileExists returns true after file creation")
  void testFileExistsAfterCreation() throws IOException {
    assertFalse(FileUtils.fileExists(testFile));

    testFile.createNewFile();

    assertTrue(FileUtils.fileExists(testFile));
  }

  @Test
  @DisplayName("getLogFile constructs file path correctly")
  void testGetLogFileConstructsPath() {
    String directory = "/var/log";
    String filename = "app.log";

    File logFile = FileUtils.getLogFile(directory, filename);

    assertEquals("/var/log/app.log", logFile.getPath());
  }

  @Test
  @DisplayName("getLogFile handles different path formats")
  void testGetLogFileWithDifferentPaths() {
    File logFile1 = FileUtils.getLogFile("/tmp", "test.log");
    File logFile2 = FileUtils.getLogFile("./logs", "app.log");
    File logFile3 = FileUtils.getLogFile("/", "root.log");

    assertEquals("/tmp/test.log", logFile1.getPath());
    assertEquals("./logs/app.log", logFile2.getPath());
    assertEquals("/root.log", logFile3.getPath());
  }

  @Test
  @DisplayName("createDirectory creates new directory")
  void testCreateDirectoryCreatesNewDir() throws IOException {
    Path newDirPath = tempDir.resolve("newdir");
    String newDirPathStr = newDirPath.toString();

    assertFalse(Files.exists(newDirPath));

    FileUtils.createDirectory(newDirPathStr);

    assertTrue(Files.exists(newDirPath));
  }

  @Test
  @DisplayName("createDirectory creates nested directories")
  void testCreateDirectoryCreatesNestedDirs() throws IOException {
    Path nestedPath = tempDir.resolve("level1/level2/level3");
    String nestedPathStr = nestedPath.toString();

    assertFalse(Files.exists(nestedPath));

    FileUtils.createDirectory(nestedPathStr);

    assertTrue(Files.exists(nestedPath));
  }

  @Test
  @DisplayName("createDirectory is idempotent - doesn't fail if dir exists")
  void testCreateDirectoryIsIdempotent() throws IOException {
    Path dirPath = tempDir.resolve("testdir");
    String dirPathStr = dirPath.toString();

    FileUtils.createDirectory(dirPathStr);
    assertTrue(Files.exists(dirPath));

    FileUtils.createDirectory(dirPathStr);

    assertTrue(Files.exists(dirPath));
  }

  @Test
  @DisplayName("listLogFiles returns all .log files in directory")
  void testListLogFilesReturnsLogFiles() throws IOException {
    File file1 = new File(testDirPath, "log1.log");
    File file2 = new File(testDirPath, "log2.log");
    File file3 = new File(testDirPath, "log3.log");
    File notLog = new File(testDirPath, "readme.txt");

    file1.createNewFile();
    file2.createNewFile();
    file3.createNewFile();
    notLog.createNewFile();

    List<File> logFiles = FileUtils.listLogFiles(testDirPath);

    assertEquals(3, logFiles.size());
    assertTrue(logFiles.stream().anyMatch(f -> f.getName().equals("log1.log")));
    assertTrue(logFiles.stream().anyMatch(f -> f.getName().equals("log2.log")));
    assertTrue(logFiles.stream().anyMatch(f -> f.getName().equals("log3.log")));
    assertFalse(logFiles.stream().anyMatch(f -> f.getName().equals("readme.txt")));
  }

  @Test
  @DisplayName("listLogFiles returns empty list for empty directory")
  void testListLogFilesEmptyDirectory() throws IOException {
    List<File> logFiles = FileUtils.listLogFiles(testDirPath);

    assertEquals(0, logFiles.size());
  }

  @Test
  @DisplayName("listLogFiles returns empty list for non-existent directory")
  void testListLogFilesNonExistentDirectory() {
    String nonExistentDir = testDirPath + "/non_existent";

    List<File> logFiles = FileUtils.listLogFiles(nonExistentDir);

    assertEquals(0, logFiles.size());
  }

  @Test
  @DisplayName("listLogFiles ignores files without .log extension")
  void testListLogFilesIgnoresNonLogFiles() throws IOException {
    File txtFile = new File(testDirPath, "file.txt");
    File binFile = new File(testDirPath, "file.bin");
    File logFile = new File(testDirPath, "file.log");

    txtFile.createNewFile();
    binFile.createNewFile();
    logFile.createNewFile();

    List<File> logFiles = FileUtils.listLogFiles(testDirPath);

    assertEquals(1, logFiles.size());
    assertEquals("file.log", logFiles.get(0).getName());
  }

  @Test
  @DisplayName("listLogFiles returns files in sorted order by name")
  void testListLogFilesSortedByName() throws IOException {
    File file3 = new File(testDirPath, "zzz.log");
    File file1 = new File(testDirPath, "aaa.log");
    File file2 = new File(testDirPath, "mmm.log");

    file1.createNewFile();
    file2.createNewFile();
    file3.createNewFile();

    List<File> logFiles = FileUtils.listLogFiles(testDirPath);

    assertEquals(3, logFiles.size());
    assertEquals("aaa.log", logFiles.get(0).getName());
    assertEquals("mmm.log", logFiles.get(1).getName());
    assertEquals("zzz.log", logFiles.get(2).getName());
  }

  @Test
  @DisplayName("listLogFiles handles case-insensitive .log extension")
  void testListLogFilesCaseInsensitiveExtension() throws IOException {
    File logFile = new File(testDirPath, "file.log");
    File logFileUpper = new File(testDirPath, "file.LOG");

    logFile.createNewFile();
    logFileUpper.createNewFile();

    List<File> logFiles = FileUtils.listLogFiles(testDirPath);

    assertEquals(2, logFiles.size());
  }

  @Test
  @DisplayName("readBytes reads specific bytes from file")
  void testReadBytesReadsSpecificBytes() throws IOException {
    byte[] testData = {10, 20, 30, 40, 50, 60, 70};
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, testData);
    fileStream.closeAll();

    byte[] readData = FileUtils.readBytes(testFile, 0, 3);

    assertArrayEquals(new byte[] {10, 20, 30}, readData);
  }

  @Test
  @DisplayName("readBytes reads from middle of file")
  void testReadBytesFromMiddleOfFile() throws IOException {
    byte[] testData = {10, 20, 30, 40, 50};
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, testData);
    fileStream.closeAll();

    byte[] readData = FileUtils.readBytes(testFile, 2, 2);

    assertArrayEquals(new byte[] {30, 40}, readData);
  }

  @Test
  @DisplayName("readBytes reads from end of file")
  void testReadBytesFromEndOfFile() throws IOException {
    byte[] testData = {10, 20, 30, 40, 50};
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, testData);
    fileStream.closeAll();

    byte[] readData = FileUtils.readBytes(testFile, 3, 2);

    assertArrayEquals(new byte[] {40, 50}, readData);
  }

  @Test
  @DisplayName("readBytes throws when reading beyond file length")
  void testReadBytesThrowsWhenBeyondFileLength() throws IOException {
    byte[] testData = {10, 20, 30};
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, testData);
    fileStream.closeAll();

    assertThrows(IOException.class, () -> FileUtils.readBytes(testFile, 0, 10));
  }

  @Test
  @DisplayName("readBytes throws for non-existent file")
  void testReadBytesThrowsForNonExistentFile() {
    File nonExistentFile = new File(testDirPath, "non_existent.log");

    assertThrows(IOException.class, () -> FileUtils.readBytes(nonExistentFile, 0, 5));
  }

  @Test
  @DisplayName("readBytes handles zero-length read")
  void testReadBytesZeroLength() throws IOException {
    byte[] testData = {10, 20, 30};
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, testData);
    fileStream.closeAll();

    byte[] readData = FileUtils.readBytes(testFile, 0, 0);

    assertEquals(0, readData.length);
  }

  @Test
  @DisplayName("readBytes works with large offsets")
  void testReadBytesWithLargeOffsets() throws IOException {
    byte[] testData = new byte[10000];
    for (int i = 0; i < testData.length; i++) {
      testData[i] = (byte) (i % 256);
    }
    FileStream fileStream = FileUtils.openAppendStream(testFile);
    FileUtils.writeToStream(fileStream, testData);
    fileStream.closeAll();

    byte[] readData = FileUtils.readBytes(testFile, 9998, 2);

    assertEquals(2, readData.length);
  }
}
