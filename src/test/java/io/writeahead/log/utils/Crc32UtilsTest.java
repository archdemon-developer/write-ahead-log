package io.writeahead.log.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Crc32Utils - CRC32 Checksum Computation")
public class Crc32UtilsTest {

  @Test
  @DisplayName("compute calculates CRC32 of byte array")
  void testComputeCalculatesCrc() {
    byte[] data = "test".getBytes();

    long crc = Crc32Utils.compute(data);

    assertTrue(crc > 0);
    assertNotEquals(0, crc);
  }

  @Test
  @DisplayName("compute produces consistent results for same data")
  void testComputeIsConsistent() {
    byte[] data = "test data".getBytes();

    long crc1 = Crc32Utils.compute(data);
    long crc2 = Crc32Utils.compute(data);

    assertEquals(crc1, crc2);
  }

  @Test
  @DisplayName("compute produces different CRCs for different data")
  void testComputeDifferentForDifferentData() {
    byte[] data1 = "test1".getBytes();
    byte[] data2 = "test2".getBytes();

    long crc1 = Crc32Utils.compute(data1);
    long crc2 = Crc32Utils.compute(data2);

    assertNotEquals(crc1, crc2);
  }

  @Test
  @DisplayName("compute handles empty byte array")
  void testComputeHandlesEmptyArray() {
    byte[] emptyData = {};

    long crc = Crc32Utils.compute(emptyData);

    assertEquals(0, crc);
  }

  @Test
  @DisplayName("compute handles single byte")
  void testComputeHandlesSingleByte() {
    byte[] singleByte = {42};

    long crc = Crc32Utils.compute(singleByte);

    assertTrue(crc >= 0);
  }

  @Test
  @DisplayName("compute handles large byte array")
  void testComputeHandlesLargeArray() {
    byte[] largeData = new byte[100000];
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }

    long crc = Crc32Utils.compute(largeData);

    assertTrue(crc >= 0);
  }

  @Test
  @DisplayName("compute handles all zero bytes")
  void testComputeAllZeros() {
    byte[] allZeros = new byte[100];

    long crc = Crc32Utils.compute(allZeros);

    assertTrue(crc >= 0);
  }

  @Test
  @DisplayName("compute handles all 0xFF bytes")
  void testComputeAllOnes() {
    byte[] allOnes = new byte[100];
    for (int i = 0; i < allOnes.length; i++) {
      allOnes[i] = (byte) 0xFF;
    }

    long crc = Crc32Utils.compute(allOnes);

    assertTrue(crc >= 0);
  }

  @Test
  @DisplayName("compute throws NullPointerException for null data")
  void testComputeThrowsForNullData() {
    assertThrows(NullPointerException.class, () -> Crc32Utils.compute(null));
  }

  @Test
  @DisplayName("computeEntryCrc calculates CRC for entry components")
  void testComputeEntryCrc() throws IOException {
    long timestamp = System.currentTimeMillis();
    int size = 10;
    byte[] data = "test data".getBytes();

    long crc = Crc32Utils.computeEntryCrc(timestamp, size, data);

    assertTrue(crc >= 0);
  }

  @Test
  @DisplayName("computeEntryCrc produces consistent results")
  void testComputeEntryCrcIsConsistent() throws IOException {
    long timestamp = System.currentTimeMillis();
    int size = 10;
    byte[] data = "test data".getBytes();

    long crc1 = Crc32Utils.computeEntryCrc(timestamp, size, data);
    long crc2 = Crc32Utils.computeEntryCrc(timestamp, size, data);

    assertEquals(crc1, crc2);
  }

  @Test
  @DisplayName("computeEntryCrc produces different results for different timestamps")
  void testComputeEntryCrcDifferentForDifferentTimestamps() throws IOException {
    int size = 10;
    byte[] data = "test data".getBytes();

    long crc1 = Crc32Utils.computeEntryCrc(100, size, data);
    long crc2 = Crc32Utils.computeEntryCrc(200, size, data);

    assertNotEquals(crc1, crc2);
  }

  @Test
  @DisplayName("computeEntryCrc produces different results for different sizes")
  void testComputeEntryCrcDifferentForDifferentSizes() throws IOException {
    long timestamp = System.currentTimeMillis();
    byte[] data = "test data".getBytes();

    long crc1 = Crc32Utils.computeEntryCrc(timestamp, 10, data);
    long crc2 = Crc32Utils.computeEntryCrc(timestamp, 20, data);

    assertNotEquals(crc1, crc2);
  }

  @Test
  @DisplayName("computeEntryCrc produces different results for different data")
  void testComputeEntryCrcDifferentForDifferentData() throws IOException {
    long timestamp = System.currentTimeMillis();
    int size = 10;

    long crc1 = Crc32Utils.computeEntryCrc(timestamp, size, "data1".getBytes());
    long crc2 = Crc32Utils.computeEntryCrc(timestamp, size, "data2".getBytes());

    assertNotEquals(crc1, crc2);
  }

  @Test
  @DisplayName("computeEntryCrc handles empty data")
  void testComputeEntryCrcEmptyData() throws IOException {
    long timestamp = System.currentTimeMillis();
    int size = 0;
    byte[] data = {};

    long crc = Crc32Utils.computeEntryCrc(timestamp, size, data);

    assertTrue(crc >= 0);
  }

  @Test
  @DisplayName("computeEntryCrc handles large data")
  void testComputeEntryCrcLargeData() throws IOException {
    long timestamp = System.currentTimeMillis();
    byte[] largeData = new byte[10000];
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }

    long crc = Crc32Utils.computeEntryCrc(timestamp, largeData.length, largeData);

    assertTrue(crc >= 0);
  }
}
