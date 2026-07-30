package io.writeahead.log.enums;

public enum LogLevel {
  DEBUG(0),
  INFO(1),
  WARN(2),
  ERROR(3);

  private final int level;

  LogLevel(int level) {
    this.level = level;
  }

  public int getLevel() {
    return level;
  }

  public boolean isEnabledFor(LogLevel other) {
    return this.level <= other.level;
  }
}
