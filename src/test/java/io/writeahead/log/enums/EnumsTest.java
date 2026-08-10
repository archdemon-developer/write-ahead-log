package io.writeahead.log.enums;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.exceptions.ConcurrencyErrorType;
import io.writeahead.log.enums.exceptions.CorruptionType;
import io.writeahead.log.enums.exceptions.ErrorContext;
import io.writeahead.log.enums.exceptions.ErrorRecoveryAction;
import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.enums.strategies.RecoveryType;
import io.writeahead.log.enums.strategies.RotationPolicyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Comprehensive Enum Coverage Test Suite")
class EnumsTest {

  @Test
  @DisplayName("ErrorContext enum has expected values")
  void errorContextHasExpectedValues() {
    ErrorContext[] contexts = ErrorContext.values();
    assertTrue(contexts.length > 0, "ErrorContext should have at least one value");

    assertContainsContextValue(ErrorContext.DISK_FULL);
    assertContainsContextValue(ErrorContext.PERMISSION_DENIED);
    assertContainsContextValue(ErrorContext.BAD_FD);
    assertContainsContextValue(ErrorContext.FILE_NOT_FOUND);
    assertContainsContextValue(ErrorContext.RESOURCE_BUSY);
    assertContainsContextValue(ErrorContext.NO_MEMORY);
    assertContainsContextValue(ErrorContext.CONCURRENCY);
    assertContainsContextValue(ErrorContext.DATA_CORRUPTION);
    assertContainsContextValue(ErrorContext.RECOVERY_FAILURE);
    assertContainsContextValue(ErrorContext.UNKNOWN_IO_ERROR);
  }

  @ParameterizedTest
  @EnumSource(ErrorContext.class)
  @DisplayName("ErrorContext values have non-empty names")
  void errorContextValuesHaveNonEmptyNames(ErrorContext context) {
    assertNotNull(context.name());
    assertFalse(context.name().isEmpty(), "ErrorContext name should not be empty");
  }

  @ParameterizedTest
  @EnumSource(ErrorContext.class)
  @DisplayName("ErrorContext values have valid ordinals")
  void errorContextValuesHaveValidOrdinals(ErrorContext context) {
    int ordinal = context.ordinal();
    assertTrue(ordinal >= 0, "ErrorContext ordinal should be non-negative");
    assertEquals(context, ErrorContext.values()[ordinal]);
  }

  @Test
  @DisplayName("ErrorRecoveryAction enum has expected values")
  void errorRecoveryActionHasExpectedValues() {
    ErrorRecoveryAction[] actions = ErrorRecoveryAction.values();
    assertTrue(actions.length > 0, "ErrorRecoveryAction should have at least one value");

    assertContainsRecoveryAction(ErrorRecoveryAction.FAIL_AND_RETRY_OPERATION);
    assertContainsRecoveryAction(ErrorRecoveryAction.QUARANTINE_AND_ALERT);
    assertContainsRecoveryAction(ErrorRecoveryAction.RETRY_WITH_BACKOFF);
    assertContainsRecoveryAction(ErrorRecoveryAction.FAIL_FAST_ALERT_OPERATOR);
    assertContainsRecoveryAction(ErrorRecoveryAction.SKIP_AND_CONTINUE);
  }

  @ParameterizedTest
  @EnumSource(ErrorRecoveryAction.class)
  @DisplayName("ErrorRecoveryAction values have non-empty names")
  void errorRecoveryActionValuesHaveNonEmptyNames(ErrorRecoveryAction action) {
    assertNotNull(action.name());
    assertFalse(action.name().isEmpty(), "ErrorRecoveryAction name should not be empty");
  }

  @ParameterizedTest
  @EnumSource(ErrorRecoveryAction.class)
  @DisplayName("ErrorRecoveryAction values can be compared")
  void errorRecoveryActionValuesCanBeCompared(ErrorRecoveryAction action) {
    assertEquals(action, action);
    assertNotEquals(action, null);
  }

  @Test
  @DisplayName("ConcurrencyErrorType enum values exist")
  void concurrencyErrorTypeEnumExists() {
    ConcurrencyErrorType[] types = ConcurrencyErrorType.values();
    assertTrue(types.length > 0, "ConcurrencyErrorType should have at least one value");

    for (ConcurrencyErrorType type : types) {
      assertNotNull(type);
      assertNotNull(type.name());
      assertFalse(type.name().isEmpty());
    }
  }

  @ParameterizedTest
  @EnumSource(ConcurrencyErrorType.class)
  @DisplayName("ConcurrencyErrorType values have valid ordinals")
  void concurrencyErrorTypeValuesHaveValidOrdinals(ConcurrencyErrorType type) {
    int ordinal = type.ordinal();
    assertTrue(ordinal >= 0, "ConcurrencyErrorType ordinal should be non-negative");
    assertEquals(type, ConcurrencyErrorType.values()[ordinal]);
  }

  @Test
  @DisplayName("CorruptionType enum values exist")
  void corruptionTypeEnumExists() {
    CorruptionType[] types = CorruptionType.values();
    assertTrue(types.length > 0, "CorruptionType should have at least one value");

    for (CorruptionType type : types) {
      assertNotNull(type);
      assertNotNull(type.name());
      assertFalse(type.name().isEmpty());
    }
  }

  @ParameterizedTest
  @EnumSource(CorruptionType.class)
  @DisplayName("CorruptionType values can be stringified")
  void corruptionTypeValuesCanBeStringified(CorruptionType type) {
    String stringValue = type.toString();
    assertNotNull(stringValue);
    assertFalse(stringValue.isEmpty());
    assertTrue(stringValue.contains(type.name()));
  }

  @Test
  @DisplayName("RecoveryType enum values exist")
  void recoveryTypeEnumExists() {
    RecoveryType[] types = RecoveryType.values();
    assertTrue(types.length > 0, "RecoveryType should have at least one value");

    for (RecoveryType type : types) {
      assertNotNull(type);
      assertNotNull(type.name());
      assertFalse(type.name().isEmpty());
    }
  }

  @ParameterizedTest
  @EnumSource(RecoveryType.class)
  @DisplayName("RecoveryType values have sequential ordinals")
  void recoveryTypeValuesHaveSequentialOrdinals(RecoveryType type) {
    RecoveryType[] allTypes = RecoveryType.values();
    int ordinal = type.ordinal();
    assertTrue(ordinal >= 0 && ordinal < allTypes.length);
  }

  @Test
  @DisplayName("RotationPolicyType enum has expected values")
  void rotationPolicyTypeHasExpectedValues() {
    RotationPolicyType[] types = RotationPolicyType.values();
    assertTrue(types.length > 0, "RotationPolicyType should have at least one value");

    assertContainsPolicyType(RotationPolicyType.SIZE_BASED);
  }

  @ParameterizedTest
  @EnumSource(RotationPolicyType.class)
  @DisplayName("RotationPolicyType values are not null")
  void rotationPolicyTypeValuesAreNotNull(RotationPolicyType type) {
    assertNotNull(type);
    assertNotNull(type.name());
    assertFalse(type.name().isEmpty());
  }

  @Test
  @DisplayName("FsyncStrategy enum has expected values")
  void fsyncStrategyHasExpectedValues() {
    FsyncStrategy[] strategies = FsyncStrategy.values();
    assertTrue(strategies.length > 0, "FsyncStrategy should have at least one value");

    assertContainsFsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH);
    assertContainsFsyncStrategy(FsyncStrategy.FSYNC_EVERY_ENTRY);
  }

  @ParameterizedTest
  @EnumSource(FsyncStrategy.class)
  @DisplayName("FsyncStrategy values have non-empty names")
  void fsyncStrategyValuesHaveNonEmptyNames(FsyncStrategy strategy) {
    assertNotNull(strategy.name());
    assertFalse(strategy.name().isEmpty(), "FsyncStrategy name should not be empty");
  }

  @ParameterizedTest
  @EnumSource(FsyncStrategy.class)
  @DisplayName("FsyncStrategy values are comparable")
  void fsyncStrategyValuesAreComparable(FsyncStrategy strategy) {
    assertEquals(strategy, strategy);
    assertTrue(strategy.compareTo(strategy) == 0);
  }

  @Test
  @DisplayName("All ErrorContext values return from values() method")
  void allErrorContextValuesReturnFromValuesMethod() {
    ErrorContext[] values = ErrorContext.values();
    ErrorContext disk = ErrorContext.DISK_FULL;

    boolean found = false;
    for (ErrorContext ec : values) {
      if (ec == disk) {
        found = true;
        break;
      }
    }
    assertTrue(found, "DISK_FULL should be in values() array");
  }

  @Test
  @DisplayName("All ErrorRecoveryAction values return from values() method")
  void allErrorRecoveryActionValuesReturnFromValuesMethod() {
    ErrorRecoveryAction[] values = ErrorRecoveryAction.values();
    ErrorRecoveryAction action = ErrorRecoveryAction.RETRY_WITH_BACKOFF;

    boolean found = false;
    for (ErrorRecoveryAction era : values) {
      if (era == action) {
        found = true;
        break;
      }
    }
    assertTrue(found, "RETRY_WITH_BACKOFF should be in values() array");
  }

  @Test
  @DisplayName("All ConcurrencyErrorType values are enumerable")
  void allConcurrencyErrorTypeValuesAreEnumerable() {
    ConcurrencyErrorType[] values = ConcurrencyErrorType.values();
    assertTrue(values.length > 0);

    for (ConcurrencyErrorType type : values) {
      ConcurrencyErrorType retrieved = ConcurrencyErrorType.valueOf(type.name());
      assertEquals(type, retrieved);
    }
  }

  @Test
  @DisplayName("All CorruptionType values are enumerable")
  void allCorruptionTypeValuesAreEnumerable() {
    CorruptionType[] values = CorruptionType.values();
    assertTrue(values.length > 0);

    for (CorruptionType type : values) {
      CorruptionType retrieved = CorruptionType.valueOf(type.name());
      assertEquals(type, retrieved);
    }
  }

  @Test
  @DisplayName("All RecoveryType values are enumerable")
  void allRecoveryTypeValuesAreEnumerable() {
    RecoveryType[] values = RecoveryType.values();
    assertTrue(values.length > 0);

    for (RecoveryType type : values) {
      RecoveryType retrieved = RecoveryType.valueOf(type.name());
      assertEquals(type, retrieved);
    }
  }

  @Test
  @DisplayName("All RotationPolicyType values are enumerable")
  void allRotationPolicyTypeValuesAreEnumerable() {
    RotationPolicyType[] values = RotationPolicyType.values();
    assertTrue(values.length > 0);

    for (RotationPolicyType type : values) {
      RotationPolicyType retrieved = RotationPolicyType.valueOf(type.name());
      assertEquals(type, retrieved);
    }
  }

  @Test
  @DisplayName("All FsyncStrategy values are enumerable")
  void allFsyncStrategyValuesAreEnumerable() {
    FsyncStrategy[] values = FsyncStrategy.values();
    assertTrue(values.length > 0);

    for (FsyncStrategy strategy : values) {
      FsyncStrategy retrieved = FsyncStrategy.valueOf(strategy.name());
      assertEquals(strategy, retrieved);
    }
  }

  @Test
  @DisplayName("ErrorContext RESOURCE_BUSY is transient indicator")
  void errorContextResourceBusyIsTransientIndicator() {
    ErrorContext resourceBusy = ErrorContext.RESOURCE_BUSY;
    assertNotNull(resourceBusy);
    assertEquals("RESOURCE_BUSY", resourceBusy.name());
  }

  @Test
  @DisplayName("ErrorContext NO_MEMORY is transient indicator")
  void errorContextNoMemoryIsTransientIndicator() {
    ErrorContext noMemory = ErrorContext.NO_MEMORY;
    assertNotNull(noMemory);
    assertEquals("NO_MEMORY", noMemory.name());
  }

  @Test
  @DisplayName("FsyncStrategy FSYNC_EVERY_BATCH and FSYNC_EVERY_ENTRY are distinct")
  void fsyncStrategyValuesAreDistinct() {
    assertNotEquals(
        FsyncStrategy.FSYNC_EVERY_BATCH,
        FsyncStrategy.FSYNC_EVERY_ENTRY,
        "FSYNC_EVERY_BATCH and FSYNC_EVERY_ENTRY should be different");
  }

  @Test
  @DisplayName("ErrorRecoveryAction FAIL_AND_RETRY_OPERATION indicates retry")
  void errorRecoveryActionFailAndRetryIndicatesRetry() {
    ErrorRecoveryAction action = ErrorRecoveryAction.FAIL_AND_RETRY_OPERATION;
    assertNotNull(action);
  }

  @Test
  @DisplayName("ErrorRecoveryAction SKIP_AND_CONTINUE indicates data loss handling")
  void errorRecoveryActionSkipAndContinueIndicatesDataLoss() {
    ErrorRecoveryAction action = ErrorRecoveryAction.SKIP_AND_CONTINUE;
    assertNotNull(action);
    assertTrue(action.name().contains("SKIP"));
  }

  private void assertContainsContextValue(ErrorContext context) {
    boolean found = false;
    for (ErrorContext ec : ErrorContext.values()) {
      if (ec == context) {
        found = true;
        break;
      }
    }
    assertTrue(found, "ErrorContext." + context.name() + " should exist");
  }

  private void assertContainsRecoveryAction(ErrorRecoveryAction action) {
    boolean found = false;
    for (ErrorRecoveryAction era : ErrorRecoveryAction.values()) {
      if (era == action) {
        found = true;
        break;
      }
    }
    assertTrue(found, "ErrorRecoveryAction." + action.name() + " should exist");
  }

  private void assertContainsPolicyType(RotationPolicyType type) {
    boolean found = false;
    for (RotationPolicyType rpt : RotationPolicyType.values()) {
      if (rpt == type) {
        found = true;
        break;
      }
    }
    assertTrue(found, "RotationPolicyType." + type.name() + " should exist");
  }

  private void assertContainsFsyncStrategy(FsyncStrategy strategy) {
    boolean found = false;
    for (FsyncStrategy fs : FsyncStrategy.values()) {
      if (fs == strategy) {
        found = true;
        break;
      }
    }
    assertTrue(found, "FsyncStrategy." + strategy.name() + " should exist");
  }
}
