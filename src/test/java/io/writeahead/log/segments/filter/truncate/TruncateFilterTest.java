package io.writeahead.log.segments.filter.truncate;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConstants;
import io.writeahead.log.models.meta.SegmentMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TruncateFilter Interface Tests")
class TruncateFilterTest {

  @Nested
  @DisplayName("Sealed Interface Contract Tests")
  class SealedInterfaceContractTests {

    @Test
    @DisplayName("TruncateFilter interface is sealed")
    void interfaceIsSealed() {
      assertTrue(TruncateFilter.class.isSealed(), "TruncateFilter should be sealed");
    }

    @Test
    @DisplayName("TruncateFilter permits only BeforeTimestampTruncateFilter")
    void permitsOnlyBeforeTimestampTruncateFilter() {
      Class<?>[] permitted = TruncateFilter.class.getPermittedSubclasses();
      assertEquals(1, permitted.length, "TruncateFilter should permit exactly one class");
      assertEquals(
          BeforeTimestampTruncateFilter.class,
          permitted[0],
          "TruncateFilter should permit BeforeTimestampTruncateFilter");
    }

    @Test
    @DisplayName("TruncateFilter is interface")
    void isInterface() {
      assertTrue(TruncateFilter.class.isInterface(), "TruncateFilter should be an interface");
    }
  }

  @Nested
  @DisplayName("Method Contract Tests")
  class MethodContractTests {

    @Test
    @DisplayName("TruncateFilter has shouldDelete method")
    void hasShouldDeleteMethod() throws NoSuchMethodException {
      assertDoesNotThrow(
          () -> TruncateFilter.class.getMethod("shouldDelete", SegmentMetadata.class),
          "TruncateFilter should have shouldDelete(SegmentMetadata) method");
    }

    @Test
    @DisplayName("TruncateFilter has name method")
    void hasNameMethod() throws NoSuchMethodException {
      assertDoesNotThrow(
          () -> TruncateFilter.class.getMethod("name"), "TruncateFilter should have name() method");
    }
  }

  @Nested
  @DisplayName("Interface Method Return Type Tests")
  class InterfaceMethodReturnTypeTests {

    @Test
    @DisplayName("shouldDelete method returns boolean")
    void shouldDeleteReturnsBoolean() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              100L,
              200L);

      boolean result = filter.shouldDelete(segment);
      assertTrue(result);
    }

    @Test
    @DisplayName("name method returns String")
    void nameReturnsString() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);

      String name = filter.name();

      assertNotNull(name, "name should return non-null String");
      assertInstanceOf(String.class, name);
    }
  }

  @Nested
  @DisplayName("Concrete Implementation via BeforeTimestampTruncateFilter")
  class ConcreteImplementationTests {

    @Test
    @DisplayName("BeforeTimestampTruncateFilter implements TruncateFilter")
    void beforeTimestampTruncateFilterImplementsTruncateFilter() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);
      assertInstanceOf(TruncateFilter.class, filter);
    }

    @Test
    @DisplayName("BeforeTimestampTruncateFilter is final")
    void beforeTimestampTruncateFilterIsFinal() {
      assertTrue(
          java.lang.reflect.Modifier.isFinal(BeforeTimestampTruncateFilter.class.getModifiers()),
          "BeforeTimestampTruncateFilter should be final");
    }

    @Test
    @DisplayName("TruncateFilter can be instantiated through BeforeTimestampTruncateFilter")
    void canBeInstantiatedThroughBeforeTimestampTruncateFilter() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);
      assertNotNull(filter);
    }
  }

  @Nested
  @DisplayName("Contract Enforcement Tests")
  class ContractEnforcementTests {

    @Test
    @DisplayName("shouldDelete must not return null")
    void shouldDeleteMustNotReturnNull() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);
      SegmentMetadata segment =
          new SegmentMetadata(
              "segment.log",
              1L,
              System.currentTimeMillis(),
              WalConstants.SEGMENT_HEADER_SIZE + WalConstants.SEGMENT_FOOTER_SIZE + 100,
              1L,
              100L,
              200L);

      boolean result = filter.shouldDelete(segment);

      assertNotNull(result, "shouldDelete must never return null");
    }

    @Test
    @DisplayName("name must not return null")
    void nameMustNotReturnNull() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);

      String name = filter.name();

      assertNotNull(name, "name must never return null");
    }

    @Test
    @DisplayName("name must not return empty string")
    void nameMustNotReturnEmptyString() {
      TruncateFilter filter = new BeforeTimestampTruncateFilter(1000L);

      String name = filter.name();

      assertFalse(name.isEmpty(), "name must never return empty string");
    }
  }

  @Nested
  @DisplayName("Method Signature Tests")
  class MethodSignatureTests {

    @Test
    @DisplayName("shouldDelete takes SegmentMetadata parameter")
    void shouldDeleteTakesSegmentMetadataParameter() throws NoSuchMethodException {
      java.lang.reflect.Method method =
          TruncateFilter.class.getMethod("shouldDelete", SegmentMetadata.class);
      assertEquals(1, method.getParameterCount());
      assertEquals(SegmentMetadata.class, method.getParameterTypes()[0]);
    }

    @Test
    @DisplayName("shouldDelete returns boolean primitive")
    void shouldDeleteReturnsBooleanPrimitive() throws NoSuchMethodException {
      java.lang.reflect.Method method =
          TruncateFilter.class.getMethod("shouldDelete", SegmentMetadata.class);
      assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("name takes no parameters")
    void nameHasNoParameters() throws NoSuchMethodException {
      java.lang.reflect.Method method = TruncateFilter.class.getMethod("name");
      assertEquals(0, method.getParameterCount());
    }

    @Test
    @DisplayName("name returns String type")
    void nameReturnsStringType() throws NoSuchMethodException {
      java.lang.reflect.Method method = TruncateFilter.class.getMethod("name");
      assertEquals(String.class, method.getReturnType());
    }
  }
}
