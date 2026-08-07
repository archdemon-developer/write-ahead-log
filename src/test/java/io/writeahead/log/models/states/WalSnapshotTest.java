package io.writeahead.log.models.states;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.metrics.WalMetricsQuery;
import io.writeahead.log.models.meta.SegmentMetadata;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WalSnapshot Tests — 100% Validation Coverage")
class WalSnapshotTest {

  private static class SimpleMetricsQuery implements WalMetricsQuery {
    @Override
    public long getEntriesWritten() {
      return 0;
    }

    @Override
    public long getBytesWritten() {
      return 0;
    }

    @Override
    public long getSegmentCount() {
      return 0;
    }

    @Override
    public long getCorruptedEntriesDetected() {
      return 0;
    }

    @Override
    public long getLastRotationTimeMs() {
      return 0;
    }

    @Override
    public double getThroughputEntriesPerSec() {
      return 0;
    }

    @Override
    public double getThroughputMbPerSec() {
      return 0;
    }

    @Override
    public long getTotalFsyncs() {
      return 0;
    }

    @Override
    public double getAverageFsyncLatencyMs() {
      return 0;
    }

    @Override
    public long getLastFsyncTimeMs() {
      return 0;
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — closedSegments == null")
  class ConstructorValidation_ClosedSegmentsNull {

    @Test
    @DisplayName("constructor rejects closedSegments = null")
    void rejectsClosedSegmentsNull() {
      SegmentState current = SegmentState.emptyOpenSegment(1L, 1000L);
      BatchState batch = BatchState.emptyBatch();
      WalMetricsQuery metrics = new SimpleMetricsQuery();

      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new WalSnapshot(null, current, batch, metrics, true, 1000L));
      assertTrue(ex.getMessage().contains("closedSegments cannot be null"));
    }

    @Test
    @DisplayName("constructor accepts closedSegments = empty list")
    void acceptsEmptyClosedSegmentsList() {
      SegmentState current = SegmentState.emptyOpenSegment(1L, 1000L);
      BatchState batch = BatchState.emptyBatch();
      WalMetricsQuery metrics = new SimpleMetricsQuery();

      WalSnapshot snapshot = new WalSnapshot(List.of(), current, batch, metrics, true, 1000L);
      assertTrue(snapshot.closedSegments().isEmpty());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — currentSegment == null")
  class ConstructorValidation_CurrentSegmentNull {

    @Test
    @DisplayName("constructor rejects currentSegment = null")
    void rejectsCurrentSegmentNull() {
      BatchState batch = BatchState.emptyBatch();
      WalMetricsQuery metrics = new SimpleMetricsQuery();

      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new WalSnapshot(List.of(), null, batch, metrics, true, 1000L));
      assertTrue(ex.getMessage().contains("currentSegment cannot be null"));
    }

    @Test
    @DisplayName("constructor accepts valid currentSegment")
    void acceptsValidCurrentSegment() {
      SegmentState current = SegmentState.emptyOpenSegment(1L, 1000L);
      BatchState batch = BatchState.emptyBatch();
      WalMetricsQuery metrics = new SimpleMetricsQuery();

      WalSnapshot snapshot = new WalSnapshot(List.of(), current, batch, metrics, true, 1000L);
      assertEquals(1L, snapshot.currentSegment().segmentSequenceNumber());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — batchState == null")
  class ConstructorValidation_BatchStateNull {

    @Test
    @DisplayName("constructor rejects batchState = null")
    void rejectsBatchStateNull() {
      SegmentState current = SegmentState.emptyOpenSegment(1L, 1000L);
      WalMetricsQuery metrics = new SimpleMetricsQuery();

      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new WalSnapshot(List.of(), current, null, metrics, true, 1000L));
      assertTrue(ex.getMessage().contains("batchState cannot be null"));
    }

    @Test
    @DisplayName("constructor accepts valid batchState")
    void acceptsValidBatchState() {
      SegmentState current = SegmentState.emptyOpenSegment(1L, 1000L);
      BatchState batch = BatchState.emptyBatch();
      WalMetricsQuery metrics = new SimpleMetricsQuery();

      WalSnapshot snapshot = new WalSnapshot(List.of(), current, batch, metrics, true, 1000L);
      assertTrue(snapshot.batchState().isEmpty());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — metrics == null")
  class ConstructorValidation_MetricsNull {

    @Test
    @DisplayName("constructor rejects metrics = null")
    void rejectsMetricsNull() {
      SegmentState current = SegmentState.emptyOpenSegment(1L, 1000L);
      BatchState batch = BatchState.emptyBatch();

      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new WalSnapshot(List.of(), current, batch, null, true, 1000L));
      assertTrue(ex.getMessage().contains("metrics cannot be null"));
    }

    @Test
    @DisplayName("constructor accepts valid metrics")
    void acceptsValidMetrics() {
      SegmentState current = SegmentState.emptyOpenSegment(1L, 1000L);
      BatchState batch = BatchState.emptyBatch();
      WalMetricsQuery metrics = new SimpleMetricsQuery();

      WalSnapshot snapshot = new WalSnapshot(List.of(), current, batch, metrics, true, 1000L);
      assertNotNull(snapshot.metrics());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — snapshotTimeMs < 0")
  class ConstructorValidation_SnapshotTimeNegative {

    @Test
    @DisplayName("constructor rejects snapshotTimeMs = -1")
    void rejectsSnapshotTimeNegative() {
      SegmentState current = SegmentState.emptyOpenSegment(1L, 1000L);
      BatchState batch = BatchState.emptyBatch();
      WalMetricsQuery metrics = new SimpleMetricsQuery();

      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new WalSnapshot(List.of(), current, batch, metrics, true, -1L));
      assertTrue(ex.getMessage().contains("snapshotTimeMs cannot be negative"));
    }

    @Test
    @DisplayName("constructor rejects snapshotTimeMs = Long.MIN_VALUE")
    void rejectsSnapshotTimeMinValue() {
      SegmentState current = SegmentState.emptyOpenSegment(1L, 1000L);
      BatchState batch = BatchState.emptyBatch();
      WalMetricsQuery metrics = new SimpleMetricsQuery();

      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new WalSnapshot(List.of(), current, batch, metrics, true, Long.MIN_VALUE));
      assertTrue(ex.getMessage().contains("snapshotTimeMs cannot be negative"));
    }

    @Test
    @DisplayName("constructor accepts snapshotTimeMs = 0")
    void acceptsSnapshotTimeZero() {
      SegmentState current = SegmentState.emptyOpenSegment(1L, 1000L);
      BatchState batch = BatchState.emptyBatch();
      WalMetricsQuery metrics = new SimpleMetricsQuery();

      WalSnapshot snapshot = new WalSnapshot(List.of(), current, batch, metrics, true, 0L);
      assertEquals(0L, snapshot.snapshotTimeMs());
    }

    @Test
    @DisplayName("constructor accepts snapshotTimeMs = Long.MAX_VALUE")
    void acceptsSnapshotTimeMaxValue() {
      SegmentState current = SegmentState.emptyOpenSegment(1L, 1000L);
      BatchState batch = BatchState.emptyBatch();
      WalMetricsQuery metrics = new SimpleMetricsQuery();

      WalSnapshot snapshot =
          new WalSnapshot(List.of(), current, batch, metrics, true, Long.MAX_VALUE);
      assertEquals(Long.MAX_VALUE, snapshot.snapshotTimeMs());
    }
  }

  @Nested
  @DisplayName("Factory Method Tests — of()")
  class FactoryMethod_Of {

    @Test
    @DisplayName("of() maps closed segments to SegmentState with isFinalized=true")
    void mapsClosedSegmentsToFinalized() throws IOException {
      SegmentMetadata closedMeta =
          new SegmentMetadata("wal-001.log", 1L, 1000L, 500L, 10, 100L, 200L);

      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(closedMeta),
              2L,
              0L,
              512,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              2000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);

      assertEquals(1, snapshot.closedSegments().size());
      SegmentState closedState = snapshot.closedSegments().getFirst();
      assertTrue(closedState.isFinalized());
      assertEquals(1L, closedState.segmentSequenceNumber());
    }

    @Test
    @DisplayName("of() creates currentSegment with isFinalized=false")
    void createsCurrentSegmentNotFinalized() throws IOException {
      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(),
              1L,
              5L,
              512,
              100L,
              200L,
              1000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);

      assertFalse(snapshot.currentSegment().isFinalized());
      assertEquals(1L, snapshot.currentSegment().segmentSequenceNumber());
    }

    @Test
    @DisplayName("of() sets snapshotTimeMs to current time")
    void setsSnapshotTimeToNow() throws IOException {
      long beforeSnapshot = System.currentTimeMillis();

      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(),
              1L,
              0L,
              512,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              1000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);

      long afterSnapshot = System.currentTimeMillis();

      assertTrue(snapshot.snapshotTimeMs() >= beforeSnapshot);
      assertTrue(snapshot.snapshotTimeMs() <= afterSnapshot + 1000);
    }

    @Test
    @DisplayName("of() handles multiple closed segments")
    void handlesMultipleClosedSegments() throws IOException {
      SegmentMetadata meta1 = new SegmentMetadata("wal-001.log", 1L, 1000L, 500L, 10, 100L, 200L);
      SegmentMetadata meta2 = new SegmentMetadata("wal-002.log", 2L, 2000L, 600L, 20, 300L, 400L);
      SegmentMetadata meta3 = new SegmentMetadata("wal-003.log", 3L, 3000L, 700L, 30, 500L, 600L);

      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(meta1, meta2, meta3),
              4L,
              0L,
              512,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              4000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);

      assertEquals(3, snapshot.closedSegments().size());
    }
  }

  @Nested
  @DisplayName("Helper Tests — getTotalEntries()")
  class GetTotalEntries {

    @Test
    @DisplayName("getTotalEntries() returns sum of all segment entries")
    void returnsSumOfEntries() throws IOException {
      SegmentMetadata meta1 = new SegmentMetadata("wal-001.log", 1L, 1000L, 500L, 10, 100L, 200L);
      SegmentMetadata meta2 = new SegmentMetadata("wal-002.log", 2L, 2000L, 600L, 20, 300L, 400L);

      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(meta1, meta2),
              3L,
              5L,
              512,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              3000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);
      assertEquals(35L, snapshot.getTotalEntries());
    }

    @Test
    @DisplayName("getTotalEntries() returns current segment entries when no closed segments")
    void returnsCurrentEntries() throws IOException {
      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(),
              1L,
              42L,
              512,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              1000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);
      assertEquals(42L, snapshot.getTotalEntries());
    }

    @Test
    @DisplayName("getTotalEntries() returns 0 for empty WAL")
    void returns0ForEmpty() throws IOException {
      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(),
              1L,
              0L,
              512,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              1000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);
      assertEquals(0L, snapshot.getTotalEntries());
    }
  }

  @Nested
  @DisplayName("Helper Tests — getTotalBytes()")
  class GetTotalBytes {

    @Test
    @DisplayName("getTotalBytes() returns sum of all segment bytes")
    void returnsSumOfBytes() throws IOException {
      SegmentMetadata meta1 = new SegmentMetadata("wal-001.log", 1L, 1000L, 500L, 10, 100L, 200L);
      SegmentMetadata meta2 = new SegmentMetadata("wal-002.log", 2L, 2000L, 600L, 20, 300L, 400L);

      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(meta1, meta2),
              3L,
              0L,
              1024,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              3000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);
      assertEquals(2124L, snapshot.getTotalBytes());
    }
  }

  @Nested
  @DisplayName("Helper Tests — getTotalSegmentCount()")
  class GetTotalSegmentCount {

    @Test
    @DisplayName("getTotalSegmentCount() returns closed segments + current segment")
    void returnsClosedPlusCurrent() throws IOException {
      SegmentMetadata meta1 = new SegmentMetadata("wal-001.log", 1L, 1000L, 500L, 10, 100L, 200L);
      SegmentMetadata meta2 = new SegmentMetadata("wal-002.log", 2L, 2000L, 600L, 20, 300L, 400L);

      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(meta1, meta2),
              3L,
              0L,
              512,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              3000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);
      assertEquals(3, snapshot.getTotalSegmentCount());
    }

    @Test
    @DisplayName("getTotalSegmentCount() returns 1 for single current segment")
    void returns1ForSingleCurrent() throws IOException {
      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(),
              1L,
              0L,
              512,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              1000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);
      assertEquals(1, snapshot.getTotalSegmentCount());
    }
  }

  @Nested
  @DisplayName("Helper Tests — isCurrentSegmentEmpty()")
  class IsCurrentSegmentEmpty {

    @Test
    @DisplayName("isCurrentSegmentEmpty() returns true when current has 0 entries")
    void returnsTrueWhenZeroEntries() throws IOException {
      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(),
              1L,
              0L,
              512,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              1000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);
      assertTrue(snapshot.isCurrentSegmentEmpty());
    }

    @Test
    @DisplayName("isCurrentSegmentEmpty() returns false when current has entries")
    void returnsFalseWhenHasEntries() throws IOException {
      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(),
              1L,
              5L,
              512,
              100L,
              200L,
              1000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);
      assertFalse(snapshot.isCurrentSegmentEmpty());
    }
  }

  @Nested
  @DisplayName("Helper Tests — hasPendingEntries()")
  class HasPendingEntries {

    @Test
    @DisplayName("hasPendingEntries() returns false for empty batch")
    void returnsFalseForEmptyBatch() throws IOException {
      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(),
              1L,
              0L,
              512,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              1000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);
      assertFalse(snapshot.hasPendingEntries());
    }

    @Test
    @DisplayName("hasPendingEntries() returns true for non-empty batch")
    void returnsTrueForNonEmptyBatch() throws IOException {
      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(),
              1L,
              0L,
              512,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              1000L,
              BatchState.withPendingEntries(5, 100L, 100L, 200L),
              new SimpleMetricsQuery(),
              true);
      assertTrue(snapshot.hasPendingEntries());
    }
  }

  @Nested
  @DisplayName("Helper Tests — getOldestSegmentCreationTime()")
  class GetOldestSegmentCreationTime {

    @Test
    @DisplayName("getOldestSegmentCreationTime() returns oldest closed segment time")
    void returnsOldestClosedSegmentTime() throws IOException {
      SegmentMetadata meta1 = new SegmentMetadata("wal-001.log", 1L, 1000L, 500L, 10, 100L, 200L);
      SegmentMetadata meta2 = new SegmentMetadata("wal-002.log", 2L, 2000L, 600L, 20, 300L, 400L);
      SegmentMetadata meta3 = new SegmentMetadata("wal-003.log", 3L, 3000L, 700L, 30, 500L, 600L);

      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(meta1, meta2, meta3),
              4L,
              0L,
              512,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              4000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);
      assertEquals(1000L, snapshot.getOldestSegmentCreationTime());
    }

    @Test
    @DisplayName("getOldestSegmentCreationTime() returns current segment time when no closed")
    void returnsCurrentSegmentTimeWhenNoClosedSegments() throws IOException {
      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(),
              1L,
              0L,
              512,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              5555L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);
      assertEquals(5555L, snapshot.getOldestSegmentCreationTime());
    }
  }

  @Nested
  @DisplayName("Helper Tests — getCurrentSegmentAgeMillis()")
  class GetCurrentSegmentAgeMillis {

    @Test
    @DisplayName("getCurrentSegmentAgeMillis() calculates age correctly")
    void calculatesAgeCorrectly() {
      long createdAt = 1000L;
      long snapshotTime = 5000L;

      WalSnapshot snapshot =
          new WalSnapshot(
              List.of(),
              SegmentState.emptyOpenSegment(1L, createdAt),
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true,
              snapshotTime);

      assertEquals(4000L, snapshot.getCurrentSegmentAgeMillis());
    }

    @Test
    @DisplayName("getCurrentSegmentAgeMillis() returns 0 when just created")
    void returns0WhenJustCreated() throws IOException {
      long createdAt = 5000L;
      long snapshotTime = 5000L;

      WalSnapshot snapshot =
          new WalSnapshot(
              List.of(),
              SegmentState.emptyOpenSegment(1L, createdAt),
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true,
              snapshotTime);

      assertEquals(0L, snapshot.getCurrentSegmentAgeMillis());
    }
  }

  @Nested
  @DisplayName("Edge Cases & Boundary Values")
  class EdgeCases {

    @Test
    @DisplayName("handles snapshot with many closed segments")
    void handlesManyClosedSegments() throws IOException {
      List<SegmentMetadata> segments = new ArrayList<>();
      for (int i = 1; i <= 100; i++) {
        segments.add(
            new SegmentMetadata(
                "wal-" + String.format("%03d", i) + ".log",
                i,
                1000L + i * 1000L,
                1000L,
                10 + i,
                100L + i * 10L,
                200L + i * 10L));
      }

      WalSnapshot snapshot =
          WalSnapshot.of(
              segments,
              101L,
              0L,
              512,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              101000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);
      assertEquals(100, snapshot.closedSegments().size());
      assertEquals(101, snapshot.getTotalSegmentCount());
    }

    @Test
    @DisplayName("handles snapshot with large entry counts and byte counts")
    void handlesLargeNumbers() throws IOException {
      SegmentMetadata meta =
          new SegmentMetadata(
              "wal-huge.log", 1L, 1000000000L, 1000000000000L, 1000000L, 100L, 200L);

      WalSnapshot snapshot =
          WalSnapshot.of(
              List.of(meta),
              2L,
              1000000L,
              1024,
              Long.MIN_VALUE,
              Long.MAX_VALUE,
              2000L,
              BatchState.emptyBatch(),
              new SimpleMetricsQuery(),
              true);
      assertEquals(2000000L, snapshot.getTotalEntries());
    }
  }
}
