package io.writeahead.log.enums.exceptions;

public enum ConcurrencyErrorType {
  LOCK_TIMEOUT("Lock acquisition timeout"),
  INTERRUPTED("Thread interrupted"),
  DEADLOCK("Potential deadlock detected"),
  UNKNOWN("Unknown concurrency error");

  private final String description;

  ConcurrencyErrorType(String desc) {
    this.description = desc;
  }

  public String description() {
    return description;
  }
}
