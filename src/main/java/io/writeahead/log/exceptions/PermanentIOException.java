package io.writeahead.log.exceptions;

import io.writeahead.log.enums.exceptions.ErrorContext;
import io.writeahead.log.enums.exceptions.ErrorRecoveryAction;

public class PermanentIOException extends WalException {

  public PermanentIOException(String message, ErrorContext context, String operationDesc) {
    super(message, context, ErrorRecoveryAction.FAIL_FAST_ALERT_OPERATOR, operationDesc);
  }

  @Override
  public boolean isTransient() {
    return false;
  }

  @Override
  public boolean indicatesDataLoss() {
    return false; // Not yet lost, but might be if we retry
  }

  @Override
  public String errorTypeCode() {
    return "PERMANENT_IO";
  }
}
