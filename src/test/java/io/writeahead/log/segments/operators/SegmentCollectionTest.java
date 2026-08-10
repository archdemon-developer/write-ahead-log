package io.writeahead.log.segments.operators;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.strategies.RotationPolicyType;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.models.results.TruncateSegmentsResult;
import io.writeahead.log.models.states.RotationDecision;
import io.writeahead.log.models.states.SegmentState;
import io.writeahead.log.segments.filter.truncate.BeforeTimestampTruncateFilter;
import io.writeahead.log.segments.filter.truncate.TruncateFilter;
import io.writeahead.log.segments.policies.RotationPolicy;
import io.writeahead.log.segments.policies.SizeBasedRotationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SegmentCollection")
class SegmentCollectionTest {

  @Nested
  @DisplayName("Initialization")
  class InitializationTests {

    @Test
    @DisplayName("creates empty collection")
    void createsEmptyCollection() {
      SegmentCollection collection = new SegmentCollection();

      assertEquals(0, collection.size());
      assertTrue(collection.getSegments().isEmpty());
    }
  }

  @Nested
  @DisplayName("Adding Segments")
  class AddingSegmentsTests {

    SegmentCollection collection;

    @BeforeEach
    void setUp() {
      collection = new SegmentCollection();
    }

    @Test
    @DisplayName("adds single segment")
    void addsSingleSegment() {
      SegmentMetadata segmentOne = OperatorsTestUtils.createSegmentMetadata(1L, 1000L);

      collection.add(segmentOne);

      assertEquals(1, collection.size());
    }

    @Test
    @DisplayName("adds multiple segments in order")
    void addsMultipleSegmentsInOrder() {
      SegmentMetadata segmentOne = OperatorsTestUtils.createSegmentMetadata(1L, 1000L);
      SegmentMetadata segmentTwo = OperatorsTestUtils.createSegmentMetadata(2L, 2000L);
      SegmentMetadata segmentThree = OperatorsTestUtils.createSegmentMetadata(3L, 3000L);

      collection.add(segmentOne);
      collection.add(segmentTwo);
      collection.add(segmentThree);

      assertEquals(3, collection.size());
    }

    @Test
    @DisplayName("getSegments returns copy of internal list")
    void getSegmentsReturnsCopyOfInternalList() {
      SegmentMetadata segmentOne = OperatorsTestUtils.createSegmentMetadata(1L, 1000L);
      collection.add(segmentOne);

      var segments = collection.getSegments();
      segments.clear();

      assertEquals(1, collection.size());
    }
  }

  @Nested
  @DisplayName("Rotation Delegation")
  class RotationDelegationTests {

    SegmentCollection collection;
    RotationPolicy sizeBasedPolicy;

    @BeforeEach
    void setUp() {
      collection = new SegmentCollection();
      sizeBasedPolicy = new SizeBasedRotationPolicy();
    }

    @Test
    @DisplayName("shouldRotate delegates to policy")
    void shouldRotateDelegatesToPolicy() {
      SegmentState openSegment = SegmentState.emptyOpenSegment(1L, System.currentTimeMillis());
      long maxSize = 1000L;

      RotationDecision decision = collection.shouldRotate(sizeBasedPolicy, openSegment, maxSize);

      assertFalse(decision.shouldRotate());
      assertEquals(RotationPolicyType.SIZE_BASED, decision.policyName());
    }

    @Test
    @DisplayName("shouldRotate returns decision with correct policy name")
    void shouldRotateReturnsDecisionWithCorrectPolicyName() {
      SegmentState openSegment = SegmentState.emptyOpenSegment(1L, System.currentTimeMillis());

      RotationDecision decision = collection.shouldRotate(sizeBasedPolicy, openSegment, 1000L);

      assertEquals(RotationPolicyType.SIZE_BASED, decision.policyName());
    }

    @Test
    @DisplayName("shouldRotate with finalized segment")
    void shouldRotateWithFinalizedSegment() {
      SegmentState finalizedSegment =
          SegmentState.withEntries(1L, 10L, 500L, 1000L, 2000L, System.currentTimeMillis(), true);

      RotationDecision decision = collection.shouldRotate(sizeBasedPolicy, finalizedSegment, 1000L);

      assertFalse(decision.shouldRotate());
      assertTrue(decision.utilizationPercent() == 100);
    }
  }

  @Nested
  @DisplayName("Truncation")
  class TruncationTests {

    SegmentCollection collection;
    TruncateFilter beforeTimestamp1500;

    @BeforeEach
    void setUp() {
      collection = new SegmentCollection();
      beforeTimestamp1500 = new BeforeTimestampTruncateFilter(1500L);
    }

    @Test
    @DisplayName("truncateMatching with no matching segments")
    void truncateMatchingWithNoMatchingSegments() {
      SegmentMetadata segmentOne =
          OperatorsTestUtils.createSegmentMetadata(1L, 1000L, 2000L, 3000L, 5L, 500L);
      collection.add(segmentOne);

      TruncateSegmentsResult result = collection.truncateMatching(beforeTimestamp1500);

      assertFalse(result.wereSegmentsRemoved());
      assertEquals(0, result.segmentsRemoved());
      assertEquals(1, result.oldestRemainingSequence());
    }

    @Test
    @DisplayName("truncateMatching with single matching segment keeps at least one")
    void truncateMatchingWithSingleMatchingSegmentKeepsAtLeastOne() {
      SegmentMetadata onlySegment =
          OperatorsTestUtils.createSegmentMetadata(1L, 1000L, 100L, 1000L, 5L, 500L);
      collection.add(onlySegment);

      TruncateSegmentsResult result = collection.truncateMatching(beforeTimestamp1500);

      assertEquals(0, result.segmentsRemoved());
      assertEquals(1, result.oldestRemainingSequence());
      assertEquals(1, collection.size());
    }

    @Test
    @DisplayName("truncateMatching removes multiple matching segments")
    void truncateMatchingRemovesMultipleMatchingSegments() {
      SegmentMetadata segmentOne =
          OperatorsTestUtils.createSegmentMetadata(1L, 1000L, 100L, 1000L, 5L, 500L);
      SegmentMetadata segmentTwo =
          OperatorsTestUtils.createSegmentMetadata(2L, 2000L, 1100L, 1200L, 5L, 500L);
      SegmentMetadata segmentThree =
          OperatorsTestUtils.createSegmentMetadata(3L, 3000L, 2000L, 3000L, 5L, 500L);

      collection.add(segmentOne);
      collection.add(segmentTwo);
      collection.add(segmentThree);

      TruncateSegmentsResult result = collection.truncateMatching(beforeTimestamp1500);

      assertEquals(2, result.segmentsRemoved());
      assertEquals(3, result.oldestRemainingSequence());
      assertEquals(1, collection.size());
    }

    @Test
    @DisplayName("truncateMatching returns correct removed segments list")
    void truncateMatchingReturnsCorrectRemovedSegmentsList() {
      SegmentMetadata segmentOne =
          OperatorsTestUtils.createSegmentMetadata(1L, 1000L, 100L, 1000L, 5L, 500L);
      SegmentMetadata segmentTwo =
          OperatorsTestUtils.createSegmentMetadata(2L, 2000L, 1100L, 1200L, 5L, 500L);
      SegmentMetadata segmentThree =
          OperatorsTestUtils.createSegmentMetadata(3L, 3000L, 2000L, 3000L, 5L, 500L);

      collection.add(segmentOne);
      collection.add(segmentTwo);
      collection.add(segmentThree);

      TruncateSegmentsResult result = collection.truncateMatching(beforeTimestamp1500);

      assertEquals(2, result.getSegmentsToDelete().size());
      assertEquals(1L, result.getSegmentsToDelete().get(0).sequenceNumber());
      assertEquals(2L, result.getSegmentsToDelete().get(1).sequenceNumber());
    }

    @Test
    @DisplayName("truncateMatching with empty collection")
    void truncateMatchingWithEmptyCollection() {
      TruncateSegmentsResult result = collection.truncateMatching(beforeTimestamp1500);

      assertEquals(0, result.segmentsRemoved());
      assertEquals(0, result.oldestRemainingSequence());
      assertEquals(0, collection.size());
    }

    @Test
    @DisplayName("truncateMatching updates collection state")
    void truncateMatchingUpdatesCollectionState() {
      SegmentMetadata segmentOne =
          OperatorsTestUtils.createSegmentMetadata(1L, 1000L, 100L, 1000L, 5L, 500L);
      SegmentMetadata segmentTwo =
          OperatorsTestUtils.createSegmentMetadata(2L, 2000L, 2000L, 3000L, 5L, 500L);

      collection.add(segmentOne);
      collection.add(segmentTwo);

      collection.truncateMatching(beforeTimestamp1500);

      assertEquals(1, collection.size());
      assertEquals(2L, collection.getNewestSequenceNumber());
    }
  }

  @Nested
  @DisplayName("Sequence Number Queries")
  class SequenceNumberQueriesTests {

    SegmentCollection collection;

    @BeforeEach
    void setUp() {
      collection = new SegmentCollection();
    }

    @Test
    @DisplayName("getOldestSequenceNumber with empty collection")
    void getOldestSequenceNumberWithEmptyCollection() {
      assertEquals(0, collection.getOldestSequenceNumber());
    }

    @Test
    @DisplayName("getOldestSequenceNumber with single segment")
    void getOldestSequenceNumberWithSingleSegment() {
      SegmentMetadata segment = OperatorsTestUtils.createSegmentMetadata(5L, 1000L);
      collection.add(segment);

      assertEquals(5L, collection.getOldestSequenceNumber());
    }

    @Test
    @DisplayName("getOldestSequenceNumber with multiple segments")
    void getOldestSequenceNumberWithMultipleSegments() {
      collection.add(OperatorsTestUtils.createSegmentMetadata(3L, 1000L));
      collection.add(OperatorsTestUtils.createSegmentMetadata(1L, 2000L));
      collection.add(OperatorsTestUtils.createSegmentMetadata(2L, 3000L));

      assertEquals(3L, collection.getOldestSequenceNumber());
    }

    @Test
    @DisplayName("getNewestSequenceNumber with empty collection")
    void getNewestSequenceNumberWithEmptyCollection() {
      assertEquals(0, collection.getNewestSequenceNumber());
    }

    @Test
    @DisplayName("getNewestSequenceNumber with single segment")
    void getNewestSequenceNumberWithSingleSegment() {
      SegmentMetadata segment = OperatorsTestUtils.createSegmentMetadata(5L, 1000L);
      collection.add(segment);

      assertEquals(5L, collection.getNewestSequenceNumber());
    }

    @Test
    @DisplayName("getNewestSequenceNumber with multiple segments")
    void getNewestSequenceNumberWithMultipleSegments() {
      collection.add(OperatorsTestUtils.createSegmentMetadata(3L, 1000L));
      collection.add(OperatorsTestUtils.createSegmentMetadata(1L, 2000L));
      collection.add(OperatorsTestUtils.createSegmentMetadata(2L, 3000L));

      assertEquals(2L, collection.getNewestSequenceNumber());
    }
  }

  @Nested
  @DisplayName("Timestamp-based Queries")
  class TimestampBasedQueriesTests {

    SegmentCollection collection;

    @BeforeEach
    void setUp() {
      collection = new SegmentCollection();
    }

    @Test
    @DisplayName("findSegmentsAfterTimestamp with empty collection")
    void findSegmentsAfterTimestampWithEmptyCollection() {
      var result = collection.findSegmentsAfterTimestamp(1000L);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findSegmentsAfterTimestamp returns segments with minTimestamp >= threshold")
    void findSegmentsAfterTimestampReturnsSegmentsWithMinTimestampGreaterOrEqual() {
      SegmentMetadata segmentOne =
          OperatorsTestUtils.createSegmentMetadata(1L, 1000L, 500L, 1500L, 5L, 500L);
      SegmentMetadata segmentTwo =
          OperatorsTestUtils.createSegmentMetadata(2L, 2000L, 1500L, 2500L, 5L, 500L);
      SegmentMetadata segmentThree =
          OperatorsTestUtils.createSegmentMetadata(3L, 3000L, 2500L, 3500L, 5L, 500L);

      collection.add(segmentOne);
      collection.add(segmentTwo);
      collection.add(segmentThree);

      var result = collection.findSegmentsAfterTimestamp(1500L);

      assertEquals(2, result.size());
      assertEquals(2L, result.get(0).sequenceNumber());
      assertEquals(3L, result.get(1).sequenceNumber());
    }

    @Test
    @DisplayName("findSegmentsAfterTimestamp with no matching segments")
    void findSegmentsAfterTimestampWithNoMatchingSegments() {
      SegmentMetadata segment =
          OperatorsTestUtils.createSegmentMetadata(1L, 1000L, 100L, 500L, 5L, 500L);
      collection.add(segment);

      var result = collection.findSegmentsAfterTimestamp(1000L);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findSegmentsAfterTimestamp returns all segments when threshold at minimum")
    void findSegmentsAfterTimestampReturnsAllSegmentsWhenThresholdAtMinimum() {
      SegmentMetadata segmentOne =
          OperatorsTestUtils.createSegmentMetadata(1L, 1000L, 500L, 1500L, 5L, 500L);
      SegmentMetadata segmentTwo =
          OperatorsTestUtils.createSegmentMetadata(2L, 2000L, 1500L, 2500L, 5L, 500L);

      collection.add(segmentOne);
      collection.add(segmentTwo);

      var result = collection.findSegmentsAfterTimestamp(500L);

      assertEquals(2, result.size());
    }

    @Test
    @DisplayName("findSegmentsAfterTimestamp with exact threshold match")
    void findSegmentsAfterTimestampWithExactThresholdMatch() {
      SegmentMetadata segment =
          OperatorsTestUtils.createSegmentMetadata(1L, 1000L, 1000L, 2000L, 5L, 500L);
      collection.add(segment);

      var result = collection.findSegmentsAfterTimestamp(1000L);

      assertEquals(1, result.size());
      assertEquals(1L, result.get(0).sequenceNumber());
    }
  }

  @Nested
  @DisplayName("Edge Cases and Safety")
  class EdgeCasesAndSafetyTests {

    SegmentCollection collection;

    @BeforeEach
    void setUp() {
      collection = new SegmentCollection();
    }

    @Test
    @DisplayName("ensures at least one segment remains during truncation")
    void ensuresAtLeastOneSegmentRemainsDoesNotRemoveLastSegment() {
      TruncateFilter deleteAllByTimestamp = new BeforeTimestampTruncateFilter(Long.MAX_VALUE);

      SegmentMetadata onlySegment =
          OperatorsTestUtils.createSegmentMetadata(1L, 1000L, 100L, 1000L, 5L, 500L);
      collection.add(onlySegment);

      TruncateSegmentsResult result = collection.truncateMatching(deleteAllByTimestamp);

      assertEquals(0, result.segmentsRemoved());
      assertEquals(1, collection.size());
    }

    @Test
    @DisplayName("size returns accurate count")
    void sizeReturnsAccurateCount() {
      assertEquals(0, collection.size());

      for (int i = 1; i <= 5; i++) {
        collection.add(OperatorsTestUtils.createSegmentMetadata((long) i, 1000L + i * 1000));
      }

      assertEquals(5, collection.size());
    }

    @Test
    @DisplayName("getSegments returns independent copy")
    void getSegmentsReturnsIndependentCopy() {
      SegmentMetadata segment = OperatorsTestUtils.createSegmentMetadata(1L, 1000L);
      collection.add(segment);

      var segments = collection.getSegments();
      segments.add(OperatorsTestUtils.createSegmentMetadata(2L, 2000L));

      assertEquals(1, collection.size());
      assertEquals(2, segments.size());
    }

    @Test
    @DisplayName("truncateMatching with timestamp-based filter")
    void truncateMatchingWithTimestampBasedFilter() {
      SegmentMetadata segmentOne =
          OperatorsTestUtils.createSegmentMetadata(1L, 1000L, 100L, 500L, 5L, 500L);
      SegmentMetadata segmentTwo =
          OperatorsTestUtils.createSegmentMetadata(2L, 2000L, 600L, 1000L, 5L, 500L);
      SegmentMetadata segmentThree =
          OperatorsTestUtils.createSegmentMetadata(3L, 3000L, 1100L, 1500L, 5L, 500L);

      collection.add(segmentOne);
      collection.add(segmentTwo);
      collection.add(segmentThree);

      TruncateFilter deleteBeforeTimestamp600 = new BeforeTimestampTruncateFilter(600L);
      TruncateSegmentsResult result = collection.truncateMatching(deleteBeforeTimestamp600);

      assertEquals(1, result.segmentsRemoved());
      assertEquals(2, collection.size());
      assertEquals(2L, collection.getOldestSequenceNumber());
    }
  }
}
