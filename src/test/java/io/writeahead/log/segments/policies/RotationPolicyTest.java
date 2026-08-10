package io.writeahead.log.segments.policies;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.strategies.RotationPolicyType;
import io.writeahead.log.models.states.RotationDecision;
import io.writeahead.log.models.states.SegmentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RotationPolicy Interface Tests")
class RotationPolicyTest {

  @Nested
  @DisplayName("Interface Contract Tests")
  class InterfaceContractTests {

    @Test
    @DisplayName("RotationPolicy is interface")
    void isInterface() {
      assertTrue(RotationPolicy.class.isInterface(), "RotationPolicy should be an interface");
    }

    @Test
    @DisplayName("RotationPolicy is not sealed")
    void isNotSealed() {
      assertFalse(RotationPolicy.class.isSealed(), "RotationPolicy should not be sealed");
    }
  }

  @Nested
  @DisplayName("Method Contract Tests")
  class MethodContractTests {

    @Test
    @DisplayName("RotationPolicy has evaluate method")
    void hasEvaluateMethod() throws NoSuchMethodException {
      assertDoesNotThrow(
          () -> RotationPolicy.class.getMethod("evaluate", SegmentState.class, long.class),
          "RotationPolicy should have evaluate(SegmentState, long) method");
    }

    @Test
    @DisplayName("RotationPolicy has name method")
    void hasNameMethod() throws NoSuchMethodException {
      assertDoesNotThrow(
          () -> RotationPolicy.class.getMethod("name"), "RotationPolicy should have name() method");
    }
  }

  @Nested
  @DisplayName("Method Signature Tests")
  class MethodSignatureTests {

    @Test
    @DisplayName("evaluate takes SegmentState and long parameters")
    void evaluateTakesCorrectParameters() throws NoSuchMethodException {
      java.lang.reflect.Method method =
          RotationPolicy.class.getMethod("evaluate", SegmentState.class, long.class);
      assertEquals(2, method.getParameterCount());
      assertEquals(SegmentState.class, method.getParameterTypes()[0]);
      assertEquals(long.class, method.getParameterTypes()[1]);
    }

    @Test
    @DisplayName("evaluate returns RotationDecision")
    void evaluateReturnsRotationDecision() throws NoSuchMethodException {
      java.lang.reflect.Method method =
          RotationPolicy.class.getMethod("evaluate", SegmentState.class, long.class);
      assertEquals(RotationDecision.class, method.getReturnType());
    }

    @Test
    @DisplayName("name takes no parameters")
    void nameHasNoParameters() throws NoSuchMethodException {
      java.lang.reflect.Method method = RotationPolicy.class.getMethod("name");
      assertEquals(0, method.getParameterCount());
    }

    @Test
    @DisplayName("name returns RotationPolicyType")
    void nameReturnsRotationPolicyType() throws NoSuchMethodException {
      java.lang.reflect.Method method = RotationPolicy.class.getMethod("name");
      assertEquals(RotationPolicyType.class, method.getReturnType());
    }
  }

  @Nested
  @DisplayName("Interface Method Return Type Tests")
  class InterfaceMethodReturnTypeTests {

    @Test
    @DisplayName("evaluate method returns RotationDecision")
    void evaluateReturnsRotationDecision() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state = SegmentState.emptyOpenSegment(1L, System.currentTimeMillis());

      RotationDecision result = policy.evaluate(state, 1024L);

      assertNotNull(result, "evaluate should return non-null RotationDecision");
      assertInstanceOf(RotationDecision.class, result);
    }

    @Test
    @DisplayName("name method returns RotationPolicyType")
    void nameReturnsRotationPolicyType() {
      RotationPolicy policy = new SizeBasedRotationPolicy();

      RotationPolicyType name = policy.name();

      assertNotNull(name, "name should return non-null RotationPolicyType");
      assertInstanceOf(RotationPolicyType.class, name);
    }
  }

  @Nested
  @DisplayName("Concrete Implementation via SizeBasedRotationPolicy")
  class ConcreteImplementationTests {

    @Test
    @DisplayName("SizeBasedRotationPolicy implements RotationPolicy")
    void sizeBasedRotationPolicyImplementsRotationPolicy() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      assertInstanceOf(RotationPolicy.class, policy);
    }

    @Test
    @DisplayName("RotationPolicy can be instantiated through SizeBasedRotationPolicy")
    void canBeInstantiatedThroughSizeBasedRotationPolicy() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      assertNotNull(policy);
    }
  }

  @Nested
  @DisplayName("Contract Enforcement Tests")
  class ContractEnforcementTests {

    @Test
    @DisplayName("evaluate must not return null")
    void evaluateMustNotReturnNull() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state = SegmentState.emptyOpenSegment(1L, System.currentTimeMillis());

      RotationDecision result = policy.evaluate(state, 1024L);

      assertNotNull(result, "evaluate must never return null");
    }

    @Test
    @DisplayName("name must not return null")
    void nameMustNotReturnNull() {
      RotationPolicy policy = new SizeBasedRotationPolicy();

      RotationPolicyType name = policy.name();

      assertNotNull(name, "name must never return null");
    }

    @Test
    @DisplayName("evaluate must handle null SegmentState")
    void evaluateMustHandleNullSegmentState() {
      RotationPolicy policy = new SizeBasedRotationPolicy();

      assertThrows(
          NullPointerException.class,
          () -> policy.evaluate(null, 1024L),
          "evaluate should throw NPE on null SegmentState");
    }
  }

  @Nested
  @DisplayName("RotationDecision Content Validation Tests")
  class RotationDecisionContentValidationTests {

    @Test
    @DisplayName("evaluate returns RotationDecision with non-null reason")
    void evaluateReturnsDecisionWithNonNullReason() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state = SegmentState.emptyOpenSegment(1L, System.currentTimeMillis());

      RotationDecision result = policy.evaluate(state, 1024L);

      assertNotNull(result.reason());
      assertFalse(result.reason().isEmpty());
    }

    @Test
    @DisplayName("evaluate returns RotationDecision with valid utilization percent")
    void evaluateReturnsDecisionWithValidUtilizationPercent() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state = SegmentState.emptyOpenSegment(1L, System.currentTimeMillis());

      RotationDecision result = policy.evaluate(state, 1024L);

      assertTrue(
          result.utilizationPercent() >= 0 && result.utilizationPercent() <= 100,
          "utilizationPercent should be between 0 and 100");
    }

    @Test
    @DisplayName("evaluate returns RotationDecision with correct policy name")
    void evaluateReturnsDecisionWithCorrectPolicyName() {
      RotationPolicy policy = new SizeBasedRotationPolicy();
      SegmentState state = SegmentState.emptyOpenSegment(1L, System.currentTimeMillis());

      RotationDecision result = policy.evaluate(state, 1024L);

      assertEquals(
          policy.name(),
          result.policyName(),
          "RotationDecision should reference the correct policy");
    }
  }
}
