package io.writeahead.log.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.results.TruncateResult;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class TruncationIntegrationTest extends IntegrationTestBase {

  @Test
  @Timeout(60)
  void testConcurrentTruncateAndWrite() throws Exception {

    wal = new WriteAheadLog(createConfig());

    long baseTimestamp = System.currentTimeMillis();
    for (int i = 0; i < 200; i++) {
      wal.append(new LogEntry(100, new byte[100], baseTimestamp + (i * 1000)));
    }
    wal.writeBatch();

    AtomicReference<Exception> writerError = new AtomicReference<>();
    AtomicReference<Exception> truncatorError = new AtomicReference<>();
    final int[] truncateCount = {0};

    Thread writer =
        new Thread(
            () -> {
              try {
                for (int i = 200; i < 300; i++) {
                  wal.append(new LogEntry(100, new byte[100], baseTimestamp + (i * 1000)));
                  if (i % 20 == 0) wal.writeBatch();
                }
              } catch (Exception e) {
                writerError.set(e);
              }
            });

    Thread truncator =
        new Thread(
            () -> {
              try {
                for (int t = 0; t < 5; t++) {
                  Thread.sleep(20);
                  long truncateTs = baseTimestamp + (t * 30_000);
                  TruncateResult result = wal.truncateBeforeTimestamp(truncateTs);
                  if (result.success()) {
                    truncateCount[0]++;
                  }
                }
              } catch (Exception e) {
                truncatorError.set(e);
              }
            });

    writer.start();
    truncator.start();
    writer.join();
    truncator.join();
    wal.writeBatch();

    if (writerError.get() != null) throw writerError.get();
    if (truncatorError.get() != null) throw truncatorError.get();
    System.out.println("Test 1: No concurrent truncate/write errors ✓");

    assertTrue(
        truncateCount[0] > 0, "Should have successful truncations, got: " + truncateCount[0]);
    System.out.println("Test 1: " + truncateCount[0] + " truncations succeeded ✓");

    List<LogEntry> remaining = wal.readAllSegments();
    assertFalse(
        remaining.isEmpty(),
        "Should have remaining entries after truncation, got: " + remaining.size());
    System.out.println("Test 1: " + remaining.size() + " entries remain after truncation ✓");

    wal.close();
  }

  @Test
  @Timeout(30)
  void testTruncateBeforeOldestTimestamp() throws IOException {
    wal = new WriteAheadLog(createConfig());

    long minTs = 10_000L;
    for (int i = 0; i < 100; i++) {
      wal.append(new LogEntry(100, new byte[100], minTs + (i * 1000)));
    }
    wal.writeBatch();

    int entriesBeforeTruncate = (int) wal.readAllSegments().size();
    System.out.println("Test 2: Before truncate = " + entriesBeforeTruncate + " entries");

    long truncateTs = minTs - 1000;
    TruncateResult result = wal.truncateBeforeTimestamp(truncateTs);

    assertTrue(result.success(), "Truncate should succeed");
    System.out.println("Test 2: Truncate before oldest succeeded ✓");

    int entriesAfterTruncate = (int) wal.readAllSegments().size();
    assertEquals(
        entriesBeforeTruncate,
        entriesAfterTruncate,
        "Should remove no entries when truncating before oldest");
    System.out.println("Test 2: No entries removed (expected) ✓");

    wal.close();
  }

  @Test
  @Timeout(30)
  void testTruncateAfterNewestTimestamp() throws IOException {
    wal = new WriteAheadLog(createConfig());

    long maxTs = 100_000L;
    for (int i = 0; i < 100; i++) {
      wal.append(new LogEntry(100, new byte[100], maxTs - (100 - i) * 1000));
    }
    wal.writeBatch();

    int entriesBeforeTruncate = (int) wal.readAllSegments().size();
    System.out.println("Test 3: Before truncate = " + entriesBeforeTruncate + " entries");

    long truncateTs = maxTs + 1000;
    TruncateResult result = wal.truncateBeforeTimestamp(truncateTs);

    assertTrue(result.success(), "Truncate should succeed");
    System.out.println("Test 3: Truncate after newest succeeded ✓");

    int entriesAfterTruncate = (int) wal.readAllSegments().size();
    assertTrue(entriesAfterTruncate >= 1, "Should keep at least 1 segment for safety");
    System.out.println(
        "Test 3: After truncate = " + entriesAfterTruncate + " entries (safety preserved) ✓");

    wal.close();
  }

  @Test
  @Timeout(30)
  void testTruncationAccuracy() throws IOException {
    wal = new WriteAheadLog(createConfig());

    long baseTs = 100_000L;
    int entryCount = 300;
    for (int i = 0; i < entryCount; i++) {
      wal.append(new LogEntry(100, new byte[100], baseTs + (i * 100)));
    }
    wal.writeBatch();

    List<LogEntry> beforeTruncate = wal.readAllSegments();
    System.out.println("Test 4: Before truncate = " + beforeTruncate.size() + " entries");

    long truncateTs = baseTs + 15_000L;
    TruncateResult result = wal.truncateBeforeTimestamp(truncateTs);

    assertTrue(result.success(), "Truncate should succeed");
    System.out.println("Test 4: Truncate operation succeeded ✓");

    List<LogEntry> afterTruncate = wal.readAllSegments();
    assertFalse(afterTruncate.isEmpty(), "Should have at least 1 entry after truncate (safety)");
    System.out.println("Test 4: " + afterTruncate.size() + " entries remain ✓");

    wal.close();
  }
}
