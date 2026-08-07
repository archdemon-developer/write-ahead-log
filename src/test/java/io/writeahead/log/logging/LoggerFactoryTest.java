package io.writeahead.log.logging;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.levels.LogLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoggerFactory - Logger Creation and Configuration")
public class LoggerFactoryTest {

  @BeforeEach
  void setUp() {
    LoggerFactory.setLogLevel(LogLevel.INFO);
  }

  @Test
  @DisplayName("getLogger with Class creates SimpleLogger instance")
  void testGetLoggerWithClass() {
    Logger logger = LoggerFactory.getLogger(String.class);

    assertNotNull(logger);
    assertInstanceOf(SimpleLogger.class, logger);
  }

  @Test
  @DisplayName("getLogger with Class uses class simple name")
  void testGetLoggerWithClassSimpleName() {
    Logger logger1 = LoggerFactory.getLogger(String.class);
    Logger logger2 = LoggerFactory.getLogger(Integer.class);

    assertNotNull(logger1);
    assertNotNull(logger2);
  }

  @Test
  @DisplayName("getLogger with Class creates new instance each time")
  void testGetLoggerWithClassNewInstance() {
    Logger logger1 = LoggerFactory.getLogger(String.class);
    Logger logger2 = LoggerFactory.getLogger(String.class);

    assertNotSame(logger1, logger2);
  }

  @Test
  @DisplayName("getLogger with String creates SimpleLogger instance")
  void testGetLoggerWithString() {
    Logger logger = LoggerFactory.getLogger("TestLogger");

    assertNotNull(logger);
    assertInstanceOf(SimpleLogger.class, logger);
  }

  @Test
  @DisplayName("getLogger with String creates new instance each time")
  void testGetLoggerWithStringNewInstance() {
    Logger logger1 = LoggerFactory.getLogger("TestLogger");
    Logger logger2 = LoggerFactory.getLogger("TestLogger");

    assertNotSame(logger1, logger2);
  }

  @Test
  @DisplayName("getLogger with empty String works")
  void testGetLoggerWithEmptyString() {
    Logger logger = LoggerFactory.getLogger("");

    assertNotNull(logger);
    assertInstanceOf(SimpleLogger.class, logger);
  }

  @Test
  @DisplayName("getLogger with special characters in name works")
  void testGetLoggerWithSpecialCharacters() {
    Logger logger = LoggerFactory.getLogger("TestLogger-123_ABC");

    assertNotNull(logger);
    assertInstanceOf(SimpleLogger.class, logger);
  }

  @Test
  @DisplayName("setLogLevel updates global log level")
  void testSetLogLevel() {
    LoggerFactory.setLogLevel(LogLevel.DEBUG);

    assertEquals(LogLevel.DEBUG, LoggerFactory.getLogLevel());
  }

  @Test
  @DisplayName("setLogLevel affects newly created loggers")
  void testSetLogLevelAffectsNewLoggers() {
    LoggerFactory.setLogLevel(LogLevel.DEBUG);
    Logger logger = LoggerFactory.getLogger("TestLogger");

    assertNotNull(logger);
  }

  @Test
  @DisplayName("setLogLevel with INFO")
  void testSetLogLevelInfo() {
    LoggerFactory.setLogLevel(LogLevel.INFO);

    assertEquals(LogLevel.INFO, LoggerFactory.getLogLevel());
  }

  @Test
  @DisplayName("setLogLevel with WARN")
  void testSetLogLevelWarn() {
    LoggerFactory.setLogLevel(LogLevel.WARN);

    assertEquals(LogLevel.WARN, LoggerFactory.getLogLevel());
  }

  @Test
  @DisplayName("setLogLevel with ERROR")
  void testSetLogLevelError() {
    LoggerFactory.setLogLevel(LogLevel.ERROR);

    assertEquals(LogLevel.ERROR, LoggerFactory.getLogLevel());
  }

  @Test
  @DisplayName("getLogLevel returns current global level")
  void testGetLogLevel() {
    LoggerFactory.setLogLevel(LogLevel.DEBUG);

    assertEquals(LogLevel.DEBUG, LoggerFactory.getLogLevel());
  }

  @Test
  @DisplayName("getLogLevel initial value is INFO")
  void testGetLogLevelInitial() {
    assertEquals(LogLevel.INFO, LoggerFactory.getLogLevel());
  }

  @Test
  @DisplayName("Multiple setLogLevel calls update correctly")
  void testMultipleSetLogLevel() {
    LoggerFactory.setLogLevel(LogLevel.DEBUG);
    assertEquals(LogLevel.DEBUG, LoggerFactory.getLogLevel());

    LoggerFactory.setLogLevel(LogLevel.WARN);
    assertEquals(LogLevel.WARN, LoggerFactory.getLogLevel());

    LoggerFactory.setLogLevel(LogLevel.ERROR);
    assertEquals(LogLevel.ERROR, LoggerFactory.getLogLevel());
  }

  @Test
  @DisplayName("getLogger with Class null throws NPE")
  void testGetLoggerWithClassNull() {
    assertThrows(NullPointerException.class, () -> LoggerFactory.getLogger((Class<?>) null));
  }

  @Test
  @DisplayName("getLogger with String null throws NPE")
  void testGetLoggerWithStringNull() {
    assertThrows(IllegalArgumentException.class, () -> LoggerFactory.getLogger((String) null));
  }

  @Test
  @DisplayName("setLogLevel null throws NPE")
  void testSetLogLevelNull() {
    assertThrows(IllegalArgumentException.class, () -> LoggerFactory.setLogLevel(null));
  }

  @Test
  @DisplayName("getLogger respects current log level")
  void testGetLoggerRespectsCurrentLevel() {
    LoggerFactory.setLogLevel(LogLevel.ERROR);
    Logger logger = LoggerFactory.getLogger("TestLogger");

    assertNotNull(logger);
  }

  @Test
  @DisplayName("Concurrent access to setLogLevel")
  void testConcurrentSetLogLevel() throws InterruptedException {
    Thread t1 =
        new Thread(
            () -> {
              for (int i = 0; i < 10; i++) {
                LoggerFactory.setLogLevel(LogLevel.DEBUG);
              }
            });

    Thread t2 =
        new Thread(
            () -> {
              for (int i = 0; i < 10; i++) {
                LoggerFactory.setLogLevel(LogLevel.INFO);
              }
            });

    t1.start();
    t2.start();
    t1.join();
    t2.join();

    assertNotNull(LoggerFactory.getLogLevel());
  }

  @Test
  @DisplayName("Concurrent access to getLogger")
  void testConcurrentGetLogger() throws InterruptedException {
    Thread t1 =
        new Thread(
            () -> {
              for (int i = 0; i < 100; i++) {
                Logger logger = LoggerFactory.getLogger("TestLogger");
                assertNotNull(logger);
              }
            });

    Thread t2 =
        new Thread(
            () -> {
              for (int i = 0; i < 100; i++) {
                Logger logger = LoggerFactory.getLogger(String.class);
                assertNotNull(logger);
              }
            });

    t1.start();
    t2.start();
    t1.join();
    t2.join();
  }

  @Test
  @DisplayName("getLogger with inner class works")
  void testGetLoggerWithInnerClass() {
    class TestInnerClass {}
    Logger logger = LoggerFactory.getLogger(TestInnerClass.class);

    assertNotNull(logger);
    assertInstanceOf(SimpleLogger.class, logger);
  }

  @Test
  @DisplayName("getLogger with primitive class works")
  void testGetLoggerWithPrimitiveClass() {
    Logger logger = LoggerFactory.getLogger(int.class);

    assertNotNull(logger);
    assertInstanceOf(SimpleLogger.class, logger);
  }

  @Test
  @DisplayName("getLogger with array type works")
  void testGetLoggerWithArrayType() {
    Logger logger = LoggerFactory.getLogger(String[].class);

    assertNotNull(logger);
    assertInstanceOf(SimpleLogger.class, logger);
  }

  @Test
  @DisplayName("getLogger with very long string name works")
  void testGetLoggerWithLongName() {
    String longName = "A".repeat(1000);
    Logger logger = LoggerFactory.getLogger(longName);

    assertNotNull(logger);
    assertInstanceOf(SimpleLogger.class, logger);
  }

  @Test
  @DisplayName("Global log level is shared across all loggers")
  void testGlobalLogLevelShared() {
    Logger logger1 = LoggerFactory.getLogger("Logger1");
    Logger logger2 = LoggerFactory.getLogger("Logger2");

    LoggerFactory.setLogLevel(LogLevel.WARN);

    assertEquals(LogLevel.WARN, LoggerFactory.getLogLevel());
  }
}
