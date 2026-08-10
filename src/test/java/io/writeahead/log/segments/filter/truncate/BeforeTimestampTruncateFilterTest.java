package io.writeahead.log.segments.filter.truncate;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConstants;
import io.writeahead.log.models.meta.SegmentMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("BeforeTimestampTruncateFilter Tests")
class BeforeTimestampTruncateFilterTest {

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("BeforeTimestampTruncateFilter creates with positive threshold")
    void createsWithPositiveThreshold() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);
      assertNotNull(filter);
    }

    @Test
    @DisplayName("BeforeTimestampTruncateFilter creates with zero threshold")
    void createsWithZeroThreshold() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(0L);
      assertNotNull(filter);
    }

    @Test
    @DisplayName("BeforeTimestampTruncateFilter creates with negative threshold")
    void createsWithNegativeThreshold() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(-1000L);
      assertNotNull(filter);
    }

    @Test
    @DisplayName("BeforeTimestampTruncateFilter creates with Long.MAX_VALUE threshold")
    void createsWithMaxLongThreshold() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(Long.MAX_VALUE);
      assertNotNull(filter);
    }

    @Test
    @DisplayName("BeforeTimestampTruncateFilter creates with Long.MIN_VALUE threshold")
    void createsWithMinLongThreshold() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(Long.MIN_VALUE);
      assertNotNull(filter);
    }
  }

  @Nested
  @DisplayName("ShouldDelete Method Tests")
  class ShouldDeleteMethodTests {

    @Test
    @DisplayName("shouldDelete returns true when maxTimestamp equals threshold")
    void deleteWhenMaxTimestampEqualsThreshold() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment_0001.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              500L,
              1000L);

      boolean shouldDelete = filter.shouldDelete(segment);

      assertTrue(shouldDelete, "Should delete when maxTimestamp == threshold");
    }

    @Test
    @DisplayName("shouldDelete returns true when maxTimestamp less than threshold")
    void deleteWhenMaxTimestampLessThanThreshold() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment_0001.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              500L,
              900L);

      boolean shouldDelete = filter.shouldDelete(segment);

      assertTrue(shouldDelete, "Should delete when maxTimestamp < threshold");
    }

    @Test
    @DisplayName("shouldDelete returns false when maxTimestamp greater than threshold")
    void dontDeleteWhenMaxTimestampGreaterThanThreshold() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment_0001.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              500L,
              1500L);

      boolean shouldDelete = filter.shouldDelete(segment);

      assertFalse(shouldDelete, "Should NOT delete when maxTimestamp > threshold");
    }

    @Test
    @DisplayName("shouldDelete with zero threshold and zero maxTimestamp")
    void zeroThresholdZeroMaxTimestamp() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(0L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              0L,
              0L);

      boolean shouldDelete = filter.shouldDelete(segment);

      assertTrue(shouldDelete);
    }

    @Test
    @DisplayName("shouldDelete with zero threshold and positive maxTimestamp")
    void zeroThresholdPositiveMaxTimestamp() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(0L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              0L,
              1L);

      boolean shouldDelete = filter.shouldDelete(segment);

      assertFalse(shouldDelete);
    }

    @Test
    @DisplayName("shouldDelete with negative threshold and positive maxTimestamp")
    void negativeThresholdPositiveMaxTimestamp() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(-1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              0L,
              1000L);

      boolean shouldDelete = filter.shouldDelete(segment);

      assertFalse(shouldDelete);
    }

    @Test
    @DisplayName(
        "shouldDelete with negative threshold and negative maxTimestamp less than threshold")
    void negativeThresholdNegativeMaxTimestampLessThanThreshold() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(-500L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              -2000L,
              -1000L);

      boolean shouldDelete = filter.shouldDelete(segment);

      assertTrue(shouldDelete);
    }

    @Test
    @DisplayName(
        "shouldDelete with negative threshold and negative maxTimestamp greater than threshold")
    void negativeThresholdNegativeMaxTimestampGreaterThanThreshold() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(-1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              -500L,
              -100L);

      boolean shouldDelete = filter.shouldDelete(segment);

      assertFalse(shouldDelete);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 500L, 1000L, 2000L, 10000L})
    @DisplayName("shouldDelete returns correct result for various maxTimestamps")
    void shouldDeleteCorrectResultForVariousMaxTimestamps(long maxTimestamp) {
      long threshold = 1000L;
      TruncateFilter filter = new BeforeTimestampTruncateFilter(threshold);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              0L,
              maxTimestamp);

      boolean shouldDelete = filter.shouldDelete(segment);

      assertEquals(maxTimestamp <= threshold, shouldDelete);
    }
  }

  @Nested
  @DisplayName("Name Method Tests")
  class NameMethodTests {

    @Test
    @DisplayName("name returns BEFORE_TIMESTAMP string")
    void nameReturnsBeforeTimestampString() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);

      String name = filter.name();

      assertEquals("BEFORE_TIMESTAMP", name);
    }

    @Test
    @DisplayName("name always returns same string value")
    void nameAlwaysReturnsSameValue() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);

      String name1 = filter.name();
      String name2 = filter.name();

      assertEquals(name1, name2);
    }

    @Test
    @DisplayName("name returns non-null")
    void nameReturnsNonNull() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);

      String name = filter.name();

      assertNotNull(name);
    }

    @Test
    @DisplayName("name returns non-empty string")
    void nameReturnsNonEmptyString() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);

      String name = filter.name();

      assertFalse(name.isEmpty());
    }

    @Test
    @DisplayName("name is case-sensitive uppercase")
    void nameIsCaseSensitiveUppercase() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);

      String name = filter.name();

      assertEquals("BEFORE_TIMESTAMP", name);
      assertNotEquals("before_timestamp", name);
      assertNotEquals("BeforeTimestamp", name);
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("shouldDelete with maxTimestamp at Long.MAX_VALUE and high threshold")
    void shouldDeleteWithMaxLongTimestamp() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(Long.MAX_VALUE);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              0L,
              Long.MAX_VALUE - 1);

      boolean shouldDelete = filter.shouldDelete(segment);

      assertTrue(shouldDelete);
    }

    @Test
    @DisplayName("shouldDelete with maxTimestamp at Long.MAX_VALUE and lower threshold")
    void shouldDeleteWithMaxLongTimestampLowerThreshold() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(Long.MAX_VALUE - 1);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              0L,
              Long.MAX_VALUE);

      boolean shouldDelete = filter.shouldDelete(segment);

      assertFalse(shouldDelete);
    }

    @Test
    @DisplayName("shouldDelete with minTimestamp and maxTimestamp at boundary")
    void shouldDeleteWithTimestampsBoundary() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(Long.MAX_VALUE);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              Long.MIN_VALUE,
              Long.MAX_VALUE - 1);

      boolean shouldDelete = filter.shouldDelete(segment);

      assertTrue(shouldDelete);
    }

    @Test
    @DisplayName("shouldDelete with Long.MIN_VALUE threshold never deletes positive timestamps")
    void minLongThresholdNeverDeletesPositiveTimestamps() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(Long.MIN_VALUE);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              0L,
              1L);

      boolean shouldDelete = filter.shouldDelete(segment);

      assertFalse(shouldDelete);
    }

    @Test
    @DisplayName("shouldDelete with minimal segment size")
    void shouldDeleteWithMinimalSegmentSize() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE,
              1L,
              500L,
              900L);

      boolean shouldDelete = filter.shouldDelete(segment);

      assertTrue(shouldDelete);
    }
  }

  @Nested
  @DisplayName("Interface Implementation Tests")
  class InterfaceImplementationTests {

    @Test
    @DisplayName("BeforeTimestampTruncateFilter implements TruncateFilter")
    void implementsTruncateFilter() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);
      assertInstanceOf(TruncateFilter.class, filter);
    }

    @Test
    @DisplayName("BeforeTimestampTruncateFilter is final class")
    void isFinalClass() {
      assertTrue(
          java.lang.reflect.Modifier.isFinal(BeforeTimestampTruncateFilter.class.getModifiers()));
    }

    @Test
    @DisplayName("BeforeTimestampTruncateFilter provides all required methods")
    void providesAllRequiredMethods() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              100L,
              200L);

      assertNotNull(filter.shouldDelete(segment));
      assertNotNull(filter.name());
    }
  }

  @Nested
  @DisplayName("Consistency Tests")
  class ConsistencyTests {

    @Test
    @DisplayName("same filter gives same result for same segment")
    void sameFilterGivesSameResultForSameSegment() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              500L,
              900L);

      boolean result1 = filter.shouldDelete(segment);
      boolean result2 = filter.shouldDelete(segment);

      assertEquals(result1, result2);
    }

    @Test
    @DisplayName("different filters give different results for same segment")
    void differentFiltersGiveDifferentResultsForSameSegment() {
      TruncateFilter filter1 = new BeforeTimestampTruncateFilter(1000L);
      TruncateFilter filter2 = new BeforeTimestampTruncateFilter(500L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              200L,
              800L);

      boolean result1 = filter1.shouldDelete(segment);
      boolean result2 = filter2.shouldDelete(segment);

      assertNotEquals(result1, result2);
      assertTrue(result1);
      assertFalse(result2);
    }
  }
}
