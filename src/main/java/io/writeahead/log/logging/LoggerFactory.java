package io.writeahead.log.logging;

import io.writeahead.log.enums.LogLevel;

public class LoggerFactory {
  private static LogLevel globalMinLevel = LogLevel.INFO;

  public static Logger getLogger(Class<?> clazz) {
    return new SimpleLogger(clazz.getSimpleName(), globalMinLevel);
  }

  public static Logger getLogger(String name) {
    return new SimpleLogger(name, globalMinLevel);
  }

  public static void setLogLevel(LogLevel level) {
    globalMinLevel = level;
  }

  public static LogLevel getLogLevel() {
    return globalMinLevel;
  }
}
