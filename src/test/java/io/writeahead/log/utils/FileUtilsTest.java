package io.writeahead.log.utils;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.FileStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FileUtilsTest {

    private static final String LOG_FILE_EXTENSION = ".log";
    private static final String TEXT_FILE_EXTENSION = ".txt";
    private static final String TEST_LOG_FILENAME = "test.log";
    private static final String INITIAL_DATA = "initial";
    private static final String APPENDED_DATA = "appended";
    private static final String HELLO_WORLD = "hello world";
    private static final String FIRST_CHUNK = "first";
    private static final String SECOND_CHUNK = "second";
    private static final String THIRD_CHUNK = "third";
    private static final String FIRST_WRITE = "1";
    private static final String SECOND_WRITE = "2";
    private static final String THIRD_WRITE = "3";
    private static final String BEFORE_FSYNC = "before fsync";
    private static final String AFTER_FSYNC = " after fsync";
    private static final String DATA_INTEGRITY_MESSAGE = "integrity test data";
    private static final String DURABLE_DATA = "durable";
    private static final String NONEXISTENT_FILE = "nonexistent.log";
    private static final String LOGS_SUBDIRECTORY = "logs";
    private static final String NEWDIR_SUBDIRECTORY = "newdir";
    private static final String WALFILE_001 = "wal-001.log";
    private static final String WALFILE_002 = "wal-002.log";
    private static final String WALFILE_003 = "wal-003.log";
    private static final String OTHER_FILE = "other.txt";
    private static final String NOT_LOG_CONTENT = "not a log";
    private static final String SEGMENT_CONTENT = "content";
    private static final int BINARY_BYTE_RANGE = 256;
    private static final int EXPECTED_LOG_FILE_COUNT = 3;
    private static final int EMPTY_FILE_SIZE = 0;

    private Path tempLogDirectory;

    @BeforeEach
    void setUp() throws IOException {
        tempLogDirectory = Files.createTempDirectory("wal-fileutils-test-");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempLogDirectory)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ignored) {
                    }
                });
    }

    @Test
    void openAppendStreamCreatesFile() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);

        FileStream openedStream = FileUtils.openAppendStream(testLogFile);

        assertNotNull(openedStream);
        assertTrue(testLogFile.exists());
        assertNotNull(openedStream.fileOutputStream());
        assertNotNull(openedStream.dataOutputStream());

        FileUtils.closeStream(openedStream);
    }

    @Test
    void openAppendStreamAppendsInsteadOfTruncating() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);

        FileStream firstStream = FileUtils.openAppendStream(testLogFile);
        FileUtils.writeToStream(firstStream, INITIAL_DATA.getBytes());
        FileUtils.closeStream(firstStream);
        long sizeAfterFirstWrite = FileUtils.getFileSize(testLogFile);

        FileStream secondStream = FileUtils.openAppendStream(testLogFile);
        FileUtils.writeToStream(secondStream, APPENDED_DATA.getBytes());
        FileUtils.closeStream(secondStream);
        long sizeAfterSecondWrite = FileUtils.getFileSize(testLogFile);

        assertTrue(sizeAfterSecondWrite > sizeAfterFirstWrite);
        assertEquals(sizeAfterFirstWrite + APPENDED_DATA.length(), sizeAfterSecondWrite);
    }

    @Test
    void writeToStreamPersistsDataToDisk() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        FileStream stream = FileUtils.openAppendStream(testLogFile);

        byte[] dataToWrite = HELLO_WORLD.getBytes();
        FileUtils.writeToStream(stream, dataToWrite);
        FileUtils.closeStream(stream);

        byte[] readBackData = FileUtils.readAllBytes(testLogFile);
        assertArrayEquals(dataToWrite, readBackData);
    }

    @Test
    void writeToStreamMultipleTimesPreservesBothWrites() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        FileStream stream = FileUtils.openAppendStream(testLogFile);

        byte[] firstChunkBytes = FIRST_CHUNK.getBytes();
        byte[] secondChunkBytes = SECOND_CHUNK.getBytes();
        byte[] thirdChunkBytes = THIRD_CHUNK.getBytes();

        FileUtils.writeToStream(stream, firstChunkBytes);
        FileUtils.writeToStream(stream, secondChunkBytes);
        FileUtils.writeToStream(stream, thirdChunkBytes);
        FileUtils.closeStream(stream);

        byte[] readBackData = FileUtils.readAllBytes(testLogFile);

        byte[] expectedData = new byte[firstChunkBytes.length + secondChunkBytes.length + thirdChunkBytes.length];
        System.arraycopy(firstChunkBytes, 0, expectedData, 0, firstChunkBytes.length);
        System.arraycopy(secondChunkBytes, 0, expectedData, firstChunkBytes.length, secondChunkBytes.length);
        System.arraycopy(thirdChunkBytes, 0, expectedData, firstChunkBytes.length + secondChunkBytes.length, thirdChunkBytes.length);

        assertArrayEquals(expectedData, readBackData);
    }

    @Test
    void closeStreamPreventsWritesAfterClosure() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        FileStream stream = FileUtils.openAppendStream(testLogFile);

        FileUtils.writeToStream(stream, "data".getBytes());
        FileUtils.closeStream(stream);

        assertThrows(
                IOException.class,
                () -> FileUtils.writeToStream(stream, "more".getBytes()));
    }

    @Test
    void fsyncStreamForcesDataToDisk() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        FileStream stream = FileUtils.openAppendStream(testLogFile);

        byte[] durableData = DURABLE_DATA.getBytes();
        FileUtils.writeToStream(stream, durableData);
        FileUtils.fsyncStream(stream);
        FileUtils.closeStream(stream);

        byte[] readBackData = FileUtils.readAllBytes(testLogFile);
        assertArrayEquals(durableData, readBackData);
    }

    @Test
    void fsyncCanBeCalledBeforeSubsequentWrites() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        FileStream stream = FileUtils.openAppendStream(testLogFile);

        FileUtils.writeToStream(stream, BEFORE_FSYNC.getBytes());
        FileUtils.fsyncStream(stream);
        FileUtils.writeToStream(stream, AFTER_FSYNC.getBytes());
        FileUtils.closeStream(stream);

        byte[] readBackData = FileUtils.readAllBytes(testLogFile);
        int expectedLength = BEFORE_FSYNC.length() + AFTER_FSYNC.length();
        assertEquals(expectedLength, readBackData.length);
    }

    @Test
    void fsyncCanBeCalledMultipleTimes() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        FileStream stream = FileUtils.openAppendStream(testLogFile);

        FileUtils.writeToStream(stream, FIRST_WRITE.getBytes());
        FileUtils.fsyncStream(stream);

        FileUtils.writeToStream(stream, SECOND_WRITE.getBytes());
        FileUtils.fsyncStream(stream);

        FileUtils.writeToStream(stream, THIRD_WRITE.getBytes());
        FileUtils.fsyncStream(stream);

        FileUtils.closeStream(stream);

        byte[] readBackData = FileUtils.readAllBytes(testLogFile);
        assertArrayEquals("123".getBytes(), readBackData);
    }

    @Test
    void readAllBytesReturnsExactDataWritten() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        byte[] originalData = HELLO_WORLD.getBytes();

        FileStream stream = FileUtils.openAppendStream(testLogFile);
        FileUtils.writeToStream(stream, originalData);
        FileUtils.closeStream(stream);

        byte[] readBackData = FileUtils.readAllBytes(testLogFile);
        assertArrayEquals(originalData, readBackData);
    }

    @Test
    void readAllBytesHandlesLargeFiles() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        byte[] largeData = new byte[10000];
        for (int byteIndex = 0; byteIndex < largeData.length; byteIndex++) {
            largeData[byteIndex] = (byte) (byteIndex % 256);
        }

        FileStream stream = FileUtils.openAppendStream(testLogFile);
        FileUtils.writeToStream(stream, largeData);
        FileUtils.closeStream(stream);

        byte[] readBackData = FileUtils.readAllBytes(testLogFile);
        assertArrayEquals(largeData, readBackData);
    }

    @Test
    void readBytesReturnsSubsetOfFile() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        byte[] fullData = HELLO_WORLD.getBytes();

        FileStream stream = FileUtils.openAppendStream(testLogFile);
        FileUtils.writeToStream(stream, fullData);
        FileUtils.closeStream(stream);

        int offsetStart = 6;
        int lengthToRead = 5;
        byte[] readBackData = FileUtils.readBytes(testLogFile, offsetStart, lengthToRead);

        assertEquals(lengthToRead, readBackData.length);
        assertArrayEquals("world".getBytes(), readBackData);
    }

    @Test
    void fileExistsReturnsTrueForExistingFile() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        FileStream stream = FileUtils.openAppendStream(testLogFile);
        FileUtils.closeStream(stream);

        assertTrue(FileUtils.fileExists(testLogFile));
    }

    @Test
    void fileExistsReturnsFalseForNonExistentFile() {
        File nonExistentFile = new File(tempLogDirectory.toFile(), NONEXISTENT_FILE);
        assertFalse(FileUtils.fileExists(nonExistentFile));
    }

    @Test
    void getFileSizeReturnsEmptyForNewFile() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        FileStream stream = FileUtils.openAppendStream(testLogFile);

        assertEquals(EMPTY_FILE_SIZE, FileUtils.getFileSize(testLogFile));

        byte[] data = "hello".getBytes();
        FileUtils.writeToStream(stream, data);
        FileUtils.closeStream(stream);

        assertEquals(data.length, FileUtils.getFileSize(testLogFile));
    }

    @Test
    void deleteFileSucceedsForExistingFile() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        FileStream stream = FileUtils.openAppendStream(testLogFile);
        FileUtils.closeStream(stream);

        assertTrue(FileUtils.fileExists(testLogFile));

        boolean deleteResult = FileUtils.deleteFile(testLogFile);

        assertTrue(deleteResult);
        assertFalse(FileUtils.fileExists(testLogFile));
    }

    @Test
    void deleteFileReturnsFalseForNonExistentFile() throws IOException {
        File nonExistentFile = new File(tempLogDirectory.toFile(), NONEXISTENT_FILE);

        assertFalse(FileUtils.fileExists(nonExistentFile));

        boolean deleteResult = FileUtils.deleteFile(nonExistentFile);

        assertFalse(deleteResult);
    }

    @Test
    void createDirectoryCreatesNewDirectory() throws IOException {
        String newDirectoryPath = tempLogDirectory.toString() + "/" + NEWDIR_SUBDIRECTORY;

        assertFalse(Files.exists(Path.of(newDirectoryPath)));

        FileUtils.createDirectory(newDirectoryPath);

        assertTrue(Files.exists(Path.of(newDirectoryPath)));
        assertTrue(Files.isDirectory(Path.of(newDirectoryPath)));
    }

    @Test
    void createDirectorySucceedsWhenDirectoryAlreadyExists() throws IOException {
        String existingDirectoryPath = tempLogDirectory.toString();

        assertTrue(Files.exists(Path.of(existingDirectoryPath)));

        FileUtils.createDirectory(existingDirectoryPath);

        assertTrue(Files.exists(Path.of(existingDirectoryPath)));
    }

    @Test
    void getLogFileConstructsAbsolutePath() {
        String directoryPath = "/tmp/logs";
        String logFilename = "test.log";

        File logFile = FileUtils.getLogFile(directoryPath, logFilename);

        assertEquals("/tmp/logs/test.log", logFile.getAbsolutePath());
    }

    @Test
    void listLogFilesReturnsOnlyLogFiles() throws IOException {
        String logsDirectoryPath = tempLogDirectory.toString() + "/" + LOGS_SUBDIRECTORY;
        FileUtils.createDirectory(logsDirectoryPath);

        Files.write(Path.of(logsDirectoryPath, WALFILE_001), SEGMENT_CONTENT.getBytes());
        Files.write(Path.of(logsDirectoryPath, WALFILE_002), SEGMENT_CONTENT.getBytes());
        Files.write(Path.of(logsDirectoryPath, WALFILE_003), SEGMENT_CONTENT.getBytes());
        Files.write(Path.of(logsDirectoryPath, OTHER_FILE), NOT_LOG_CONTENT.getBytes());

        List<File> logFiles = FileUtils.listLogFiles(logsDirectoryPath);

        assertEquals(EXPECTED_LOG_FILE_COUNT, logFiles.size());
        assertTrue(logFiles.stream().allMatch(f -> f.getName().endsWith(LOG_FILE_EXTENSION)));
    }

    @Test
    void listLogFilesReturnsSortedResults() throws IOException {
        String logsDirectoryPath = tempLogDirectory.toString() + "/" + LOGS_SUBDIRECTORY;
        FileUtils.createDirectory(logsDirectoryPath);

        Files.write(Path.of(logsDirectoryPath, WALFILE_003), SEGMENT_CONTENT.getBytes());
        Files.write(Path.of(logsDirectoryPath, WALFILE_001), SEGMENT_CONTENT.getBytes());
        Files.write(Path.of(logsDirectoryPath, WALFILE_002), SEGMENT_CONTENT.getBytes());

        List<File> logFiles = FileUtils.listLogFiles(logsDirectoryPath);

        assertEquals(EXPECTED_LOG_FILE_COUNT, logFiles.size());
        assertEquals(WALFILE_001, logFiles.get(0).getName());
        assertEquals(WALFILE_002, logFiles.get(1).getName());
        assertEquals(WALFILE_003, logFiles.get(2).getName());
    }

    @Test
    void listLogFilesReturnsEmptyListForEmptyDirectory() throws IOException {
        List<File> logFiles = FileUtils.listLogFiles(tempLogDirectory.toString());

        assertTrue(logFiles.isEmpty());
    }

    @Test
    void listLogFilesReturnsEmptyListForNonExistentDirectory() {
        List<File> logFiles = FileUtils.listLogFiles("/nonexistent/directory");

        assertTrue(logFiles.isEmpty());
    }

    @Test
    void writeToClosedStreamThrowsIOException() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        FileStream stream = FileUtils.openAppendStream(testLogFile);
        FileUtils.closeStream(stream);

        assertThrows(
                IOException.class,
                () -> FileUtils.writeToStream(stream, "data".getBytes()));
    }

    @Test
    void fsyncClosedStreamThrowsIOException() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        FileStream stream = FileUtils.openAppendStream(testLogFile);
        FileUtils.closeStream(stream);

        assertThrows(
                IOException.class,
                () -> FileUtils.fsyncStream(stream));
    }

    @Test
    void readNonExistentFileThrowsIOException() {
        File nonExistentFile = new File(tempLogDirectory.toFile(), NONEXISTENT_FILE);

        assertThrows(IOException.class, () -> FileUtils.readAllBytes(nonExistentFile));
    }

    @Test
    void readBytesWithNegativeOffsetThrowsIOException() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        FileStream stream = FileUtils.openAppendStream(testLogFile);
        FileUtils.writeToStream(stream, "data".getBytes());
        FileUtils.closeStream(stream);

        assertThrows(IOException.class, () -> FileUtils.readBytes(testLogFile, -1, 5));
    }

    @Test
    void dataIntegrityAfterFsyncRoundTrip() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);
        byte[] originalData = DATA_INTEGRITY_MESSAGE.getBytes();

        FileStream stream = FileUtils.openAppendStream(testLogFile);
        FileUtils.writeToStream(stream, originalData);
        FileUtils.fsyncStream(stream);
        FileUtils.closeStream(stream);

        byte[] readBackData = FileUtils.readAllBytes(testLogFile);
        assertArrayEquals(originalData, readBackData);
    }

    @Test
    void binaryDataBytesPreservedExactly() throws IOException {
        File testLogFile = new File(tempLogDirectory.toFile(), TEST_LOG_FILENAME);

        byte[] binaryData = new byte[BINARY_BYTE_RANGE];
        for (int byteIndex = 0; byteIndex < BINARY_BYTE_RANGE; byteIndex++) {
            binaryData[byteIndex] = (byte) byteIndex;
        }

        FileStream stream = FileUtils.openAppendStream(testLogFile);
        FileUtils.writeToStream(stream, binaryData);
        FileUtils.closeStream(stream);

        byte[] readBackData = FileUtils.readAllBytes(testLogFile);
        assertArrayEquals(binaryData, readBackData);
    }
}