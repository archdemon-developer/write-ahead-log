package io.writeahead.log.exceptions;

import io.writeahead.log.enums.exceptions.ErrorContext;
import io.writeahead.log.enums.exceptions.ErrorRecoveryAction;
import java.io.IOException;

public abstract sealed class WalException extends IOException
    permits TransientIOException,
        PermanentIOException,
        CorruptionException,
        RecoveryException,
        ConcurrencyException {

  private final ErrorContext errorContext;
  private final ErrorRecoveryAction suggestedAction;
  private final long timestamp;
  private final String operationDescription;

  public WalException(
      String message, ErrorContext context, ErrorRecoveryAction action, String operationDesc) {
    super(message);
    this.errorContext = context;
    this.suggestedAction = action;
    this.timestamp = System.currentTimeMillis();
    this.operationDescription = operationDesc;
  }

  public abstract boolean isTransient();

  public abstract boolean indicatesDataLoss();

  public ErrorContext context() {
    return errorContext;
  }

  public ErrorRecoveryAction suggestedAction() {
    return suggestedAction;
  }

  public long timestamp() {
    return timestamp;
  }

  public String operationDescription() {
    return operationDescription;
  }

  public abstract String errorTypeCode();

  @Override
  public String toString() {
    return String.format(
        "[%s] %s (context: %s, recovery: %s, op: %s)",
        errorTypeCode(), getMessage(), errorContext, suggestedAction, operationDescription);
  }
}
