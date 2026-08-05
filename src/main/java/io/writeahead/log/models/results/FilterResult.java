package io.writeahead.log.models.results;

import io.writeahead.log.models.LogEntry;

public record FilterResult(boolean matches, LogEntry entry, String filterName) {

  public FilterResult {
    if (entry == null) {
      throw new IllegalArgumentException("entry should not null");
    }

    if (filterName == null || filterName.isEmpty()) {
      throw new IllegalArgumentException("filterName should not be null or empty");
    }
  }

  public static FilterResult accepted(LogEntry entry, String filterName) {
    return new FilterResult(true, entry, filterName);
  }

  public static FilterResult rejected(LogEntry entry, String filterName) {
    return new FilterResult(false, entry, filterName);
  }

  public boolean isAccepted() {
    return matches;
  }

  public boolean isRejected() {
    return !matches;
  }
}
