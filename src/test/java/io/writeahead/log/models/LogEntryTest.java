package io.writeahead.log.models;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LogEntry Tests — Simple Record Properties")
class LogEntryTest {

  @Nested
  @DisplayName("Record Properties")
  class RecordProperties {

    @Test
    void recordCreatesWithAllFields() {
      byte[] data = new byte[100];
      LogEntry entry = new LogEntry(100, data, 1000L);

      assertEquals(100, entry.size());
      assertArrayEquals(data, entry.data());
      assertEquals(1000L, entry.timestamp());
    }

    @Test
    void recordHandlesZeroSize() {
      LogEntry entry = new LogEntry(0, new byte[0], 0L);
      assertEquals(0, entry.size());
      assertEquals(0, entry.data().length);
      assertEquals(0L, entry.timestamp());
    }

    @Test
    void recordHandlesLargeSize() {
      byte[] largeData = new byte[1_000_000];
      LogEntry entry = new LogEntry(1_000_000, largeData, Long.MAX_VALUE);
      assertEquals(1_000_000, entry.size());
      assertEquals(Long.MAX_VALUE, entry.timestamp());
    }

    @Test
    void recordHandlesNegativeTimestamp() {
      LogEntry entry = new LogEntry(100, new byte[100], -1L);
      assertEquals(-1L, entry.timestamp());
    }

    @Test
    void recordPreservesDataContent() {
      byte[] data = new byte[10];
      for (int i = 0; i < 10; i++) {
        data[i] = (byte) i;
      }
      LogEntry entry = new LogEntry(10, data, 1000L);

      byte[] retrieved = entry.data();
      for (int i = 0; i < 10; i++) {
        assertEquals((byte) i, retrieved[i]);
      }
    }

    @Test
    void recordEquality() {
      byte[] data = new byte[100];
      LogEntry entry1 = new LogEntry(100, data, 1000L);
      LogEntry entry2 = new LogEntry(100, data, 1000L);

      assertEquals(entry1, entry2);
    }

    @Test
    void recordHashCode() {
      byte[] data = new byte[100];
      LogEntry entry1 = new LogEntry(100, data, 1000L);
      LogEntry entry2 = new LogEntry(100, data, 1000L);

      assertEquals(entry1.hashCode(), entry2.hashCode());
    }

    @Test
    void recordToString() {
      LogEntry entry = new LogEntry(100, new byte[100], 1000L);
      String str = entry.toString();

      assertNotNull(str);
      assertFalse(str.isEmpty());
    }
  }
}
