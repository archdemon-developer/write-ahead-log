package io.writeahead.log.models.results;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.meta.SegmentMetadata;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TruncateSegmentsResult Tests — 100% Validation Coverage")
class TruncateSegmentsResultTest {

  @Nested
  @DisplayName("Compact Constructor Validation")
  class ConstructorValidation {

    @Test
    void rejectsSegmentsRemovedNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new TruncateSegmentsResult(-1L, 0L, List.of()));
      assertTrue(ex.getMessage().contains("segmentsRemoved cannot be negative"));
    }

    @Test
    void rejectsOldestRemainingSequenceNegative() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new TruncateSegmentsResult(0L, -1L, List.of()));
      assertTrue(ex.getMessage().contains("oldestRemainingSequence cannot be negative"));
    }

    @Test
    void rejectsRemovedSegmentsNull() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new TruncateSegmentsResult(0L, 0L, null));
      assertTrue(ex.getMessage().contains("removedSegments cannot be null"));
    }

    @Test
    void rejectsMismatchedListSize() {
      SegmentMetadata meta = new SegmentMetadata("wal-001.log", 1L, 1000L, 500L, 10, 100L, 200L);
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new TruncateSegmentsResult(5L, 0L, List.of(meta)));
      assertTrue(
          ex.getMessage().contains("removedSegments.size()")
              && ex.getMessage().contains("segmentsRemoved"));
    }

    @Test
    void acceptsValidResult() {
      TruncateSegmentsResult result = new TruncateSegmentsResult(0L, 0L, List.of());
      assertFalse(result.wereSegmentsRemoved());
    }
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    void nothingRemovedCreatesCorrectResult() {
      TruncateSegmentsResult result = TruncateSegmentsResult.nothingRemoved(0L);
      assertFalse(result.wereSegmentsRemoved());
      assertEquals(0L, result.segmentsRemoved());
      assertTrue(result.removedSegments().isEmpty());
    }

    @Test
    void segmentsRemovedCreatesCorrectResult() {
      SegmentMetadata meta1 = new SegmentMetadata("wal-001.log", 1L, 1000L, 500L, 10, 100L, 200L);
      SegmentMetadata meta2 = new SegmentMetadata("wal-002.log", 2L, 2000L, 600L, 20, 300L, 400L);
      List<SegmentMetadata> removed = List.of(meta1, meta2);

      TruncateSegmentsResult result = TruncateSegmentsResult.segmentsRemoved(2L, 3L, removed);
      assertTrue(result.wereSegmentsRemoved());
      assertEquals(2L, result.segmentsRemoved());
      assertEquals(2, result.removedSegments().size());
    }
  }

  @Nested
  @DisplayName("Helper Methods")
  class HelperMethods {

    @Test
    void wereSegmentsRemovedReturnsTrueWhenRemoved() {
      SegmentMetadata meta = new SegmentMetadata("wal-001.log", 1L, 1000L, 500L, 10, 100L, 200L);
      TruncateSegmentsResult result = TruncateSegmentsResult.segmentsRemoved(1L, 1L, List.of(meta));
      assertTrue(result.wereSegmentsRemoved());
    }

    @Test
    void wereSegmentsRemovedReturnsFalseWhenNotRemoved() {
      TruncateSegmentsResult result = TruncateSegmentsResult.nothingRemoved(0L);
      assertFalse(result.wereSegmentsRemoved());
    }

    @Test
    void getSegmentsToDeleteReturnsDefensiveCopy() {
      SegmentMetadata meta = new SegmentMetadata("wal-001.log", 1L, 1000L, 500L, 10, 100L, 200L);
      List<SegmentMetadata> original = new ArrayList<>(List.of(meta));
      TruncateSegmentsResult result = TruncateSegmentsResult.segmentsRemoved(1L, 1L, original);

      List<SegmentMetadata> returned = result.getSegmentsToDelete();
      assertEquals(1, returned.size());

      returned.clear();
      assertEquals(1, result.removedSegments().size());
    }
  }
}
