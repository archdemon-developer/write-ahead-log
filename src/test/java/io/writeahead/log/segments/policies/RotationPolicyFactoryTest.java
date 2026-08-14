package io.writeahead.log.segments.policies;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.strategies.RotationPolicyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RotationPolicyFactory Tests")
class RotationPolicyFactoryTest {

  @Nested
  @DisplayName("Factory Method Tests")
  class FactoryMethodTests {

    @Test
    @DisplayName("create method exists")
    void createMethodExists() throws NoSuchMethodException {
      assertDoesNotThrow(
          () -> RotationPolicyFactory.class.getMethod("create", RotationPolicyType.class),
          "RotationPolicyFactory should have create(RotationPolicyType) method");
    }

    @Test
    @DisplayName("create returns RotationPolicy")
    void createReturnsRotationPolicy() throws NoSuchMethodException {
      java.lang.reflect.Method method =
          RotationPolicyFactory.class.getMethod("create", RotationPolicyType.class);
      assertEquals(RotationPolicy.class, method.getReturnType());
    }
  }

  @Nested
  @DisplayName("SIZE_BASED Policy Creation Tests")
  class SizeBasedPolicyCreationTests {

    @Test
    @DisplayName("create returns non-null policy for SIZE_BASED type")
    void createReturnsNonNullPolicyForSizeBased() {
      RotationPolicy policy = RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED);

      assertNotNull(policy, "create should return non-null RotationPolicy");
    }

    @Test
    @DisplayName("create returns SizeBasedRotationPolicy for SIZE_BASED type")
    void createReturnsSizeBasedRotationPolicyForSizeBased() {
      RotationPolicy policy = RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED);

      assertInstanceOf(
          SizeBasedRotationPolicy.class,
          policy,
          "create should return SizeBasedRotationPolicy for SIZE_BASED type");
    }

    @Test
    @DisplayName("create returns RotationPolicy instance for SIZE_BASED type")
    void createReturnsRotationPolicyInstanceForSizeBased() {
      RotationPolicy policy = RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED);

      assertInstanceOf(RotationPolicy.class, policy);
    }

    @Test
    @DisplayName("created SIZE_BASED policy has correct name")
    void createdSizeBasedPolicyHasCorrectName() {
      RotationPolicy policy = RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED);

      assertEquals(
          RotationPolicyType.SIZE_BASED,
          policy.name(),
          "Created policy should have SIZE_BASED name");
    }

    @Test
    @DisplayName("create produces different instances for multiple calls")
    void createProducesDifferentInstancesForMultipleCalls() {
      RotationPolicy policy1 = RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED);
      RotationPolicy policy2 = RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED);

      assertNotSame(policy1, policy2, "Each call to create should produce a new instance");
    }

    @Test
    @DisplayName("create produces functionally equivalent instances")
    void createProducesFunctionallyEquivalentInstances() {
      RotationPolicy policy1 = RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED);
      RotationPolicy policy2 = RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED);

      assertEquals(policy1.name(), policy2.name(), "Created policies should have the same name");
    }

    @Test
    @DisplayName("created SIZE_BASED policy can evaluate segments")
    void createdSizeBasedPolicyCanEvaluateSegments() {
      RotationPolicy policy = RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED);
      io.writeahead.log.models.states.SegmentState state =
          io.writeahead.log.models.states.SegmentState.emptyOpenSegment(
              1L, System.currentTimeMillis());

      assertDoesNotThrow(
          () -> policy.evaluate(state, 1024L),
          "Created policy should be able to evaluate segments");
    }
  }

  @Nested
  @DisplayName("Factory Switch Expression Tests")
  class FactorySwitchExpressionTests {

    @Test
    @DisplayName("factory uses switch expression")
    void factoryUsesSwitchExpression() {
      for (RotationPolicyType type : RotationPolicyType.values()) {
        assertDoesNotThrow(
            () -> RotationPolicyFactory.create(type), "Factory should handle " + type.name());
      }
    }

    @Test
    @DisplayName("factory returns non-null for all enum values")
    void factoryReturnsNonNullForAllEnumValues() {
      for (RotationPolicyType type : RotationPolicyType.values()) {
        RotationPolicy policy = RotationPolicyFactory.create(type);
        assertNotNull(policy, "Factory should return non-null for " + type.name());
      }
    }

    @Test
    @DisplayName("factory returns correct type for all enum values")
    void factoryReturnsCorrectTypeForAllEnumValues() {
      for (RotationPolicyType type : RotationPolicyType.values()) {
        RotationPolicy policy = RotationPolicyFactory.create(type);
        assertEquals(
            type, policy.name(), "Created policy should have matching name for " + type.name());
      }
    }
  }

  @Nested
  @DisplayName("Factory Contract Tests")
  class FactoryContractTests {

    @Test
    @DisplayName("create never returns null")
    void createNeverReturnsNull() {
      RotationPolicy policy = RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED);

      assertNotNull(policy, "create must never return null");
    }

    @Test
    @DisplayName("create always returns RotationPolicy instance")
    void createAlwaysReturnsRotationPolicyInstance() {
      RotationPolicy policy = RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED);

      assertInstanceOf(
          RotationPolicy.class, policy, "create should always return a RotationPolicy instance");
    }
  }

  @Nested
  @DisplayName("Factory Consistency Tests")
  class FactoryConsistencyTests {

    @Test
    @DisplayName("multiple calls to create return functionally equivalent policies")
    void multipleCallsReturnFunctionallyEquivalentPolicies() {
      RotationPolicy policy1 = RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED);
      RotationPolicy policy2 = RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED);

      io.writeahead.log.models.states.SegmentState state =
          io.writeahead.log.models.states.SegmentState.emptyOpenSegment(
              1L, System.currentTimeMillis());

      io.writeahead.log.models.states.RotationDecision result1 = policy1.evaluate(state, 1024L);
      io.writeahead.log.models.states.RotationDecision result2 = policy2.evaluate(state, 1024L);

      assertEquals(
          result1.shouldRotate(),
          result2.shouldRotate(),
          "Both policies should produce same rotation decision");
      assertEquals(
          result1.utilizationPercent(),
          result2.utilizationPercent(),
          "Both policies should calculate same utilization");
    }

    @Test
    @DisplayName("factory returns policies with correct policy name in decisions")
    void factoryReturnsPoliciesWithCorrectPolicyNameInDecisions() {
      RotationPolicy policy = RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED);
      io.writeahead.log.models.states.SegmentState state =
          io.writeahead.log.models.states.SegmentState.emptyOpenSegment(
              1L, System.currentTimeMillis());

      io.writeahead.log.models.states.RotationDecision decision = policy.evaluate(state, 1024L);

      assertEquals(
          RotationPolicyType.SIZE_BASED,
          decision.policyName(),
          "Decision should reference correct policy name");
    }
  }

  @Nested
  @DisplayName("Factory Static Method Tests")
  class FactoryStaticMethodTests {

    @Test
    @DisplayName("create is static method")
    void createIsStaticMethod() throws NoSuchMethodException {
      java.lang.reflect.Method method =
          RotationPolicyFactory.class.getMethod("create", RotationPolicyType.class);
      assertTrue(
          java.lang.reflect.Modifier.isStatic(method.getModifiers()),
          "create should be a static method");
    }

    @Test
    @DisplayName("RotationPolicyFactory can be used without instantiation")
    void canBeUsedWithoutInstantiation() {
      assertDoesNotThrow(
          () -> RotationPolicyFactory.create(RotationPolicyType.SIZE_BASED),
          "Factory should be usable as static utility");
    }
  }

  @Nested
  @DisplayName("Factory Type Safety Tests")
  class FactoryTypeSafetyTests {

    @Test
    @DisplayName("create enforces RotationPolicyType parameter type")
    void createEnforcesRotationPolicyTypeParameterType() throws NoSuchMethodException {
      java.lang.reflect.Method method =
          RotationPolicyFactory.class.getMethod("create", RotationPolicyType.class);
      assertEquals(
          RotationPolicyType.class,
          method.getParameterTypes()[0],
          "create method should only accept RotationPolicyType parameter");
    }

    @Test
    @DisplayName("create enforces RotationPolicy return type")
    void createEnforcesRotationPolicyReturnType() throws NoSuchMethodException {
      java.lang.reflect.Method method =
          RotationPolicyFactory.class.getMethod("create", RotationPolicyType.class);
      assertEquals(
          RotationPolicy.class,
          method.getReturnType(),
          "create method should return RotationPolicy type");
    }
  }
}
