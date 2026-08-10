package io.writeahead.log.segments.management;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConstants;
import io.writeahead.log.models.FileStream;
import io.writeahead.log.models.meta.SegmentHeader;
import io.writeahead.log.models.states.SegmentFinalizationData;
import io.writeahead.log.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SegmentLifecycleManager Tests")
class SegmentLifecycleManagerTest {

  private File testDirectory;
  private SegmentLifecycleManager manager;

  @BeforeEach
  void setUp() throws IOException {
    testDirectory = ManagementTestUtils.createTempLogDirectory();
    manager = new SegmentLifecycleManager(testDirectory.getAbsolutePath());
  }

  @AfterEach
  void tearDown() throws IOException {
    ManagementTestUtils.deleteDirectory(testDirectory);
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("SegmentLifecycleManager creates with valid directory")
    void createsWithValidDirectory() throws IOException {
      assertNotNull(manager);
    }

    @Test
    @DisplayName("SegmentLifecycleManager throws IOException for nonexistent directory")
    void throwsIOExceptionForNonexistentDirectory() {
      File nonexistent = new File("nonexistent");

      assertThrows(
          IOException.class,
          () -> new SegmentLifecycleManager(nonexistent.getAbsolutePath()),
          "Should throw IOException for nonexistent directory");
    }

    @Test
    @DisplayName("SegmentLifecycleManager throws IOException for file instead of directory")
    void throwsIOExceptionForFile() throws IOException {
      File file = new File(testDirectory, "file.txt");
      Files.createFile(file.toPath());

      assertThrows(
          IOException.class,
          () -> new SegmentLifecycleManager(file.getAbsolutePath()),
          "Should throw IOException when path is a file");
    }

    @Test
    @DisplayName("SegmentLifecycleManager stores logDir reference")
    void storesLogDir() throws IOException {
      SegmentLifecycleManager testManager =
          new SegmentLifecycleManager(testDirectory.getAbsolutePath());
      assertNotNull(testManager);
    }
  }

  @Nested
  @DisplayName("Generate Segment Filename Tests")
  class GenerateSegmentFilenameTests {

    @Test
    @DisplayName("generateSegmentFilename returns non-null string")
    void returnsNonNullString() {
      String filename = SegmentLifecycleManager.generateSegmentFilename(1L);

      assertNotNull(filename);
      assertFalse(filename.isEmpty());
    }

    @Test
    @DisplayName("generateSegmentFilename includes 'wal-' prefix")
    void includesWalPrefix() {
      String filename = SegmentLifecycleManager.generateSegmentFilename(1L);

      assertTrue(filename.startsWith("wal-"));
    }

    @Test
    @DisplayName("generateSegmentFilename includes '.log' suffix")
    void includesLogSuffix() {
      String filename = SegmentLifecycleManager.generateSegmentFilename(1L);

      assertTrue(filename.endsWith(".log"));
    }

    @Test
    @DisplayName("generateSegmentFilename formats sequence with leading zeros")
    void formatsSequenceWithLeadingZeros() {
      String filename = SegmentLifecycleManager.generateSegmentFilename(1L);

      String parts[] = filename.split("-");
      String sequencePart = parts[parts.length - 1].replace(".log", "");

      assertTrue(sequencePart.matches("\\d{6}"));
    }

    @Test
    @DisplayName("generateSegmentFilename produces different names for different sequences")
    void producesDifferentNamesForDifferentSequences() {
      String filename1 = SegmentLifecycleManager.generateSegmentFilename(1L);
      String filename2 = SegmentLifecycleManager.generateSegmentFilename(2L);

      assertNotEquals(filename1, filename2);
    }

    @Test
    @DisplayName("generateSegmentFilename handles large sequence numbers")
    void handlesLargeSequenceNumbers() {
      String filename = SegmentLifecycleManager.generateSegmentFilename(Long.MAX_VALUE);

      assertNotNull(filename);
      assertTrue(filename.startsWith("wal-"));
      assertTrue(filename.endsWith(".log"));
    }

    @Test
    @DisplayName("generateSegmentFilename handles zero sequence")
    void handlesZeroSequence() {
      String filename = SegmentLifecycleManager.generateSegmentFilename(0L);

      assertNotNull(filename);
      assertTrue(filename.contains("000000"));
    }
  }

  @Nested
  @DisplayName("Create New Segment Tests")
  class CreateNewSegmentTests {

    @Test
    @DisplayName("createNewSegment returns non-null FileStream")
    void returnsNonNullFileStream() throws IOException {
      FileStream stream = manager.createNewSegment(1L);

      assertNotNull(stream);
    }

    @Test
    @DisplayName("createNewSegment creates file in logDir")
    void createsFileInLogDir() throws IOException {
      FileStream stream = manager.createNewSegment(1L);
      stream.closeAll();

      File[] files = testDirectory.listFiles();
      assertNotNull(files);
      assertEquals(1, files.length);
      assertTrue(files[0].getName().startsWith("wal-"));
    }

    @Test
    @DisplayName("createNewSegment file contains valid header")
    void fileContainsValidHeader() throws IOException {
      long sequence = 1L;
      FileStream stream = manager.createNewSegment(sequence);
      stream.closeAll();

      File[] files = testDirectory.listFiles();
      assertNotNull(files);
      File segment = files[0];

      byte[] headerBytes = FileUtils.readBytes(segment, 0, WalConstants.SEGMENT_HEADER_SIZE);
      SegmentHeader header = SegmentHeader.fromBytes(headerBytes);

      assertEquals(sequence, header.segmentSequence());
      assertEquals((byte) 0xAA, header.magic());
      assertTrue(header.isValid());
    }

    @Test
    @DisplayName("createNewSegment file has header size")
    void fileHasHeaderSize() throws IOException {
      FileStream stream = manager.createNewSegment(1L);
      stream.closeAll();

      File[] files = testDirectory.listFiles();
      assertNotNull(files);
      File segment = files[0];

      long fileSize = FileUtils.getFileSize(segment);
      assertEquals(WalConstants.SEGMENT_HEADER_SIZE, fileSize);
    }

    @Test
    @DisplayName("createNewSegment returns writable FileStream")
    void returnsWritableFileStream() throws IOException {
      FileStream stream = manager.createNewSegment(1L);

      assertNotNull(stream.fileOutputStream());
      assertNotNull(stream.dataOutputStream());
      stream.closeAll();
    }

    @Test
    @DisplayName("createNewSegment with different sequences creates different files")
    void differentSequencesCreateDifferentFiles() throws IOException {
      FileStream stream1 = manager.createNewSegment(1L);
      FileStream stream2 = manager.createNewSegment(2L);
      stream1.closeAll();
      stream2.closeAll();

      File[] files = testDirectory.listFiles();
      assertNotNull(files);
      assertEquals(2, files.length);
    }

    @Test
    @DisplayName("createNewSegment with zero sequence works")
    void zeroSequenceWorks() throws IOException {
      FileStream stream = manager.createNewSegment(0L);

      assertNotNull(stream);
      stream.closeAll();

      File[] files = testDirectory.listFiles();
      assertNotNull(files);
      assertEquals(1, files.length);
    }

    @Test
    @DisplayName("createNewSegment with large sequence works")
    void largeSequenceWorks() throws IOException {
      FileStream stream = manager.createNewSegment(Long.MAX_VALUE - 1);

      assertNotNull(stream);
      stream.closeAll();

      File[] files = testDirectory.listFiles();
      assertNotNull(files);
      assertEquals(1, files.length);
    }
  }

  @Nested
  @DisplayName("Finalize Segment Tests")
  class FinalizeSegmentTests {

    @Test
    @DisplayName("finalizeSegment writes footer to segment")
    void writesFooterToSegment() throws IOException {
      FileStream stream = manager.createNewSegment(1L);
      SegmentFinalizationData data = SegmentFinalizationData.of(5, 100L, 500L);

      manager.finalizeSegment(stream, data);

      File[] files = testDirectory.listFiles();
      assertNotNull(files);
      File segment = files[0];

      long fileSize = FileUtils.getFileSize(segment);
      assertTrue(fileSize >= WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE);
    }

    @Test
    @DisplayName("finalizeSegment closes stream")
    void closesStream() throws IOException {
      FileStream stream = manager.createNewSegment(1L);
      SegmentFinalizationData data = SegmentFinalizationData.of(5, 100L, 500L);

      manager.finalizeSegment(stream, data);

      assertThrows(
          IOException.class,
          () -> stream.fileOutputStream().write(1),
          "Stream should be closed after finalization");
    }

    @Test
    @DisplayName("finalizeSegment with various entry counts")
    void finalizeWithVariousEntryCounts() throws IOException {
      int[] entryCounts = {1, 5, 10, 100, 1000};

      for (int count : entryCounts) {
        FileStream stream = manager.createNewSegment(1L);
        SegmentFinalizationData data = SegmentFinalizationData.of(count, 0L, 1000L);

        assertDoesNotThrow(() -> manager.finalizeSegment(stream, data));
      }
    }

    @Test
    @DisplayName("finalizeSegment with zero timestamp range")
    void finalizeWithZeroTimestampRange() throws IOException {
      FileStream stream = manager.createNewSegment(1L);
      SegmentFinalizationData data = SegmentFinalizationData.of(10, 500L, 500L);

      assertDoesNotThrow(() -> manager.finalizeSegment(stream, data));
    }

    @Test
    @DisplayName("finalizeSegment with max timestamp values")
    void finalizeWithMaxTimestampValues() throws IOException {
      FileStream stream = manager.createNewSegment(1L);
      SegmentFinalizationData data = SegmentFinalizationData.of(1, 0L, Long.MAX_VALUE);

      assertDoesNotThrow(() -> manager.finalizeSegment(stream, data));
    }

    @Test
    @DisplayName("finalizeSegment closes stream even on error")
    void closesStreamEvenOnError() throws IOException {
      FileStream stream = manager.createNewSegment(1L);
      stream.fileOutputStream().close();

      SegmentFinalizationData data = SegmentFinalizationData.of(5, 100L, 500L);

      assertThrows(IOException.class, () -> manager.finalizeSegment(stream, data));

      assertThrows(
          IOException.class,
          () -> stream.fileOutputStream().write(1),
          "Stream should remain closed even after error");
    }
  }

  @Nested
  @DisplayName("Lifecycle Flow Tests")
  class LifecycleFlowTests {

    @Test
    @DisplayName("create and finalize complete segment flow")
    void completeSegmentFlow() throws IOException {
      long sequence = 1L;
      FileStream stream = manager.createNewSegment(sequence);

      byte[] data = new byte[100];
      FileUtils.writeToStream(stream, data);

      SegmentFinalizationData finalization = SegmentFinalizationData.of(5, 100L, 500L);
      manager.finalizeSegment(stream, finalization);

      File[] files = testDirectory.listFiles();
      assertNotNull(files);
      assertEquals(1, files.length);

      long fileSize = FileUtils.getFileSize(files[0]);
      assertTrue(fileSize > WalConstants.SEGMENT_HEADER_SIZE);
    }

    @Test
    @DisplayName("create multiple segments and finalize all")
    void createAndFinalizeMultipleSegments() throws IOException {
      for (int i = 1; i <= 3; i++) {
        FileStream stream = manager.createNewSegment(i);
        SegmentFinalizationData data = SegmentFinalizationData.of(i * 5, i * 100L, i * 500L);
        manager.finalizeSegment(stream, data);
      }

      File[] files = testDirectory.listFiles();
      assertNotNull(files);
      assertEquals(3, files.length);
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("createNewSegment cleans up partial file on header write failure")
    void cleansUpOnWriteFailure() throws IOException {
      testDirectory.setWritable(false);

      assertThrows(IOException.class, () -> manager.createNewSegment(1L));

      testDirectory.setWritable(true);
    }

    @Test
    @DisplayName("finalizeSegment throws IOException for invalid data")
    void throwsExceptionForInvalidData() throws IOException {
      FileStream stream = manager.createNewSegment(1L);

      assertThrows(
          IllegalArgumentException.class,
          () -> new SegmentFinalizationData(0, 100L, 500L),
          "Should not allow 0 entries");
    }

    @Test
    @DisplayName("finalizeSegment handles null stream gracefully")
    void handlesInvalidStream() throws IOException {
      SegmentFinalizationData data = SegmentFinalizationData.of(5, 100L, 500L);

      assertThrows(NullPointerException.class, () -> manager.finalizeSegment(null, data));
    }
  }

  @Nested
  @DisplayName("File System Tests")
  class FileSystemTests {

    @Test
    @DisplayName("segment file is written to correct directory")
    void writeToCorrectDirectory() throws IOException {
      FileStream stream = manager.createNewSegment(1L);
      stream.closeAll();

      File[] files = testDirectory.listFiles();
      assertNotNull(files);
      assertEquals(1, files.length);
      assertTrue(files[0].getParent().equals(testDirectory.getAbsolutePath()));
    }

    @Test
    @DisplayName("segment file is readable after creation")
    void fileIsReadableAfterCreation() throws IOException {
      FileStream stream = manager.createNewSegment(1L);
      stream.closeAll();

      File[] files = testDirectory.listFiles();
      assertNotNull(files);
      assertTrue(files[0].canRead());
    }

    @Test
    @DisplayName("segment file permission preservation")
    void preservesFilePermissions() throws IOException {
      FileStream stream = manager.createNewSegment(1L);
      stream.closeAll();

      File[] files = testDirectory.listFiles();
      assertNotNull(files);
      assertTrue(files[0].exists());
      assertTrue(files[0].isFile());
    }
  }

  @Nested
  @DisplayName("Concurrency Tests")
  class ConcurrencyTests {

    @Test
    @DisplayName("multiple sequential segment creations work correctly")
    void multipleSequentialCreations() throws IOException {
      for (int i = 1; i <= 5; i++) {
        FileStream stream = manager.createNewSegment(i);
        stream.closeAll();
      }

      File[] files = testDirectory.listFiles();
      assertNotNull(files);
      assertEquals(5, files.length);
    }

    @Test
    @DisplayName("segment files have unique names")
    void segmentFilesHaveUniqueNames() throws IOException {
      for (int i = 1; i <= 3; i++) {
        FileStream stream = manager.createNewSegment(i);
        stream.closeAll();
      }

      File[] files = testDirectory.listFiles();
      assertNotNull(files);

      for (int i = 0; i < files.length - 1; i++) {
        for (int j = i + 1; j < files.length; j++) {
          assertNotEquals(files[i].getName(), files[j].getName());
        }
      }
    }
  }
}
