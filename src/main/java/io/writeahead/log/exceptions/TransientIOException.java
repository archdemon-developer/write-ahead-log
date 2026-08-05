package io.writeahead.log.exceptions;

import io.writeahead.log.enums.exceptions.ErrorContext;
import io.writeahead.log.enums.exceptions.ErrorRecoveryAction;

public class TransientIOException extends WalException {

  public TransientIOException(String message, ErrorContext context, String operationDesc) {
    super(message, context, ErrorRecoveryAction.RETRY_WITH_BACKOFF, operationDesc);
  }

  @Override
  public boolean isTransient() {
    return true;
  }

  @Override
  public boolean indicatesDataLoss() {
    return false;
  }

  @Override
  public String errorTypeCode() {
    return "TRANSIENT_IO";
  }
}
