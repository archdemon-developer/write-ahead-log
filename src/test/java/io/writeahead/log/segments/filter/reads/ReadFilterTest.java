package io.writeahead.log.segments.filter.reads;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConstants;
import io.writeahead.log.enums.strategies.ReadFilterType;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.models.meta.SegmentMetadata;
import io.writeahead.log.models.results.FilterResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReadFilter Interface Tests")
class ReadFilterTest {

  @Nested
  @DisplayName("Sealed Interface Contract Tests")
  class SealedInterfaceContractTests {

    @Test
    @DisplayName("ReadFilter interface is sealed")
    void interfaceIsSealed() {
      assertTrue(ReadFilter.class.isSealed(), "ReadFilter should be sealed");
    }

    @Test
    @DisplayName("ReadFilter permits only AfterTimestampFilter")
    void permitsOnlyAfterTimestampFilter() {
      Class<?>[] permitted = ReadFilter.class.getPermittedSubclasses();
      assertEquals(1, permitted.length, "ReadFilter should permit exactly one class");
      assertEquals(
          AfterTimestampFilter.class,
          permitted[0],
          "ReadFilter should permit AfterTimestampFilter");
    }

    @Test
    @DisplayName("ReadFilter is interface")
    void isInterface() {
      assertTrue(ReadFilter.class.isInterface(), "ReadFilter should be an interface");
    }
  }

  @Nested
  @DisplayName("Method Contract Tests")
  class MethodContractTests {

    @Test
    @DisplayName("ReadFilter has matches method")
    void hasMatchesMethod() throws NoSuchMethodException {
      assertDoesNotThrow(
          () -> ReadFilter.class.getMethod("matches", LogEntry.class),
          "ReadFilter should have matches(LogEntry) method");
    }

    @Test
    @DisplayName("ReadFilter has name method")
    void hasNameMethod() throws NoSuchMethodException {
      assertDoesNotThrow(
          () -> ReadFilter.class.getMethod("name"), "ReadFilter should have name() method");
    }

    @Test
    @DisplayName("ReadFilter has canSkipSegment method")
    void hasCanSkipSegmentMethod() throws NoSuchMethodException {
      assertDoesNotThrow(
          () -> ReadFilter.class.getMethod("canSkipSegment", SegmentMetadata.class),
          "ReadFilter should have canSkipSegment(SegmentMetadata) method");
    }
  }

  @Nested
  @DisplayName("Default Behavior Tests")
  class DefaultBehaviorTests {

    @Test
    @DisplayName("canSkipSegment null handling")
    void canSkipSegmentNullHandling() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      assertThrows(
          NullPointerException.class,
          () -> filter.canSkipSegment(null),
          "canSkipSegment should throw NPE on null segment");
    }
  }

  @Nested
  @DisplayName("Interface Method Return Type Tests")
  class InterfaceMethodReturnTypeTests {

    @Test
    @DisplayName("matches method returns FilterResult")
    void matchesReturnsFilterResult() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      LogEntry entry = new LogEntry(100, new byte[100], 1500L);

      FilterResult result = filter.matches(entry);

      assertNotNull(result, "matches should return non-null FilterResult");
      assertInstanceOf(FilterResult.class, result);
    }

    @Test
    @DisplayName("name method returns ReadFilterType")
    void nameReturnsReadFilterType() {
      ReadFilter filter = new AfterTimestampFilter(1000L);

      ReadFilterType name = filter.name();

      assertNotNull(name, "name should return non-null ReadFilterType");
      assertInstanceOf(ReadFilterType.class, name);
    }

    @Test
    @DisplayName("canSkipSegment method returns boolean")
    void canSkipSegmentReturnsBoolean() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              100L,
              200L);

      boolean result = filter.canSkipSegment(segment);

      assertInstanceOf(Boolean.class, result);
    }
  }

  @Nested
  @DisplayName("Concrete Implementation via AfterTimestampFilter")
  class ConcreteImplementationTests {

    @Test
    @DisplayName("AfterTimestampFilter implements ReadFilter")
    void afterTimestampFilterImplementsReadFilter() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      assertInstanceOf(ReadFilter.class, filter);
    }

    @Test
    @DisplayName("AfterTimestampFilter is final")
    void afterTimestampFilterIsFinal() {
      assertTrue(
          java.lang.reflect.Modifier.isFinal(AfterTimestampFilter.class.getModifiers()),
          "AfterTimestampFilter should be final");
    }

    @Test
    @DisplayName("ReadFilter can be instantiated through AfterTimestampFilter")
    void canBeInstantiatedThroughAfterTimestampFilter() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      assertNotNull(filter);
    }
  }

  @Nested
  @DisplayName("Contract Enforcement Tests")
  class ContractEnforcementTests {

    @Test
    @DisplayName("matches must not return null")
    void matchesMustNotReturnNull() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      LogEntry entry = new LogEntry(100, new byte[100], 1500L);

      FilterResult result = filter.matches(entry);

      assertNotNull(result, "matches must never return null");
    }

    @Test
    @DisplayName("name must not return null")
    void nameMustNotReturnNull() {
      ReadFilter filter = new AfterTimestampFilter(1000L);

      ReadFilterType name = filter.name();

      assertNotNull(name, "name must never return null");
    }

    @Test
    @DisplayName("canSkipSegment must not return null")
    void canSkipSegmentMustNotReturnNull() {
      ReadFilter filter = new AfterTimestampFilter(1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              100L,
              200L);

      boolean result = filter.canSkipSegment(segment);

      assertTrue(result, "canSkipSegment must never return null");
    }
  }
}
