package io.writeahead.log.segments.filter.reads;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConstants;
import io.writeahead.log.enums.strategies.ReadFilterType;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.models.results.FilterResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AfterTimestampFilter Tests")
class AfterTimestampFilterTest {

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("AfterTimestampFilter creates with positive threshold")
    void createsWithPositiveThreshold() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      assertNotNull(filter);
    }

    @Test
    @DisplayName("AfterTimestampFilter creates with zero threshold")
    void createsWithZeroThreshold() {
      ReadFilter filter = new AfterTimestampFilter(0L);
      assertNotNull(filter);
    }

    @Test
    @DisplayName("AfterTimestampFilter creates with negative threshold")
    void createsWithNegativeThreshold() {
      ReadFilter filter = new AfterTimestampFilter(-1000L);
      assertNotNull(filter);
    }

    @Test
    @DisplayName("AfterTimestampFilter creates with Long.MAX_VALUE threshold")
    void createsWithMaxLongThreshold() {
      ReadFilter filter = new AfterTimestampFilter(Long.MAX_VALUE);
      assertNotNull(filter);
    }

    @Test
    @DisplayName("AfterTimestampFilter creates with Long.MIN_VALUE threshold")
    void createsWithMinLongThreshold() {
      ReadFilter filter = new AfterTimestampFilter(Long.MIN_VALUE);
      assertNotNull(filter);
    }
  }

  @Nested
  @DisplayName("Matches Method Tests")
  class MatchesMethodTests {

    @Test
    @DisplayName("matches accepts entry with timestamp equal to threshold")
    void acceptsEntryWithTimestampEqualToThreshold() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      LogEntry entry = new LogEntry(100, new byte[100], 1000L);

      FilterResult result = filter.matches(entry);

      assertTrue(result.isAccepted(), "Entry with timestamp == threshold should be accepted");
      assertTrue(result.matches());
    }

    @Test
    @DisplayName("matches accepts entry with timestamp greater than threshold")
    void acceptsEntryWithTimestampGreaterThanThreshold() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      LogEntry entry = new LogEntry(100, new byte[100], 2000L);

      FilterResult result = filter.matches(entry);

      assertTrue(result.isAccepted(), "Entry with timestamp > threshold should be accepted");
      assertTrue(result.matches());
    }

    @Test
    @DisplayName("matches rejects entry with timestamp less than threshold")
    void rejectsEntryWithTimestampLessThanThreshold() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      LogEntry entry = new LogEntry(100, new byte[100], 500L);

      FilterResult result = filter.matches(entry);

      assertTrue(result.isRejected(), "Entry with timestamp < threshold should be rejected");
      assertFalse(result.matches());
    }

    @Test
    @DisplayName("matches with zero threshold accepts positive timestamp")
    void zeroThresholdAcceptsPositiveTimestamp() {
      ReadFilter filter = new AfterTimestampFilter(0L);
      LogEntry entry = new LogEntry(100, new byte[100], 1L);

      FilterResult result = filter.matches(entry);

      assertTrue(result.isAccepted());
    }

    @Test
    @DisplayName("matches with zero threshold rejects negative timestamp")
    void zeroThresholdRejectsNegativeTimestamp() {
      ReadFilter filter = new AfterTimestampFilter(0L);
      LogEntry entry = new LogEntry(100, new byte[100], -1L);

      FilterResult result = filter.matches(entry);

      assertTrue(result.isRejected());
    }

    @Test
    @DisplayName("matches with negative threshold accepts all non-negative timestamps")
    void negativeThresholdAcceptsNonNegativeTimestamps() {
      ReadFilter filter = new AfterTimestampFilter(-1000L);
      LogEntry entry = new LogEntry(100, new byte[100], 0L);

      FilterResult result = filter.matches(entry);

      assertTrue(result.isAccepted());
    }

    @Test
    @DisplayName("matches with negative threshold accepts equal negative timestamp")
    void negativeThresholdAcceptsEqualNegativeTimestamp() {
      ReadFilter filter = new AfterTimestampFilter(-1000L);
      LogEntry entry = new LogEntry(100, new byte[100], -1000L);

      FilterResult result = filter.matches(entry);

      assertTrue(result.isAccepted());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 500L, 1000L, 2000L, 10000L})
    @DisplayName("matches returns correct result for various thresholds")
    void matchesCorrectResultForVariousThresholds(long timestamp) {
      long threshold = 1000L;
      ReadFilter filter = new AfterTimestampFilter(threshold);
      LogEntry entry = new LogEntry(100, new byte[100], timestamp);

      FilterResult result = filter.matches(entry);

      assertEquals(timestamp >= threshold, result.isAccepted());
    }
  }

  @Nested
  @DisplayName("FilterResult Content Tests")
  class FilterResultContentTests {

    @Test
    @DisplayName("matches returns FilterResult with correct entry")
    void matchesReturnsCorrectEntry() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      LogEntry entry = new LogEntry(100, new byte[100], 2000L);

      FilterResult result = filter.matches(entry);

      assertEquals(entry, result.entry());
    }

    @Test
    @DisplayName("matches returns FilterResult with correct filter name")
    void matchesReturnsCorrectFilterName() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      LogEntry entry = new LogEntry(100, new byte[100], 2000L);

      FilterResult result = filter.matches(entry);

      assertEquals(ReadFilterType.AFTER_TIMESTAMP.toString(), result.filterName());
    }

    @Test
    @DisplayName("matches returns non-null FilterResult")
    void matchesReturnsNonNullResult() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      LogEntry entry = new LogEntry(100, new byte[100], 2000L);

      FilterResult result = filter.matches(entry);

      assertNotNull(result);
    }
  }

  @Nested
  @DisplayName("Name Method Tests")
  class NameMethodTests {

    @Test
    @DisplayName("name returns AFTER_TIMESTAMP")
    void nameReturnsAfterTimestampType() {
      ReadFilter filter = new AfterTimestampFilter(1000L);

      ReadFilterType name = filter.name();

      assertEquals(ReadFilterType.AFTER_TIMESTAMP, name);
    }

    @Test
    @DisplayName("name always returns same enum value")
    void nameAlwaysReturnsSameValue() {
      ReadFilter filter = new AfterTimestampFilter(1000L);

      ReadFilterType name1 = filter.name();
      ReadFilterType name2 = filter.name();

      assertEquals(name1, name2);
      assertSame(name1, name2);
    }

    @Test
    @DisplayName("name returns non-null")
    void nameReturnsNonNull() {
      ReadFilter filter = new AfterTimestampFilter(1000L);

      ReadFilterType name = filter.name();

      assertNotNull(name);
    }
  }

  @Nested
  @DisplayName("CanSkipSegment Method Tests")
  class CanSkipSegmentMethodTests {

    @Test
    @DisplayName("canSkipSegment returns true when maxTimestamp < threshold")
    void skipSegmentWhenMaxTimestampLessThanThreshold() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment_0001.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              500L,
              900L);

      boolean canSkip = filter.canSkipSegment(segment);

      assertTrue(canSkip, "Should skip segment when maxTimestamp < threshold");
    }

    @Test
    @DisplayName("canSkipSegment returns false when maxTimestamp equals threshold")
    void dontSkipSegmentWhenMaxTimestampEqualsThreshold() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment_0001.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              500L,
              1000L);

      boolean canSkip = filter.canSkipSegment(segment);

      assertFalse(canSkip, "Should NOT skip segment when maxTimestamp == threshold");
    }

    @Test
    @DisplayName("canSkipSegment returns false when maxTimestamp > threshold")
    void dontSkipSegmentWhenMaxTimestampGreaterThanThreshold() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment_0001.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              500L,
              1500L);

      boolean canSkip = filter.canSkipSegment(segment);

      assertFalse(canSkip, "Should NOT skip segment when maxTimestamp > threshold");
    }

    @Test
    @DisplayName("canSkipSegment with zero threshold and zero maxTimestamp")
    void zeroThresholdZeroMaxTimestamp() {
      ReadFilter filter = new AfterTimestampFilter(0L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              0L,
              0L);

      boolean canSkip = filter.canSkipSegment(segment);

      assertFalse(canSkip);
    }

    @Test
    @DisplayName("canSkipSegment with negative threshold and positive maxTimestamp")
    void negativeThresholdPositiveMaxTimestamp() {
      ReadFilter filter = new AfterTimestampFilter(-1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              1000L,
              2000L);

      boolean canSkip = filter.canSkipSegment(segment);

      assertFalse(canSkip);
    }

    @Test
    @DisplayName(
        "canSkipSegment with negative threshold and negative maxTimestamp less than threshold")
    void negativeThresholdNegativeMaxTimestampLessThanThreshold() {
      ReadFilter filter = new AfterTimestampFilter(-500L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              -2000L,
              -1000L);

      boolean canSkip = filter.canSkipSegment(segment);

      assertTrue(canSkip);
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("matches with boundary timestamp Long.MAX_VALUE")
    void matchesWithMaxLongTimestamp() {
      ReadFilter filter = new AfterTimestampFilter(Long.MAX_VALUE - 1);
      LogEntry entry = new LogEntry(100, new byte[100], Long.MAX_VALUE);

      FilterResult result = filter.matches(entry);

      assertTrue(result.isAccepted());
    }

    @Test
    @DisplayName("matches with boundary timestamp Long.MIN_VALUE")
    void matchesWithMinLongTimestamp() {
      ReadFilter filter = new AfterTimestampFilter(Long.MIN_VALUE + 1);
      LogEntry entry = new LogEntry(100, new byte[100], Long.MIN_VALUE);

      FilterResult result = filter.matches(entry);

      assertTrue(result.isRejected());
    }

    @Test
    @DisplayName("canSkipSegment with maxTimestamp at Long.MAX_VALUE")
    void canSkipSegmentWithMaxLongTimestamp() {
      ReadFilter filter = new AfterTimestampFilter(Long.MAX_VALUE);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              0L,
              Long.MAX_VALUE - 1);

      boolean canSkip = filter.canSkipSegment(segment);

      assertTrue(canSkip);
    }

    @Test
    @DisplayName("matches with empty entry data")
    void matchesWithEmptyEntryData() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      LogEntry entry = new LogEntry(0, new byte[0], 1500L);

      FilterResult result = filter.matches(entry);

      assertTrue(result.isAccepted());
    }
  }

  @Nested
  @DisplayName("Interface Implementation Tests")
  class InterfaceImplementationTests {

    @Test
    @DisplayName("AfterTimestampFilter implements ReadFilter")
    void implementsReadFilter() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      assertInstanceOf(ReadFilter.class, filter);
    }

    @Test
    @DisplayName("AfterTimestampFilter is final class")
    void isFinalClass() {
      assertTrue(java.lang.reflect.Modifier.isFinal(AfterTimestampFilter.class.getModifiers()));
    }

    @Test
    @DisplayName("AfterTimestampFilter provides all required methods")
    void providesAllRequiredMethods() {
      ReadFilter filter = new AfterTimestampFilter(1000L);

      assertNotNull(filter.matches(new LogEntry(100, new byte[100], 1500L)));
      assertNotNull(filter.name());
    }
  }
}
