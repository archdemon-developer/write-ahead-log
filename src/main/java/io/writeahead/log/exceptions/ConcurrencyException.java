package io.writeahead.log.exceptions;

import io.writeahead.log.enums.ConcurrencyErrorType;
import io.writeahead.log.enums.ErrorContext;
import io.writeahead.log.enums.ErrorRecoveryAction;

public class ConcurrencyException extends WalException {

  private final ConcurrencyErrorType concurrencyErrorType;

  public ConcurrencyException(String message, ConcurrencyErrorType type) {
    super(
        message,
        ErrorContext.CONCURRENCY,
        ErrorRecoveryAction.FAIL_AND_RETRY_OPERATION,
        "concurrent operation");
    this.concurrencyErrorType = type;
  }

  @Override
  public boolean isTransient() {
    return true; // Transient: retry the operation
  }

  @Override
  public boolean indicatesDataLoss() {
    return false;
  }

  @Override
  public String errorTypeCode() {
    return "CONCURRENCY_" + concurrencyErrorType.name();
  }

  public ConcurrencyErrorType errorType() {
    return concurrencyErrorType;
  }
}
