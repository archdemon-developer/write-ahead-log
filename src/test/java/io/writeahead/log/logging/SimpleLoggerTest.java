package io.writeahead.log.logging;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.enums.levels.LogLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SimpleLogger - Logging Functionality")
public class SimpleLoggerTest {

  private Logger logger;
  private PrintStream originalOut;
  private ByteArrayOutputStream capturedOutput;

  @BeforeEach
  void setUp() {
    logger = new SimpleLogger("TestLogger", LogLevel.DEBUG);
    originalOut = System.out;
    capturedOutput = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capturedOutput));
  }

  @AfterEach
  void tearDown() {
    System.setOut(originalOut);
  }

  @Test
  @DisplayName("debug with message logs when DEBUG enabled")
  void testDebugWithMessage() {
    logger.debug("Debug message");

    String output = capturedOutput.toString();
    assertTrue(output.contains("Debug message"));
    assertTrue(output.contains("DEBUG"));
    assertTrue(output.contains("TestLogger"));
  }

  @Test
  @DisplayName("debug with message doesn't log when DEBUG disabled")
  void testDebugWithMessageDisabled() {
    logger = new SimpleLogger("TestLogger", LogLevel.INFO);

    logger.debug("Debug message");

    String output = capturedOutput.toString();
    assertFalse(output.contains("Debug message"));
  }

  @Test
  @DisplayName("debug with varargs formats correctly")
  void testDebugWithVarargs() {
    logger.debug("Value: {}", 42);

    String output = capturedOutput.toString();
    assertTrue(output.contains("Value: 42"));
  }

  @Test
  @DisplayName("debug with multiple varargs")
  void testDebugWithMultipleVarargs() {
    logger.debug("{} {} {}", "a", "b", "c");

    String output = capturedOutput.toString();
    assertTrue(output.contains("a b c"));
  }

  @Test
  @DisplayName("debug with empty varargs")
  void testDebugWithEmptyVarargs() {
    logger.debug("Message", new Object[0]);

    String output = capturedOutput.toString();
    assertTrue(output.contains("Message"));
  }

  @Test
  @DisplayName("debug with null varargs")
  void testDebugWithNullVarargs() {
    logger.debug("Message");

    String output = capturedOutput.toString();
    assertTrue(output.contains("Message"));
  }

  @Test
  @DisplayName("info with message logs when INFO enabled")
  void testInfoWithMessage() {
    logger.info("Info message");

    String output = capturedOutput.toString();
    assertTrue(output.contains("Info message"));
    assertTrue(output.contains("INFO"));
    assertTrue(output.contains("TestLogger"));
  }

  @Test
  @DisplayName("info with message doesn't log when INFO disabled")
  void testInfoWithMessageDisabled() {
    logger = new SimpleLogger("TestLogger", LogLevel.WARN);

    logger.info("Info message");

    String output = capturedOutput.toString();
    assertFalse(output.contains("Info message"));
  }

  @Test
  @DisplayName("info with varargs formats correctly")
  void testInfoWithVarargs() {
    logger.info("Count: {}", 100);

    String output = capturedOutput.toString();
    assertTrue(output.contains("Count: 100"));
  }

  @Test
  @DisplayName("warn with message logs when WARN enabled")
  void testWarnWithMessage() {
    logger.warn("Warning message");

    String output = capturedOutput.toString();
    assertTrue(output.contains("Warning message"));
    assertTrue(output.contains("WARN"));
    assertTrue(output.contains("TestLogger"));
  }

  @Test
  @DisplayName("warn with message doesn't log when WARN disabled")
  void testWarnWithMessageDisabled() {
    logger = new SimpleLogger("TestLogger", LogLevel.ERROR);

    logger.warn("Warning message");

    String output = capturedOutput.toString();
    assertFalse(output.contains("Warning message"));
  }

  @Test
  @DisplayName("warn with varargs formats correctly")
  void testWarnWithVarargs() {
    logger.warn("Available: {}", 50);

    String output = capturedOutput.toString();
    assertTrue(output.contains("Available: 50"));
  }

  @Test
  @DisplayName("error with message logs always")
  void testErrorWithMessage() {
    logger = new SimpleLogger("TestLogger", LogLevel.ERROR);
    logger.error("Error message");

    String output = capturedOutput.toString();
    assertTrue(output.contains("Error message"));
    assertTrue(output.contains("ERROR"));
  }

  @Test
  @DisplayName("error with varargs logs always")
  void testErrorWithVarargs() {
    logger = new SimpleLogger("TestLogger", LogLevel.ERROR);
    logger.error("Error code: {}", 500);

    String output = capturedOutput.toString();
    assertTrue(output.contains("Error code: 500"));
  }

  @Test
  @DisplayName("error with Throwable logs exception details")
  void testErrorWithThrowable() {
    Exception testException = new IllegalArgumentException("Test error");
    logger.error("Exception occurred", testException);

    String output = capturedOutput.toString();
    assertTrue(output.contains("Exception occurred"));
    assertTrue(output.contains("IllegalArgumentException"));
  }

  @Test
  @DisplayName("error with Throwable and varargs logs both")
  void testErrorWithThrowableAndVarargs() {
    Exception testException = new RuntimeException("Runtime error");
    logger.error("Failed: {}", testException);

    String output = capturedOutput.toString();
    assertTrue(output.contains("Failed"));
  }

  @Test
  @DisplayName("Logger name appears in output")
  void testLoggerNameInOutput() {
    Logger customLogger = new SimpleLogger("CustomName", LogLevel.DEBUG);
    System.setOut(new PrintStream(capturedOutput));

    customLogger.info("Test");

    String output = capturedOutput.toString();
    assertTrue(output.contains("CustomName"));
  }

  @Test
  @DisplayName("Multiple logs accumulate")
  void testMultipleLogs() {
    logger.debug("First");
    logger.info("Second");
    logger.warn("Third");

    String output = capturedOutput.toString();
    assertTrue(output.contains("First"));
    assertTrue(output.contains("Second"));
    assertTrue(output.contains("Third"));
  }

  @Test
  @DisplayName("Log level filtering with DEBUG level")
  void testLogLevelFilteringDebug() {
    logger = new SimpleLogger("TestLogger", LogLevel.DEBUG);

    logger.debug("Debug");
    logger.info("Info");
    logger.warn("Warn");
    logger.error("Error");

    String output = capturedOutput.toString();
    assertTrue(output.contains("Debug"));
    assertTrue(output.contains("Info"));
    assertTrue(output.contains("Warn"));
    assertTrue(output.contains("Error"));
  }

  @Test
  @DisplayName("Log level filtering with INFO level")
  void testLogLevelFilteringInfo() {
    logger = new SimpleLogger("TestLogger", LogLevel.INFO);

    logger.debug("Debug");
    logger.info("Info");
    logger.warn("Warn");
    logger.error("Error");

    String output = capturedOutput.toString();
    assertFalse(output.contains("Debug"));
    assertTrue(output.contains("Info"));
    assertTrue(output.contains("Warn"));
    assertTrue(output.contains("Error"));
  }

  @Test
  @DisplayName("Log level filtering with WARN level")
  void testLogLevelFilteringWarn() {
    logger = new SimpleLogger("TestLogger", LogLevel.WARN);

    logger.debug("Debug");
    logger.info("Info");
    logger.warn("Warn");
    logger.error("Error");

    String output = capturedOutput.toString();
    assertFalse(output.contains("Debug"));
    assertFalse(output.contains("Info"));
    assertTrue(output.contains("Warn"));
    assertTrue(output.contains("Error"));
  }

  @Test
  @DisplayName("Log level filtering with ERROR level")
  void testLogLevelFilteringError() {
    logger = new SimpleLogger("TestLogger", LogLevel.ERROR);

    logger.debug("Debug");
    logger.info("Info");
    logger.warn("Warn");
    logger.error("Error");

    String output = capturedOutput.toString();
    assertFalse(output.contains("Debug"));
    assertFalse(output.contains("Info"));
    assertFalse(output.contains("Warn"));
    assertTrue(output.contains("Error"));
  }

  @Test
  @DisplayName("Empty message logs without error")
  void testEmptyMessage() {
    logger.info("");

    String output = capturedOutput.toString();
    assertTrue(output.contains("INFO"));
  }

  @Test
  @DisplayName("Very long message logs fully")
  void testVeryLongMessage() {
    String longMessage = "A".repeat(1000);
    logger.info(longMessage);

    String output = capturedOutput.toString();
    assertTrue(output.contains(longMessage));
  }

  @Test
  @DisplayName("Message with special characters")
  void testSpecialCharactersInMessage() {
    logger.info("Message with [brackets] {braces} and <angle>");

    String output = capturedOutput.toString();
    assertTrue(output.contains("Message with [brackets] {braces} and <angle>"));
  }

  @Test
  @DisplayName("Timestamp is included in output")
  void testTimestampInOutput() {
    logger.info("Test message");

    String output = capturedOutput.toString();
    assertTrue(output.matches("(?s).*\\d{4}-\\d{2}-\\d{2}.*"));
  }

  @Test
  @DisplayName("Thread name is included in output")
  void testThreadNameInOutput() {
    logger.info("Test message");

    String output = capturedOutput.toString();
    assertTrue(output.contains(Thread.currentThread().getName()));
  }

  @Test
  @DisplayName("Log level appears in output")
  void testLogLevelInOutput() {
    logger.debug("Debug msg");
    capturedOutput.reset();

    logger.info("Info msg");
    String output = capturedOutput.toString();
    assertTrue(output.contains("INFO"));
  }

  @Test
  @DisplayName("Error with null Throwable")
  void testErrorWithNullThrowable() {
    logger.error("Message", (Throwable) null);

    String output = capturedOutput.toString();
    assertTrue(output.contains("Message"));
  }

  @Test
  @DisplayName("Varargs with various types")
  void testVarargsVariousTypes() {
    logger.info("Values: {} {} {} {}", 42, 3.14, true, null);

    String output = capturedOutput.toString();
    assertTrue(output.contains("Values:"));
  }

  @Test
  @DisplayName("Constructor with DEBUG level")
  void testConstructorDebugLevel() {
    logger = new SimpleLogger("TestLogger", LogLevel.DEBUG);
    logger.debug("Test");

    String output = capturedOutput.toString();
    assertTrue(output.contains("Test"));
  }

  @Test
  @DisplayName("Constructor with different names")
  void testConstructorDifferentNames() {
    Logger logger1 = new SimpleLogger("Logger1", LogLevel.INFO);
    Logger logger2 = new SimpleLogger("Logger2", LogLevel.INFO);

    logger1.info("From logger 1");
    capturedOutput.reset();
    logger2.info("From logger 2");

    String output = capturedOutput.toString();
    assertTrue(output.contains("Logger2"));
  }

  @Test
  @DisplayName("Multiple exceptions handled")
  void testMultipleExceptions() {
    Exception ex1 = new RuntimeException("Error 1");
    Exception ex2 = new IllegalArgumentException("Error 2");

    logger.error("First error", ex1);
    capturedOutput.reset();
    logger.error("Second error", ex2);

    String output = capturedOutput.toString();
    assertTrue(output.contains("IllegalArgumentException"));
  }

  @Test
  @DisplayName("Chained exceptions handled")
  void testChainedExceptions() {
    Exception cause = new IOException("Original cause");
    Exception wrapper = new RuntimeException("Wrapper", cause);

    logger.error("Operation failed", wrapper);

    String output = capturedOutput.toString();
    assertTrue(output.contains("Wrapper"));
  }

  @Test
  @DisplayName("Format with escaped characters")
  void testFormatWithEscapedCharacters() {
    logger.info("Path: {}", "C:\\Users\\test");

    String output = capturedOutput.toString();
    assertTrue(output.contains("Path:"));
  }

  @Test
  @DisplayName("Debug doesn't log when minimum level is WARN")
  void testDebugNotLoggedWarnLevel() {
    logger = new SimpleLogger("TestLogger", LogLevel.WARN);
    logger.debug("Should not appear");

    String output = capturedOutput.toString();
    assertFalse(output.contains("Should not appear"));
  }

  @Test
  @DisplayName("Message with percentage signs")
  void testMessageWithPercentageSigns() {
    logger.info("Progress: 100%");

    String output = capturedOutput.toString();
    assertTrue(output.contains("Progress: 100%"));
  }

  @Test
  @DisplayName("Concurrent logging from multiple threads")
  void testConcurrentLogging() throws InterruptedException {
    capturedOutput.reset();

    Thread t1 =
        new Thread(
            () -> {
              for (int i = 0; i < 10; i++) {
                logger.info("Thread1-Message" + i);
              }
            });

    Thread t2 =
        new Thread(
            () -> {
              for (int i = 0; i < 10; i++) {
                logger.info("Thread2-Message" + i);
              }
            });

    t1.start();
    t2.start();
    t1.join();
    t2.join();

    String output = capturedOutput.toString();
    assertFalse(output.isEmpty());
  }
}
