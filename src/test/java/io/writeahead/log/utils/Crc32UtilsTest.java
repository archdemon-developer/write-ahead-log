package io.writeahead.log.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class Crc32UtilsTest {

  @Test
  void testComputeBasic() {
    byte[] data = "hello world".getBytes();
    long crc = Crc32Utils.compute(data);

    assertNotEquals(0, crc, "CRC should be non-zero for valid data");
  }

  @Test
  void testComputeDeterministic() {
    byte[] data = "test data".getBytes();
    long crc1 = Crc32Utils.compute(data);
    long crc2 = Crc32Utils.compute(data);

    assertEquals(crc1, crc2, "Same input should produce same CRC");
  }

  @Test
  void testDifferentDataDifferentCrc() {
    byte[] data1 = "data1".getBytes();
    byte[] data2 = "data2".getBytes();

    long crc1 = Crc32Utils.compute(data1);
    long crc2 = Crc32Utils.compute(data2);

    assertNotEquals(crc1, crc2, "Different data should produce different CRCs");
  }

  @Test
  void testSmallChangeDetected() {
    byte[] data1 = "entry-1".getBytes();
    byte[] data2 = "entry-2".getBytes();

    long crc1 = Crc32Utils.compute(data1);
    long crc2 = Crc32Utils.compute(data2);

    assertNotEquals(crc1, crc2, "Single byte change should produce different CRC");
  }

  @Test
  void testComputeWithComponents() throws Exception {
    long timestamp = 1000L;
    int size = 5;
    byte[] data = "hello".getBytes();

    long crc = Crc32Utils.compute(timestamp, size, data);

    assertNotEquals(0, crc, "CRC of components should be non-zero");
  }

  @Test
  void testComputeComponentsConsistent() throws Exception {
    long timestamp = 1000L;
    int size = 5;
    byte[] data = "hello".getBytes();

    long crc1 = Crc32Utils.compute(timestamp, size, data);
    long crc2 = Crc32Utils.compute(timestamp, size, data);

    assertEquals(crc1, crc2, "Same components should produce same CRC");
  }

  @Test
  void testNullDataThrows() {
    assertThrows(
        NullPointerException.class,
        () -> Crc32Utils.compute(null),
        "Should throw NullPointerException for null data");
  }

  @Test
  void testEmptyArray() {
    byte[] empty = new byte[0];
    long crc = Crc32Utils.compute(empty);

    assertEquals(0L, crc, "CRC of empty array should be 0");
  }

  @Test
  void testLargeData() {
    byte[] large = new byte[1024 * 1024];
    for (int i = 0; i < large.length; i++) {
      large[i] = (byte) (i % 256);
    }

    long crc = Crc32Utils.compute(large);

    assertNotEquals(0, crc, "CRC of large array should be computed");
  }

  @Test
  void testOneByteChange() {
    byte[] data1 = "the quick brown fox".getBytes();
    byte[] data2 = "the quick brown fox".getBytes();
    data2[4] = 'X';

    long crc1 = Crc32Utils.compute(data1);
    long crc2 = Crc32Utils.compute(data2);

    assertNotEquals(crc1, crc2, "Single byte modification should change CRC");
  }
}
