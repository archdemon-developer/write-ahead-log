package io.writeahead.log.utils;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.file.FileStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MILITARY-GRADE FILE I/O TESTS FOR FileUtils
 *
 * <p>FileUtils handles all file operations. If wrong:
 * - Data loss on write
 * - Crash on read
 * - Durability failures (fsync)
 * - Directory operations fail
 *
 * <p>These tests verify file I/O correctness for production reliability.
 */
public class FileUtilsTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("wal-fileutils-test-");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(
                        path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                // Ignore
                            }
                        });
    }

    // ============================================================================
    // SECTION 1: OPEN/WRITE/CLOSE STREAM LIFECYCLE
    // ============================================================================

    @Test
    void testOpenAppendStreamCreatesFile() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");

        FileStream stream = FileUtils.openAppendStream(file);

        assertNotNull(stream, "Should return valid stream");
        assertTrue(file.exists(), "File should be created");
        assertNotNull(stream.fileOutputStream(), "Should have file output stream");
        assertNotNull(stream.dataOutputStream(), "Should have data output stream");

        FileUtils.closeStream(stream);
    }

    @Test
    void testOpenAppendStreamAppendMode() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");

        // Write initial data
        FileStream stream1 = FileUtils.openAppendStream(file);
        FileUtils.writeToStream(stream1, "initial".getBytes());
        FileUtils.closeStream(stream1);

        long sizeAfterFirst = FileUtils.getFileSize(file);

        // Append more data (should not truncate)
        FileStream stream2 = FileUtils.openAppendStream(file);
        FileUtils.writeToStream(stream2, "appended".getBytes());
        FileUtils.closeStream(stream2);

        long sizeAfterSecond = FileUtils.getFileSize(file);

        // Should have appended, not replaced
        assertTrue(sizeAfterSecond > sizeAfterFirst, "Append mode should add to file, not replace");
        assertEquals(
                sizeAfterFirst + "appended".length(), sizeAfterSecond, "Size should match initial + appended");
    }

    @Test
    void testWriteToStreamWritesData() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        FileStream stream = FileUtils.openAppendStream(file);

        byte[] data = "hello world".getBytes();
        FileUtils.writeToStream(stream, data);
        FileUtils.closeStream(stream);

        byte[] readBack = FileUtils.readAllBytes(file);
        assertArrayEquals(data, readBack, "Written data should match read data");
    }

    @Test
    void testWriteMultipleChunks() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        FileStream stream = FileUtils.openAppendStream(file);

        byte[] chunk1 = "first".getBytes();
        byte[] chunk2 = "second".getBytes();
        byte[] chunk3 = "third".getBytes();

        FileUtils.writeToStream(stream, chunk1);
        FileUtils.writeToStream(stream, chunk2);
        FileUtils.writeToStream(stream, chunk3);
        FileUtils.closeStream(stream);

        byte[] readBack = FileUtils.readAllBytes(file);

        // Reconstruct expected
        byte[] expected = new byte[chunk1.length + chunk2.length + chunk3.length];
        System.arraycopy(chunk1, 0, expected, 0, chunk1.length);
        System.arraycopy(chunk2, 0, expected, chunk1.length, chunk2.length);
        System.arraycopy(chunk3, 0, expected, chunk1.length + chunk2.length, chunk3.length);

        assertArrayEquals(expected, readBack, "Multiple writes should be sequential");
    }

    @Test
    void testCloseStreamClosesFile() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        FileStream stream = FileUtils.openAppendStream(file);

        FileUtils.writeToStream(stream, "data".getBytes());
        FileUtils.closeStream(stream);

        // After close, writing should fail
        assertThrows(
                IOException.class,
                () -> FileUtils.writeToStream(stream, "more".getBytes()),
                "Should not be able to write to closed stream");
    }

    // ============================================================================
    // SECTION 2: FSYNC DURABILITY
    // ============================================================================

    @Test
    void testFsyncStreamDurability() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        FileStream stream = FileUtils.openAppendStream(file);

        byte[] data = "durable".getBytes();
        FileUtils.writeToStream(stream, data);
        FileUtils.fsyncStream(stream); // Force to disk
        FileUtils.closeStream(stream);

        // Verify data persisted
        byte[] readBack = FileUtils.readAllBytes(file);
        assertArrayEquals(data, readBack, "Fsynced data should persist");
    }

    @Test
    void testFsyncBeforeClose() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        FileStream stream = FileUtils.openAppendStream(file);

        FileUtils.writeToStream(stream, "before fsync".getBytes());
        FileUtils.fsyncStream(stream); // Explicit fsync
        FileUtils.writeToStream(stream, " after fsync".getBytes());
        FileUtils.closeStream(stream);

        byte[] readBack = FileUtils.readAllBytes(file);
        assertEquals(
                "before fsync after fsync".length(),
                readBack.length,
                "Both pre-fsync and post-fsync data should exist");
    }

    @Test
    void testFsyncMultipleTimes() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        FileStream stream = FileUtils.openAppendStream(file);

        FileUtils.writeToStream(stream, "1".getBytes());
        FileUtils.fsyncStream(stream);

        FileUtils.writeToStream(stream, "2".getBytes());
        FileUtils.fsyncStream(stream);

        FileUtils.writeToStream(stream, "3".getBytes());
        FileUtils.fsyncStream(stream);

        FileUtils.closeStream(stream);

        byte[] readBack = FileUtils.readAllBytes(file);
        assertArrayEquals("123".getBytes(), readBack, "Multiple fsyncs should persist all data");
    }

    // ============================================================================
    // SECTION 3: READ OPERATIONS
    // ============================================================================

    @Test
    void testReadAllBytesSimple() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        byte[] original = "hello world".getBytes();

        FileStream stream = FileUtils.openAppendStream(file);
        FileUtils.writeToStream(stream, original);
        FileUtils.closeStream(stream);

        byte[] readBack = FileUtils.readAllBytes(file);
        assertArrayEquals(original, readBack, "Read bytes should match written");
    }

    @Test
    void testReadAllBytesLargeFile() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        byte[] largeData = new byte[10000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        FileStream stream = FileUtils.openAppendStream(file);
        FileUtils.writeToStream(stream, largeData);
        FileUtils.closeStream(stream);

        byte[] readBack = FileUtils.readAllBytes(file);
        assertArrayEquals(largeData, readBack, "Large file read should match");
    }

    @Test
    void testReadBytesWithOffset() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        byte[] data = "0123456789".getBytes();

        FileStream stream = FileUtils.openAppendStream(file);
        FileUtils.writeToStream(stream, data);
        FileUtils.closeStream(stream);

        byte[] middle = FileUtils.readBytes(file, 3, 4);
        assertArrayEquals("3456".getBytes(), middle, "Should read bytes 3-6");
    }

    @Test
    void testReadBytesFromStart() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        byte[] data = "0123456789".getBytes();

        FileStream stream = FileUtils.openAppendStream(file);
        FileUtils.writeToStream(stream, data);
        FileUtils.closeStream(stream);

        byte[] start = FileUtils.readBytes(file, 0, 5);
        assertArrayEquals("01234".getBytes(), start, "Should read from start");
    }

    @Test
    void testReadBytesFromEnd() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        byte[] data = "0123456789".getBytes();

        FileStream stream = FileUtils.openAppendStream(file);
        FileUtils.writeToStream(stream, data);
        FileUtils.closeStream(stream);

        byte[] end = FileUtils.readBytes(file, 5, 5);
        assertArrayEquals("56789".getBytes(), end, "Should read from end");
    }

    @Test
    void testReadBytesInsufficientData() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        FileStream stream = FileUtils.openAppendStream(file);
        FileUtils.writeToStream(stream, "short".getBytes());
        FileUtils.closeStream(stream);

        // Try to read more bytes than file contains
        assertThrows(
                IOException.class,
                () -> FileUtils.readBytes(file, 0, 100),
                "Should throw when trying to read past EOF");
    }

    // ============================================================================
    // SECTION 4: FILE OPERATIONS (EXISTS, SIZE, DELETE)
    // ============================================================================

    @Test
    void testFileExists() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");

        assertFalse(FileUtils.fileExists(file), "File should not exist initially");

        FileStream stream = FileUtils.openAppendStream(file);
        FileUtils.closeStream(stream);

        assertTrue(FileUtils.fileExists(file), "File should exist after creation");
    }

    @Test
    void testGetFileSize() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        FileStream stream = FileUtils.openAppendStream(file);

        assertEquals(0, FileUtils.getFileSize(file), "Empty file should be 0 bytes");

        byte[] data = "hello".getBytes();
        FileUtils.writeToStream(stream, data);
        FileUtils.closeStream(stream);

        assertEquals(data.length, FileUtils.getFileSize(file), "File size should match data length");
    }

    @Test
    void testDeleteFile() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        FileStream stream = FileUtils.openAppendStream(file);
        FileUtils.closeStream(stream);

        assertTrue(FileUtils.fileExists(file), "File should exist before delete");

        boolean deleted = FileUtils.deleteFile(file);

        assertTrue(deleted, "Delete should succeed");
        assertFalse(FileUtils.fileExists(file), "File should not exist after delete");
    }

    @Test
    void testDeleteNonExistentFile() throws IOException {
        File file = new File(tempDir.toFile(), "nonexistent.log");

        assertFalse(FileUtils.fileExists(file), "File should not exist");

        boolean deleted = FileUtils.deleteFile(file);

        assertFalse(deleted, "Delete should return false for non-existent file");
    }

    // ============================================================================
    // SECTION 5: DIRECTORY OPERATIONS
    // ============================================================================

    @Test
    void testCreateDirectory() throws IOException {
        String newDir = tempDir.toString() + "/newdir";

        assertFalse(Files.exists(Path.of(newDir)), "Directory should not exist initially");

        FileUtils.createDirectory(newDir);

        assertTrue(Files.exists(Path.of(newDir)), "Directory should be created");
        assertTrue(Files.isDirectory(Path.of(newDir)), "Should be a directory");
    }

    @Test
    void testCreateDirectoryAlreadyExists() throws IOException {
        String existingDir = tempDir.toString();

        assertTrue(Files.exists(Path.of(existingDir)), "Directory exists");

        FileUtils.createDirectory(existingDir); // Should not throw

        assertTrue(Files.exists(Path.of(existingDir)), "Directory should still exist");
    }

    @Test
    void testGetLogFile() {
        String directory = "/tmp/logs";
        String filename = "test.log";

        File file = FileUtils.getLogFile(directory, filename);

        assertEquals("/tmp/logs/test.log", file.getAbsolutePath(), "Path should be concatenated");
    }

    @Test
    void testListLogFiles() throws IOException {
        FileUtils.createDirectory(tempDir.toString() + "/logs");
        String logsDir = tempDir.toString() + "/logs";

        // Create some log files
        Files.write(Path.of(logsDir, "wal-001.log"), "content1".getBytes());
        Files.write(Path.of(logsDir, "wal-002.log"), "content2".getBytes());
        Files.write(Path.of(logsDir, "wal-003.log"), "content3".getBytes());

        // Create non-log file
        Files.write(Path.of(logsDir, "other.txt"), "not a log".getBytes());

        java.util.List<File> logFiles = FileUtils.listLogFiles(logsDir);

        assertEquals(3, logFiles.size(), "Should find 3 log files");
        assertTrue(
                logFiles.stream().allMatch(f -> f.getName().endsWith(".log")),
                "All files should be .log");
    }

    @Test
    void testListLogFilesSorted() throws IOException {
        FileUtils.createDirectory(tempDir.toString() + "/logs");
        String logsDir = tempDir.toString() + "/logs";

        // Create log files in random order
        Files.write(Path.of(logsDir, "wal-003.log"), "content".getBytes());
        Files.write(Path.of(logsDir, "wal-001.log"), "content".getBytes());
        Files.write(Path.of(logsDir, "wal-002.log"), "content".getBytes());

        java.util.List<File> logFiles = FileUtils.listLogFiles(logsDir);

        assertEquals(3, logFiles.size(), "Should find 3 files");
        assertEquals("wal-001.log", logFiles.get(0).getName(), "First should be wal-001.log");
        assertEquals("wal-002.log", logFiles.get(1).getName(), "Second should be wal-002.log");
        assertEquals("wal-003.log", logFiles.get(2).getName(), "Third should be wal-003.log");
    }

    @Test
    void testListLogFilesEmptyDirectory() throws IOException {
        java.util.List<File> logFiles = FileUtils.listLogFiles(tempDir.toString());

        assertTrue(logFiles.isEmpty(), "Empty directory should return empty list");
    }

    @Test
    void testListLogFilesNonExistentDirectory() {
        java.util.List<File> logFiles = FileUtils.listLogFiles("/nonexistent/directory");

        assertTrue(logFiles.isEmpty(), "Non-existent directory should return empty list");
    }

    // ============================================================================
    // SECTION 6: ERROR HANDLING
    // ============================================================================

    @Test
    void testWriteToClosedStreamThrows() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        FileStream stream = FileUtils.openAppendStream(file);
        FileUtils.closeStream(stream);

        assertThrows(
                IOException.class,
                () -> FileUtils.writeToStream(stream, "data".getBytes()),
                "Should throw when writing to closed stream");
    }

    @Test
    void testFsyncClosedStreamThrows() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        FileStream stream = FileUtils.openAppendStream(file);
        FileUtils.closeStream(stream);

        assertThrows(
                IOException.class,
                () -> FileUtils.fsyncStream(stream),
                "Should throw when fsyncing closed stream");
    }

    @Test
    void testReadNonExistentFile() {
        File file = new File(tempDir.toFile(), "nonexistent.log");

        assertThrows(IOException.class, () -> FileUtils.readAllBytes(file),
                "Should throw when reading non-existent file");
    }

    @Test
    void testReadBytesNegativeOffset() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        FileStream stream = FileUtils.openAppendStream(file);
        FileUtils.writeToStream(stream, "data".getBytes());
        FileUtils.closeStream(stream);

        assertThrows(IOException.class, () -> FileUtils.readBytes(file, -1, 5),
                "Should throw for negative offset");
    }

    // ============================================================================
    // SECTION 7: DATA INTEGRITY
    // ============================================================================

    @Test
    void testDataIntegrityAfterFsync() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");
        byte[] original = "integrity test data".getBytes();

        FileStream stream = FileUtils.openAppendStream(file);
        FileUtils.writeToStream(stream, original);
        FileUtils.fsyncStream(stream);
        FileUtils.closeStream(stream);

        // Read back and verify byte-for-byte
        byte[] readBack = FileUtils.readAllBytes(file);
        assertArrayEquals(original, readBack, "Data should be identical after fsync");
    }

    @Test
    void testBinaryDataPreservation() throws IOException {
        File file = new File(tempDir.toFile(), "test.log");

        byte[] binary = new byte[256];
        for (int i = 0; i < 256; i++) {
            binary[i] = (byte) i;
        }

        FileStream stream = FileUtils.openAppendStream(file);
        FileUtils.writeToStream(stream, binary);
        FileUtils.closeStream(stream);

        byte[] readBack = FileUtils.readAllBytes(file);
        assertArrayEquals(binary, readBack, "Binary data should be preserved exactly");
    }
}
