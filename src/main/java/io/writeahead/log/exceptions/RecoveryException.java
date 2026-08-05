package io.writeahead.log.exceptions;

import io.writeahead.log.enums.exceptions.ErrorContext;
import io.writeahead.log.enums.exceptions.ErrorRecoveryAction;
import io.writeahead.log.enums.strategies.RecoveryType;

public class RecoveryException extends WalException {

  private final RecoveryType recoveryType;

  public RecoveryException(String message, RecoveryType type) {
    super(
        message, ErrorContext.RECOVERY_FAILURE, ErrorRecoveryAction.SKIP_AND_CONTINUE, "recovery");
    this.recoveryType = type;
  }

  @Override
  public boolean isTransient() {
    return false;
  }

  @Override
  public boolean indicatesDataLoss() {
    return true; // Skipping entries = data loss
  }

  @Override
  public String errorTypeCode() {
    return "RECOVERY_" + recoveryType.name();
  }

  public RecoveryType recoveryType() {
    return recoveryType;
  }
}
