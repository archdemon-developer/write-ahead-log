package io.writeahead.log.logging;

import io.writeahead.log.enums.levels.LogLevel;

public class LoggerFactory {
  private static LogLevel globalMinLevel = LogLevel.INFO;

  public static Logger getLogger(Class<?> clazz) {
    return new SimpleLogger(clazz.getSimpleName(), globalMinLevel);
  }

  public static Logger getLogger(String name) {
    if (name == null) {
      throw new IllegalArgumentException("name should not be null");
    }
    return new SimpleLogger(name, globalMinLevel);
  }

  public static void setLogLevel(LogLevel level) {
    if (level == null) {
      throw new IllegalArgumentException("Log level cannot be null");
    }
    globalMinLevel = level;
  }

  public static LogLevel getLogLevel() {
    return globalMinLevel;
  }
}
