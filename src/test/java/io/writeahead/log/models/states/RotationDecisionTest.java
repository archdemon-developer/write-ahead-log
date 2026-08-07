package io.writeahead.log.models.states;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.strategies.RotationPolicyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RotationDecision Tests — 100% Validation Coverage")
class RotationDecisionTest {

  @Nested
  @DisplayName("Compact Constructor Validation — reason == null")
  class ConstructorValidation_ReasonNull {

    @Test
    @DisplayName("constructor rejects reason = null")
    void rejectsReasonNull() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new RotationDecision(true, null, 50, RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("reason cannot be null or empty"));
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — reason.isEmpty()")
  class ConstructorValidation_ReasonEmpty {

    @Test
    @DisplayName("constructor rejects reason = \"\"")
    void rejectsReasonEmpty() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new RotationDecision(true, "", 50, RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("reason cannot be null or empty"));
    }

    @Test
    @DisplayName("constructor accepts reason with valid content")
    void acceptsValidReason() {
      RotationDecision decision =
          new RotationDecision(true, "Segment full", 100, RotationPolicyType.SIZE_BASED);
      assertEquals("Segment full", decision.reason());
    }

    @Test
    @DisplayName("constructor accepts reason with single character")
    void acceptsSingleCharacterReason() {
      RotationDecision decision =
          new RotationDecision(true, "a", 50, RotationPolicyType.SIZE_BASED);
      assertEquals("a", decision.reason());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — utilizationPercent < 0")
  class ConstructorValidation_UtilizationPercentNegative {

    @Test
    @DisplayName("constructor rejects utilizationPercent = -1")
    void rejectsNegativeOne() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new RotationDecision(true, "Reason", -1, RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("utilizationPercent must be between 0 and 100"));
    }

    @Test
    @DisplayName("constructor rejects utilizationPercent = -100")
    void rejectsNegativeHundred() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new RotationDecision(true, "Reason", -100, RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("utilizationPercent must be between 0 and 100"));
    }

    @Test
    @DisplayName("constructor rejects utilizationPercent = Integer.MIN_VALUE")
    void rejectsMinValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new RotationDecision(
                      true, "Reason", Integer.MIN_VALUE, RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("utilizationPercent must be between 0 and 100"));
    }

    @Test
    @DisplayName("constructor accepts utilizationPercent = 0")
    void acceptsZero() {
      RotationDecision decision =
          new RotationDecision(true, "Reason", 0, RotationPolicyType.SIZE_BASED);
      assertEquals(0, decision.utilizationPercent());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — utilizationPercent > 100")
  class ConstructorValidation_UtilizationPercentTooHigh {

    @Test
    @DisplayName("constructor rejects utilizationPercent = 101")
    void rejectsOnehundredOne() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new RotationDecision(true, "Reason", 101, RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("utilizationPercent must be between 0 and 100"));
    }

    @Test
    @DisplayName("constructor rejects utilizationPercent = 200")
    void rejectsTwoHundred() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> new RotationDecision(true, "Reason", 200, RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("utilizationPercent must be between 0 and 100"));
    }

    @Test
    @DisplayName("constructor rejects utilizationPercent = Integer.MAX_VALUE")
    void rejectsMaxValue() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new RotationDecision(
                      true, "Reason", Integer.MAX_VALUE, RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("utilizationPercent must be between 0 and 100"));
    }

    @Test
    @DisplayName("constructor accepts utilizationPercent = 100")
    void acceptsOnehundred() {
      RotationDecision decision =
          new RotationDecision(true, "Reason", 100, RotationPolicyType.SIZE_BASED);
      assertEquals(100, decision.utilizationPercent());
    }
  }

  @Nested
  @DisplayName("Compact Constructor Validation — policyName == null")
  class ConstructorValidation_PolicyNameNull {

    @Test
    @DisplayName("constructor rejects policyName = null")
    void rejectsPolicyNameNull() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new RotationDecision(true, "Reason", 50, null));
      assertTrue(ex.getMessage().contains("policyName cannot be null"));
    }

    @Test
    @DisplayName("constructor accepts all RotationPolicyType enum values")
    void acceptsAllPolicyTypes() {
      for (RotationPolicyType policyType : RotationPolicyType.values()) {
        RotationDecision decision = new RotationDecision(true, "Reason", 50, policyType);
        assertEquals(policyType, decision.policyName());
      }
    }
  }

  @Nested
  @DisplayName("Factory Method Tests — rotateNow()")
  class FactoryMethod_RotateNow {

    @Test
    @DisplayName("rotateNow() sets shouldRotate to true")
    void setShouldRotateTrue() {
      RotationDecision decision =
          RotationDecision.rotateNow("Segment full", 100, RotationPolicyType.SIZE_BASED);
      assertTrue(decision.shouldRotate());
    }

    @Test
    @DisplayName("rotateNow() sets reason")
    void setsReason() {
      RotationDecision decision =
          RotationDecision.rotateNow("Segment full", 100, RotationPolicyType.SIZE_BASED);
      assertEquals("Segment full", decision.reason());
    }

    @Test
    @DisplayName("rotateNow() sets utilizationPercent")
    void setsUtilizationPercent() {
      RotationDecision decision =
          RotationDecision.rotateNow("Reason", 75, RotationPolicyType.SIZE_BASED);
      assertEquals(75, decision.utilizationPercent());
    }

    @Test
    @DisplayName("rotateNow() validates all parameters")
    void validatesAllParameters() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> RotationDecision.rotateNow("", 50, RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("reason"));
    }
  }

  @Nested
  @DisplayName("Factory Method Tests — keepOpen()")
  class FactoryMethod_KeepOpen {

    @Test
    @DisplayName("keepOpen() sets shouldRotate to false")
    void setShouldRotateFalse() {
      RotationDecision decision =
          RotationDecision.keepOpen("Still room", 50, RotationPolicyType.SIZE_BASED);
      assertFalse(decision.shouldRotate());
    }

    @Test
    @DisplayName("keepOpen() sets reason")
    void setsReason() {
      RotationDecision decision =
          RotationDecision.keepOpen("Still room", 50, RotationPolicyType.SIZE_BASED);
      assertEquals("Still room", decision.reason());
    }

    @Test
    @DisplayName("keepOpen() sets utilizationPercent")
    void setsUtilizationPercent() {
      RotationDecision decision =
          RotationDecision.keepOpen("Reason", 25, RotationPolicyType.SIZE_BASED);
      assertEquals(25, decision.utilizationPercent());
    }

    @Test
    @DisplayName("keepOpen() sets policyName")
    void setsPolicyName() {
      RotationDecision decision =
          RotationDecision.keepOpen("Reason", 50, RotationPolicyType.SIZE_BASED);
      assertEquals(RotationPolicyType.SIZE_BASED, decision.policyName());
    }

    @Test
    @DisplayName("keepOpen() validates all parameters")
    void validatesAllParameters() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> RotationDecision.keepOpen("Reason", 150, RotationPolicyType.SIZE_BASED));
      assertTrue(ex.getMessage().contains("utilizationPercent"));
    }
  }

  @Nested
  @DisplayName("Helper Tests — needsRotation()")
  class NeedsRotation {

    @Test
    @DisplayName("needsRotation() returns true for shouldRotate=true")
    void returnsTrueWhenShouldRotate() {
      RotationDecision decision =
          RotationDecision.rotateNow("Reason", 100, RotationPolicyType.SIZE_BASED);
      assertTrue(decision.needsRotation());
    }

    @Test
    @DisplayName("needsRotation() returns false for shouldRotate=false")
    void returnsFalseWhenShouldNotRotate() {
      RotationDecision decision =
          RotationDecision.keepOpen("Reason", 50, RotationPolicyType.SIZE_BASED);
      assertFalse(decision.needsRotation());
    }

    @Test
    @DisplayName("needsRotation() reflects shouldRotate flag")
    void reflectsShouldRotate() {
      RotationDecision rotateDecision =
          RotationDecision.rotateNow("Reason", 50, RotationPolicyType.SIZE_BASED);
      RotationDecision keepDecision =
          RotationDecision.keepOpen("Reason", 50, RotationPolicyType.SIZE_BASED);

      assertEquals(rotateDecision.shouldRotate(), rotateDecision.needsRotation());
      assertEquals(keepDecision.shouldRotate(), keepDecision.needsRotation());
    }
  }

  @Nested
  @DisplayName("Helper Tests — canStayOpen()")
  class CanStayOpen {

    @Test
    @DisplayName("canStayOpen() returns false for shouldRotate=true")
    void returnsFalseWhenShouldRotate() {
      RotationDecision decision =
          RotationDecision.rotateNow("Reason", 100, RotationPolicyType.SIZE_BASED);
      assertFalse(decision.canStayOpen());
    }

    @Test
    @DisplayName("canStayOpen() returns true for shouldRotate=false")
    void returnsTrueWhenShouldNotRotate() {
      RotationDecision decision =
          RotationDecision.keepOpen("Reason", 50, RotationPolicyType.SIZE_BASED);
      assertTrue(decision.canStayOpen());
    }

    @Test
    @DisplayName("canStayOpen() is inverse of needsRotation()")
    void isInverseOfNeedsRotation() {
      RotationDecision rotateDecision =
          RotationDecision.rotateNow("Reason", 50, RotationPolicyType.SIZE_BASED);
      RotationDecision keepDecision =
          RotationDecision.keepOpen("Reason", 50, RotationPolicyType.SIZE_BASED);

      assertNotEquals(rotateDecision.needsRotation(), rotateDecision.canStayOpen());
      assertNotEquals(keepDecision.needsRotation(), keepDecision.canStayOpen());
    }

    @Test
    @DisplayName("canStayOpen() negates shouldRotate")
    void negatesShouldRotate() {
      RotationDecision decision1 =
          RotationDecision.rotateNow("Reason", 50, RotationPolicyType.SIZE_BASED);
      RotationDecision decision2 =
          RotationDecision.keepOpen("Reason", 50, RotationPolicyType.SIZE_BASED);

      assertEquals(!decision1.shouldRotate(), decision1.canStayOpen());
      assertEquals(!decision2.shouldRotate(), decision2.canStayOpen());
    }
  }

  @Nested
  @DisplayName("Edge Cases & Boundary Values")
  class EdgeCases {

    @Test
    @DisplayName("handles utilizationPercent = 0 (empty segment)")
    void handlesZeroUtilization() {
      RotationDecision decision =
          new RotationDecision(false, "Empty", 0, RotationPolicyType.SIZE_BASED);
      assertEquals(0, decision.utilizationPercent());
    }

    @Test
    @DisplayName("handles utilizationPercent = 100 (full segment)")
    void handlesFullUtilization() {
      RotationDecision decision =
          new RotationDecision(true, "Full", 100, RotationPolicyType.SIZE_BASED);
      assertEquals(100, decision.utilizationPercent());
    }

    @Test
    @DisplayName("handles all percentage boundaries: 1-99")
    void handlesAllPercentages() {
      for (int percent = 0; percent <= 100; percent++) {
        RotationDecision decision =
            new RotationDecision(
                percent >= 80, "Reason " + percent, percent, RotationPolicyType.SIZE_BASED);
        assertEquals(percent, decision.utilizationPercent());
      }
    }

    @Test
    @DisplayName("handles very long reason string")
    void handlesLongReason() {
      String longReason = "a".repeat(1000);
      RotationDecision decision =
          new RotationDecision(true, longReason, 50, RotationPolicyType.SIZE_BASED);
      assertEquals(longReason, decision.reason());
    }

    @Test
    @DisplayName("handles reason with special characters")
    void handlesSpecialCharactersInReason() {
      RotationDecision decision =
          new RotationDecision(
              true, "Reason: file/size[1000] > max (100%) !", 75, RotationPolicyType.SIZE_BASED);
      assertTrue(decision.reason().contains("file/size"));
    }

    @Test
    @DisplayName("handles all RotationPolicyType enum values")
    void handlesAllPolicyTypes() {
      for (RotationPolicyType policyType : RotationPolicyType.values()) {
        RotationDecision decision = new RotationDecision(true, "Reason", 50, policyType);
        assertEquals(policyType, decision.policyName());
      }
    }

    @Test
    @DisplayName("handles both shouldRotate states independently")
    void handlesBothRotateStates() {
      RotationDecision shouldRotate =
          new RotationDecision(true, "Reason", 100, RotationPolicyType.SIZE_BASED);
      RotationDecision shouldNotRotate =
          new RotationDecision(false, "Reason", 0, RotationPolicyType.SIZE_BASED);

      assertTrue(shouldRotate.needsRotation());
      assertFalse(shouldNotRotate.needsRotation());
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class Integration {

    @Test
    @DisplayName("rotateNow() decision has consistent helpers")
    void rotateNowDecisionConsistency() {
      RotationDecision decision =
          RotationDecision.rotateNow("Full", 100, RotationPolicyType.SIZE_BASED);

      assertTrue(decision.needsRotation());
      assertFalse(decision.canStayOpen());
      assertTrue(decision.shouldRotate());
      assertEquals(100, decision.utilizationPercent());
    }

    @Test
    @DisplayName("keepOpen() decision has consistent helpers")
    void keepOpenDecisionConsistency() {
      RotationDecision decision =
          RotationDecision.keepOpen("Still room", 25, RotationPolicyType.SIZE_BASED);

      assertFalse(decision.needsRotation());
      assertTrue(decision.canStayOpen());
      assertFalse(decision.shouldRotate());
      assertEquals(25, decision.utilizationPercent());
    }

    @Test
    @DisplayName("helpers are inverse of each other")
    void helpersAreInverse() {
      RotationDecision decision1 =
          RotationDecision.rotateNow("Reason", 50, RotationPolicyType.SIZE_BASED);
      RotationDecision decision2 =
          RotationDecision.keepOpen("Reason", 50, RotationPolicyType.SIZE_BASED);

      assertEquals(decision1.needsRotation(), !decision1.canStayOpen());
      assertEquals(decision2.needsRotation(), !decision2.canStayOpen());
    }
  }
}
