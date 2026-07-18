package io.writeahead.log.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.writeahead.log.models.SegmentMetadata;
import io.writeahead.log.models.WalMetadata;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MetadataParserTest {

  @Test
  public void testMultipleSegments() {
    WalMetadata original =
        new WalMetadata(
            "wal-2026-07-19-120000-002.log",
            List.of(
                new SegmentMetadata("wal-2026-07-19-120000-001.log", 1000, 5000000),
                new SegmentMetadata("wal-2026-07-19-120000-002.log", 5000001, 10000000)));

    String json = MetadataParser.toJson(original);

    WalMetadata deserialized = MetadataParser.parseJson(json);

    assertEquals(original, deserialized);
  }

  @Test
  public void testNullLastActiveSegmentEmptySegments() {
    WalMetadata original = new WalMetadata(null, new ArrayList<>());

    String json = MetadataParser.toJson(original);
    WalMetadata deserialized = MetadataParser.parseJson(json);

    assertEquals(original, deserialized);
  }

  @Test
  public void testSingleSegment() {
    WalMetadata original =
        new WalMetadata(
            "wal-2026-07-19-120000-001.log",
            List.of(new SegmentMetadata("wal-2026-07-19-120000-001.log", 0, 0)));

    String json = MetadataParser.toJson(original);
    WalMetadata deserialized = MetadataParser.parseJson(json);

    assertEquals(original, deserialized);
  }

  @Test
  public void testEdgeCaseTimestamps() {
    WalMetadata original =
        new WalMetadata(
            "wal-2026-07-19-120000-003.log",
            List.of(
                new SegmentMetadata("wal-2026-07-19-120000-001.log", Long.MIN_VALUE, 1000),
                new SegmentMetadata("wal-2026-07-19-120000-002.log", 1001, Long.MAX_VALUE)));

    String json = MetadataParser.toJson(original);
    WalMetadata deserialized = MetadataParser.parseJson(json);

    assertEquals(original, deserialized);
  }

  @Test
  void testParseJsonWithNullLastActiveSegment() {
    WalMetadata metadata =
        new WalMetadata(null, List.of(new SegmentMetadata("wal-001.log", 100, 200)));

    String json = MetadataParser.toJson(metadata);
    WalMetadata parsed = MetadataParser.parseJson(json);

    assertNull(parsed.lastActiveSegment());
    assertEquals(1, parsed.segments().size());
  }

  @Test
  void testExtractStringNotFound() {
    // This tests the private method indirectly - if key not found, should handle gracefully
    String json = "{\"other\": \"value\"}";
    WalMetadata parsed = MetadataParser.parseJson(json);

    assertNull(parsed.lastActiveSegment(), "Missing key should parse as null");
    assertEquals(0, parsed.segments().size(), "Empty segments should be handled");
  }
}
