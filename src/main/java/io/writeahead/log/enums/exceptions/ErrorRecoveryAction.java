package io.writeahead.log.enums.exceptions;

public enum ErrorRecoveryAction {
  RETRY_WITH_BACKOFF("Retry operation with exponential backoff"),
  FAIL_FAST_ALERT_OPERATOR("Fail immediately, alert operator"),
  QUARANTINE_AND_ALERT("Quarantine segment, alert operator, continue recovery"),
  SKIP_AND_CONTINUE("Skip entry/segment, continue recovery"),
  FAIL_AND_RETRY_OPERATION("Fail operation, allow caller to retry");

  private final String description;

  ErrorRecoveryAction(String desc) {
    this.description = desc;
  }

  public String description() {
    return description;
  }
}
