package io.writeahead.log.logging;

import io.writeahead.log.enums.LogLevel;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SimpleLogger implements Logger {

    private final String name;
    private final LogLevel minLevel;
    private static final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public SimpleLogger(String name, LogLevel minLevel) {
        this.name = name;
        this.minLevel = minLevel;
    }

    @Override
    public void debug(String message) {
        if (minLevel.isEnabledFor(LogLevel.DEBUG)) {
            print(LogLevel.DEBUG, message, null);
        }
    }

    @Override
    public void debug(String message, Object... args) {
        if (minLevel.isEnabledFor(LogLevel.DEBUG)) {
            print(LogLevel.DEBUG, format(message, args), null);
        }
    }

    @Override
    public void info(String message) {
        if (minLevel.isEnabledFor(LogLevel.INFO)) {
            print(LogLevel.INFO, message, null);
        }
    }

    @Override
    public void info(String message, Object... args) {
        if (minLevel.isEnabledFor(LogLevel.INFO)) {
            print(LogLevel.INFO, format(message, args), null);
        }
    }

    @Override
    public void warn(String message) {
        if (minLevel.isEnabledFor(LogLevel.WARN)) {
            print(LogLevel.WARN, message, null);
        }
    }

    @Override
    public void warn(String message, Object... args) {
        if (minLevel.isEnabledFor(LogLevel.WARN)) {
            print(LogLevel.WARN, format(message, args), null);
        }
    }

    @Override
    public void error(String message) {
        if (minLevel.isEnabledFor(LogLevel.ERROR)) {
            print(LogLevel.ERROR, message, null);
        }
    }

    @Override
    public void error(String message, Throwable cause) {
        if (minLevel.isEnabledFor(LogLevel.ERROR)) {
            print(LogLevel.ERROR, message, cause);
        }
    }

    @Override
    public void error(String message, Throwable cause, Object... args) {
        if (minLevel.isEnabledFor(LogLevel.ERROR)) {
            print(LogLevel.ERROR, format(message, args), cause);
        }
    }

    private void print(LogLevel level, String message, Throwable cause) {
        String timestamp = LocalDateTime.now().format(dateFormatter);
        String threadName = Thread.currentThread().getName();
        String formatted = String.format("[%s] [%s] [%s] %s - %s",
                timestamp, threadName, level, name, message);

        System.out.println(formatted);

        if (cause != null) {
            cause.printStackTrace(System.out);
        }
    }

    private String format(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        return String.format(message.replace("{}", "%s"), args);
    }
}
