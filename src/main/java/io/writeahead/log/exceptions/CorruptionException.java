package io.writeahead.log.exceptions;

import io.writeahead.log.enums.exceptions.CorruptionType;
import io.writeahead.log.enums.exceptions.ErrorContext;
import io.writeahead.log.enums.exceptions.ErrorRecoveryAction;

public final class CorruptionException extends WalException {

  private final String segmentName;
  private final long byteOffset;
  private final CorruptionType corruptionType;
  private final long computedValue;
  private final long expectedValue;

  public CorruptionException(
      String message,
      String segmentName,
      long byteOffset,
      CorruptionType type,
      long computed,
      long expected) {
    super(
        message,
        ErrorContext.DATA_CORRUPTION,
        ErrorRecoveryAction.QUARANTINE_AND_ALERT,
        "reading " + segmentName + " at offset " + byteOffset);

    this.segmentName = segmentName;
    this.byteOffset = byteOffset;
    this.corruptionType = type;
    this.computedValue = computed;
    this.expectedValue = expected;
  }

  @Override
  public boolean isTransient() {
    return false;
  }

  @Override
  public boolean indicatesDataLoss() {
    return true;
  }

  @Override
  public String errorTypeCode() {
    return "CORRUPTION_" + corruptionType.name();
  }

  public String segmentName() {
    return segmentName;
  }

  public long byteOffset() {
    return byteOffset;
  }

  public CorruptionType corruptionType() {
    return corruptionType;
  }

  public long computedValue() {
    return computedValue;
  }

  public long expectedValue() {
    return expectedValue;
  }
}
