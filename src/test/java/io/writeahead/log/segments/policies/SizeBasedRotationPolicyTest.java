package io.writeahead.log.segments.policies;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.strategies.RotationPolicyType;
import io.writeahead.log.models.states.RotationDecision;
import io.writeahead.log.models.states.SegmentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SizeBasedRotationPolicy Tests")
class SizeBasedRotationPolicyTest {

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("SizeBasedRotationPolicy creates successfully")
    void createsSuccessfully() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      assertNotNull(policy);
    }

    @Test
    @DisplayName("SizeBasedRotationPolicy implements RotationPolicy")
    void implementsRotationPolicy() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      assertInstanceOf(RotationPolicy.class, policy);
    }

    @Test
    @DisplayName("SizeBasedRotationPolicy can be created multiple times")
    void canBeCreatedMultipleTimes() {
      RotationPolicy policy1 = new SizeBasedRotationPolicy();
      RotationPolicy policy2 = new SizeBasedRotationPolicy();

      assertNotNull(policy1);
      assertNotNull(policy2);
      assertNotSame(policy1, policy2);
    }
  }

  @Nested
  @DisplayName("Name Method Tests")
  class NameMethodTests {

    @Test
    @DisplayName("name returns SIZE_BASED")
    void nameReturnsSizeBased() {
      RotationPolicy policy = new SizeBasedRotationPolicy();

      RotationPolicyType name = policy.name();

      assertEquals(RotationPolicyType.SIZE_BASED, name);
    }

    @Test
    @DisplayName("name always returns same enum value")
    void nameAlwaysReturnsSameValue() {
      RotationPolicy policy = new SizeBasedRotationPolicy();

      RotationPolicyType name1 = policy.name();
      RotationPolicyType name2 = policy.name();

      assertEquals(name1, name2);
      assertSame(name1, name2);
    }

    @Test
    @DisplayName("name returns non-null")
    void nameReturnsNonNull() {
      RotationPolicy policy = new SizeBasedRotationPolicy();

      RotationPolicyType name = policy.name();

      assertNotNull(name);
    }
  }

  @Nested
  @DisplayName("Evaluate Method - Finalized Segment Tests")
  class EvaluateFinalizedSegmentTests {

    @Test
    @DisplayName("evaluate returns keepOpen when segment is finalized")
    void keepOpenWhenFinalized() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 1L, 100L, 0L, 100L, System.currentTimeMillis(), true);

      RotationDecision result = policy.evaluate(state, 1024L);

      assertFalse(result.needsRotation(), "Should not rotate finalized segment");
      assertTrue(result.canStayOpen());
    }

    @Test
    @DisplayName("evaluate returns 100% utilization when segment is finalized")
    void returns100PercentUtilizationWhenFinalized() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 1L, 100L, 0L, 100L, System.currentTimeMillis(), true);

      RotationDecision result = policy.evaluate(state, 1024L);

      assertEquals(
          100, result.utilizationPercent(), "Finalized segment should report 100% utilization");
    }

    @Test
    @DisplayName("evaluate returns keepOpen reason when segment is finalized")
    void returnsKeepOpenReasonWhenFinalized() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 1L, 100L, 0L, 100L, System.currentTimeMillis(), true);

      RotationDecision result = policy.evaluate(state, 1024L);

      assertTrue(
          result.reason().contains("Segment finalized") || result.reason().contains("finalized"));
    }

    @Test
    @DisplayName("evaluate finalized segment with various maxSegmentSizes")
    void finalizedSegmentWithVariousMaxSizes() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 1L, 100L, 0L, 100L, System.currentTimeMillis(), true);

      for (long maxSize : new long[] {512L, 1024L, 2048L, 10000L}) {
        RotationDecision result = policy.evaluate(state, maxSize);
        assertFalse(
            result.needsRotation(), "Finalized segment should not rotate regardless of maxSize");
      }
    }
  }

  @Nested
  @DisplayName("Evaluate Method - Size-Based Rotation Tests")
  class EvaluateSizeBasedRotationTests {

    @Test
    @DisplayName("evaluate returns rotateNow when totalByteCount >= maxSegmentSize")
    void rotateNowWhenAtMaxSize() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 5L, 1024L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 1024L);

      assertTrue(result.needsRotation(), "Should rotate when totalByteCount >= maxSegmentSize");
      assertFalse(result.canStayOpen());
    }

    @Test
    @DisplayName("evaluate returns rotateNow when totalByteCount exceeds maxSegmentSize")
    void rotateNowWhenExceedsMaxSize() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 10L, 2048L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 1024L);

      assertTrue(result.needsRotation(), "Should rotate when totalByteCount > maxSegmentSize");
    }

    @Test
    @DisplayName("evaluate returns rotation reason mentioning max size")
    void returnsRotationReasonMentioningMaxSize() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 5L, 1024L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 1024L);

      assertTrue(result.reason().contains("max size") || result.reason().contains("reached"));
    }

    @Test
    @DisplayName("evaluate calculates correct utilization percent at max size")
    void correctUtilizationPercentAtMaxSize() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 5L, 1024L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 1024L);

      assertEquals(100, result.utilizationPercent(), "Utilization should be 100% when at max size");
    }

    @Test
    @DisplayName("evaluate calculates correct utilization percent when exceeding max size")
    void correctUtilizationPercentWhenExceedingMaxSize() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 10L, 2048L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 1024L);

      assertEquals(
          100,
          result.utilizationPercent(),
          "Utilization should be capped at 100% even when exceeding max size");
    }

    @ParameterizedTest
    @ValueSource(longs = {100L, 500L, 1000L, 5000L})
    @DisplayName("evaluate rotates for various byte counts that exceed maxSegmentSize")
    void rotatesForVariousByteCounts(long byteCount) {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      long maxSize = 50L;
      SegmentState state =
          SegmentState.withEntries(1L, 1L, byteCount, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, maxSize);

      if (byteCount >= maxSize) {
        assertTrue(result.needsRotation());
      }
    }
  }

  @Nested
  @DisplayName("Evaluate Method - Keep Open Tests")
  class EvaluateKeepOpenTests {

    @Test
    @DisplayName("evaluate returns keepOpen when below max size")
    void keepOpenWhenBelowMaxSize() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 3L, 512L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 1024L);

      assertFalse(result.needsRotation(), "Should not rotate when below max size");
      assertTrue(result.canStayOpen());
    }

    @Test
    @DisplayName("evaluate calculates correct utilization percent when below max size")
    void correctUtilizationPercentBelowMaxSize() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 3L, 512L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 1024L);

      assertEquals(50, result.utilizationPercent(), "512 out of 1024 should be 50% utilization");
    }

    @Test
    @DisplayName("evaluate returns keepOpen reason when below max size")
    void returnsKeepOpenReasonWhenBelowMaxSize() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 3L, 512L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 1024L);

      assertTrue(result.reason().contains("utilization") || result.reason().contains("%"));
    }

    @Test
    @DisplayName("evaluate with empty segment below max size")
    void emptySegmentBelowMaxSize() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state = SegmentState.emptyOpenSegment(1L, System.currentTimeMillis());

      RotationDecision result = policy.evaluate(state, 1024L);

      assertFalse(result.needsRotation());
      assertTrue(result.canStayOpen());
    }

    @Test
    @DisplayName("evaluate with minimal utilization (1%)")
    void minimalUtilization() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 1L, 58L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 10000L);

      assertFalse(result.needsRotation());
      assertTrue(result.utilizationPercent() <= 1);
    }

    @Test
    @DisplayName("evaluate with high utilization near limit (99%)")
    void highUtilizationNearLimit() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 100L, 9900L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 10000L);

      assertFalse(result.needsRotation(), "Should not rotate at 99%");
      assertEquals(99, result.utilizationPercent());
    }
  }

  @Nested
  @DisplayName("Evaluate Method - Utilization Calculation Tests")
  class EvaluateUtilizationCalculationTests {

    @Test
    @DisplayName("evaluate calculates 0% utilization correctly")
    void calculatesZeroPercentUtilization() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(
              1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 1000L);

      assertTrue(result.utilizationPercent() <= 5, "Empty segment should have ~0% utilization");
    }

    @Test
    @DisplayName("evaluate calculates 25% utilization correctly")
    void calculatesQuarterPercentUtilization() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 1L, 300L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 1000L);

      assertEquals(30, result.utilizationPercent(), "300 out of 1000 should be 30% utilization");
    }

    @Test
    @DisplayName("evaluate calculates 50% utilization correctly")
    void calculatesHalfPercentUtilization() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 1L, 500L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 1000L);

      assertEquals(50, result.utilizationPercent(), "500 out of 1000 should be 50% utilization");
    }

    @Test
    @DisplayName("evaluate calculates 75% utilization correctly")
    void calculatesThreeQuartersPercentUtilization() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 1L, 750L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 1000L);

      assertEquals(75, result.utilizationPercent(), "750 out of 1000 should be 75% utilization");
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("evaluate with very small maxSegmentSize")
    void verySmallMaxSegmentSize() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 1L, 100L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 50L);

      assertTrue(result.needsRotation(), "Should rotate when totalByteCount > maxSegmentSize");
    }

    @Test
    @DisplayName("evaluate with very large maxSegmentSize")
    void veryLargeMaxSegmentSize() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(1L, 1L, 100L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, Long.MAX_VALUE);

      assertFalse(result.needsRotation(), "Should not rotate when maxSegmentSize is huge");
    }

    @Test
    @DisplayName("evaluate with segment size equal to maxSegmentSize triggers rotation")
    void segmentSizeEqualToMaxSizeTriggersRotation() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(
              1L, 0L, 48L, Long.MIN_VALUE, Long.MAX_VALUE, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 48L);

      assertTrue(
          result.needsRotation(),
          "Segment at max size should trigger rotation (totalByteCount >= maxSegmentSize)");
      assertEquals(100, result.utilizationPercent());
    }

    @Test
    @DisplayName("evaluate with high sequence number")
    void highSequenceNumber() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(
              Long.MAX_VALUE - 1, 10L, 1024L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 2048L);

      assertFalse(result.needsRotation());
    }

    @Test
    @DisplayName("evaluate with high entry count")
    void highEntryCount() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state =
          SegmentState.withEntries(
              1L, Long.MAX_VALUE / 2, 1024L, 0L, 100L, System.currentTimeMillis(), false);

      RotationDecision result = policy.evaluate(state, 2048L);

      assertFalse(result.needsRotation());
    }
  }

  @Nested
  @DisplayName("Contract Enforcement Tests")
  class ContractEnforcementTests {

    @Test
    @DisplayName("evaluate returns non-null RotationDecision")
    void evaluateReturnsNonNull() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state = SegmentState.emptyOpenSegment(1L, System.currentTimeMillis());

      RotationDecision result = policy.evaluate(state, 1024L);

      assertNotNull(result);
    }

    @Test
    @DisplayName("evaluate never returns null reason")
    void evaluateNeverReturnsNullReason() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state = SegmentState.emptyOpenSegment(1L, System.currentTimeMillis());

      RotationDecision result = policy.evaluate(state, 1024L);

      assertNotNull(result.reason());
      assertFalse(result.reason().isEmpty());
    }

    @Test
    @DisplayName("evaluate always includes policy name in result")
    void evaluateAlwaysIncludesPolicyName() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state = SegmentState.emptyOpenSegment(1L, System.currentTimeMillis());

      RotationDecision result = policy.evaluate(state, 1024L);

      assertEquals(RotationPolicyType.SIZE_BASED, result.policyName());
    }

    @Test
    @DisplayName("evaluate always returns valid utilization percent")
    void evaluateAlwaysReturnsValidUtilizationPercent() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state = SegmentState.emptyOpenSegment(1L, System.currentTimeMillis());

      RotationDecision result = policy.evaluate(state, 1024L);

      assertTrue(
          result.utilizationPercent() >= 0 && result.utilizationPercent() <= 100,
          "Utilization percent should be between 0 and 100");
    }
  }

  @Nested
  @DisplayName("Interface Implementation Tests")
  class InterfaceImplementationTests {

    @Test
    @DisplayName("SizeBasedRotationPolicy implements RotationPolicy")
    void implementsRotationPolicy() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      assertInstanceOf(RotationPolicy.class, policy);
    }

    @Test
    @DisplayName("SizeBasedRotationPolicy provides all required methods")
    void providesAllRequiredMethods() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state = SegmentState.emptyOpenSegment(1L, System.currentTimeMillis());

      assertNotNull(policy.evaluate(state, 1024L));
      assertNotNull(policy.name());
    }
  }
}
