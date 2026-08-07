package io.writeahead.log.models.results;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.models.LogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FilterResult Tests — 100% Validation Coverage")
class FilterResultTest {

  private final LogEntry testEntry = new LogEntry(100, new byte[100], 1000L);

  @Nested
  @DisplayName("Compact Constructor Validation")
  class ConstructorValidation {

    @Test
    void rejectsEntryNull() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new FilterResult(true, null, "TestFilter"));
      assertTrue(ex.getMessage().contains("entry should not null"));
    }

    @Test
    void rejectsFilterNameNull() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new FilterResult(true, testEntry, null));
      assertTrue(ex.getMessage().contains("filterName should not be null"));
    }

    @Test
    void rejectsFilterNameEmpty() {
      IllegalArgumentException ex =
          assertThrows(IllegalArgumentException.class, () -> new FilterResult(true, testEntry, ""));
      assertTrue(ex.getMessage().contains("filterName should not be null or empty"));
    }

    @Test
    void acceptsValidFilterResult() {
      FilterResult result = new FilterResult(true, testEntry, "ValidFilter");
      assertTrue(result.matches());
      assertEquals("ValidFilter", result.filterName());
    }
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    void acceptedCreatesAcceptedResult() {
      FilterResult result = FilterResult.accepted(testEntry, "TimestampFilter");
      assertTrue(result.matches());
      assertTrue(result.isAccepted());
    }

    @Test
    void rejectedCreatesRejectedResult() {
      FilterResult result = FilterResult.rejected(testEntry, "TimestampFilter");
      assertFalse(result.matches());
      assertTrue(result.isRejected());
    }
  }

  @Nested
  @DisplayName("Helper Methods")
  class HelperMethods {

    @Test
    void isAcceptedReturnsTrueForAccepted() {
      FilterResult result = FilterResult.accepted(testEntry, "Filter");
      assertTrue(result.isAccepted());
    }

    @Test
    void isAcceptedReturnsFalseForRejected() {
      FilterResult result = FilterResult.rejected(testEntry, "Filter");
      assertFalse(result.isAccepted());
    }

    @Test
    void isRejectedReturnsTrueForRejected() {
      FilterResult result = FilterResult.rejected(testEntry, "Filter");
      assertTrue(result.isRejected());
    }

    @Test
    void isRejectedReturnsFalseForAccepted() {
      FilterResult result = FilterResult.accepted(testEntry, "Filter");
      assertFalse(result.isRejected());
    }
  }
}
