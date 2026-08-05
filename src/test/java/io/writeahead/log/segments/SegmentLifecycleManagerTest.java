package io.writeahead.log.segments;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.FileStream;
import io.writeahead.log.segments.management.SegmentLifecycleManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SegmentLifecycleManagerTest {

  private static final int MAGIC_BYTE_INDEX = 0;
  private static final byte EXPECTED_MAGIC_BYTE = (byte) 0xAA;
  private static final int SEGMENT_HEADER_SIZE_BYTES = 48;
  private static final int MINIMUM_FILE_SIZE_FOR_HEADER_BYTES = 48;
  private static final String LOG_FILE_EXTENSION = ".log";
  private static final int SINGLE_SEGMENT_FILE = 1;
  private static final int THREE_SEGMENT_FILES = 3;
  private static final int ZERO_ENTRY_COUNT = 0;
  private static final int ONE_ENTRY = 1;
  private static final long FIRST_SEGMENT_SEQUENCE = 1L;
  private static final long SECOND_SEGMENT_SEQUENCE = 2L;
  private static final long THIRD_SEGMENT_SEQUENCE = 3L;
  private static final long FIRST_ENTRY_TIMESTAMP = 1000L;
  private static final long SECOND_ENTRY_TIMESTAMP = 2000L;
  private static final String TEST_ENTRY_DATA = "test entry";
  private static final String READABLE_TEST_DATA = "readable data";
  private static final int LARGE_DATA_SIZE = 2048;

  private Path tempFileSystemDirectory;
  private SegmentLifecycleManager lifecycleManagerUnderTest;

  @BeforeEach
  void setUp() throws IOException {
    tempFileSystemDirectory = Files.createTempDirectory("segment-lifecycle-test-");
    lifecycleManagerUnderTest = new SegmentLifecycleManager(tempFileSystemDirectory.toString());
  }

  @AfterEach
  void tearDown() throws IOException {
    Files.walk(tempFileSystemDirectory)
        .sorted((a, b) -> b.compareTo(a))
        .forEach(
            path -> {
              try {
                Files.delete(path);
              } catch (IOException ignored) {
              }
            });
  }

  @Test
  void constructorThrowsWhenDirectoryDoesNotExist() {
    String nonexistentDirectoryPath = "/tmp/does-not-exist-" + System.currentTimeMillis();

    assertThrows(IOException.class, () -> new SegmentLifecycleManager(nonexistentDirectoryPath));
  }

  @Test
  void constructorThrowsWhenPathIsNotDirectory() throws IOException {
    File fileInsteadOfDirectory = new File(tempFileSystemDirectory.toFile(), "notadir");
    Files.write(fileInsteadOfDirectory.toPath(), "test".getBytes());

    assertThrows(
        IOException.class,
        () -> new SegmentLifecycleManager(fileInsteadOfDirectory.getAbsolutePath()));
  }

  @Test
  void createNewSegmentWritesHeaderToFile() throws IOException {
    FileStream createdSegmentStream =
        lifecycleManagerUnderTest.createNewSegment(FIRST_SEGMENT_SEQUENCE);

    File[] logFiles =
        tempFileSystemDirectory
            .toFile()
            .listFiles((dir, name) -> name.endsWith(LOG_FILE_EXTENSION));
    assertEquals(SINGLE_SEGMENT_FILE, logFiles.length);

    byte[] segmentFileBytes = Files.readAllBytes(logFiles[0].toPath());
    assertTrue(segmentFileBytes.length >= MINIMUM_FILE_SIZE_FOR_HEADER_BYTES);
    assertEquals(EXPECTED_MAGIC_BYTE, segmentFileBytes[MAGIC_BYTE_INDEX]);

    createdSegmentStream.fileOutputStream().close();
    createdSegmentStream.dataOutputStream().close();
  }

  @Test
  void createNewSegmentReturnsOpenFileStream() throws IOException {
    FileStream createdSegmentStream =
        lifecycleManagerUnderTest.createNewSegment(FIRST_SEGMENT_SEQUENCE);

    assertNotNull(createdSegmentStream);
    assertNotNull(createdSegmentStream.fileOutputStream());
    assertNotNull(createdSegmentStream.dataOutputStream());

    createdSegmentStream.fileOutputStream().close();
    createdSegmentStream.dataOutputStream().close();
  }

  @Test
  void finalizeSegmentWritesFooterAndClosesStream() throws IOException {
    FileStream createdSegmentStream =
        lifecycleManagerUnderTest.createNewSegment(FIRST_SEGMENT_SEQUENCE);
    byte[] testEntryData = TEST_ENTRY_DATA.getBytes();
    createdSegmentStream.dataOutputStream().write(testEntryData);

    lifecycleManagerUnderTest.finalizeSegment(
        createdSegmentStream, ZERO_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

    File[] logFiles =
        tempFileSystemDirectory
            .toFile()
            .listFiles((dir, name) -> name.endsWith(LOG_FILE_EXTENSION));
    byte[] segmentFileBytes = Files.readAllBytes(logFiles[0].toPath());
    assertTrue(segmentFileBytes.length > SEGMENT_HEADER_SIZE_BYTES);
  }

  @Test
  void multipleSequentialSegmentsCreatedIndependently() throws IOException {
    FileStream firstSegmentStream =
        lifecycleManagerUnderTest.createNewSegment(FIRST_SEGMENT_SEQUENCE);
    lifecycleManagerUnderTest.finalizeSegment(
        firstSegmentStream, ZERO_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

    FileStream secondSegmentStream =
        lifecycleManagerUnderTest.createNewSegment(SECOND_SEGMENT_SEQUENCE);
    lifecycleManagerUnderTest.finalizeSegment(
        secondSegmentStream, ZERO_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

    FileStream thirdSegmentStream =
        lifecycleManagerUnderTest.createNewSegment(THIRD_SEGMENT_SEQUENCE);
    lifecycleManagerUnderTest.finalizeSegment(
        thirdSegmentStream, ZERO_ENTRY_COUNT, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

    File[] logFiles =
        tempFileSystemDirectory
            .toFile()
            .listFiles((dir, name) -> name.endsWith(LOG_FILE_EXTENSION));
    assertEquals(THREE_SEGMENT_FILES, logFiles.length);
  }

  @Test
  void createdSegmentFileIsReadable() throws IOException {
    FileStream createdSegmentStream =
        lifecycleManagerUnderTest.createNewSegment(FIRST_SEGMENT_SEQUENCE);
    byte[] testData = READABLE_TEST_DATA.getBytes();
    createdSegmentStream.dataOutputStream().write(testData);

    lifecycleManagerUnderTest.finalizeSegment(
        createdSegmentStream, ONE_ENTRY, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

    File[] logFiles =
        tempFileSystemDirectory
            .toFile()
            .listFiles((dir, name) -> name.endsWith(LOG_FILE_EXTENSION));
    byte[] readBackData = Files.readAllBytes(logFiles[0].toPath());
    assertTrue(readBackData.length > 0);
  }

  @Test
  void closeSegmentFinalizesAndLogs() throws IOException {
    FileStream createdSegmentStream =
        lifecycleManagerUnderTest.createNewSegment(FIRST_SEGMENT_SEQUENCE);
    byte[] testData = TEST_ENTRY_DATA.getBytes();
    createdSegmentStream.dataOutputStream().write(testData);

    lifecycleManagerUnderTest.closeSegment(
        createdSegmentStream, ONE_ENTRY, FIRST_ENTRY_TIMESTAMP, SECOND_ENTRY_TIMESTAMP);

    File[] logFiles =
        tempFileSystemDirectory
            .toFile()
            .listFiles((dir, name) -> name.endsWith(LOG_FILE_EXTENSION));
    assertEquals(SINGLE_SEGMENT_FILE, logFiles.length);

    byte[] segmentFileBytes = Files.readAllBytes(logFiles[0].toPath());
    assertTrue(segmentFileBytes.length > SEGMENT_HEADER_SIZE_BYTES);
  }

  @Test
  void generateSegmentFilenameIncludesSequenceNumber() {
    String generatedFilename =
        SegmentLifecycleManager.generateSegmentFilename(FIRST_SEGMENT_SEQUENCE);

    assertTrue(generatedFilename.contains("000001"));
    assertTrue(generatedFilename.endsWith(LOG_FILE_EXTENSION));
    assertTrue(generatedFilename.startsWith("wal-"));
  }

  @Test
  void segmentFilenamesAreUniqueForDifferentSequences() {
    String firstFilename = SegmentLifecycleManager.generateSegmentFilename(FIRST_SEGMENT_SEQUENCE);
    String secondFilename =
        SegmentLifecycleManager.generateSegmentFilename(SECOND_SEGMENT_SEQUENCE);

    assertNotEquals(firstFilename, secondFilename);
  }
}
