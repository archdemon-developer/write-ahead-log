//package io.writeahead.log.utils;
//
//import io.writeahead.log.models.segment.SegmentMetadata;
//import io.writeahead.log.models.wal.WalMetadata;
//import java.io.ByteArrayInputStream;
//import java.io.DataInputStream;
//import java.io.EOFException;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//
//public class MetadataParserUtils {
//  public static String toJson(WalMetadata metadata) {
//    StringBuilder jsonBuilder = new StringBuilder();
//    jsonBuilder.append("{\n");
//    jsonBuilder.append("  \"lastActiveSegment\": ");
//    if (metadata.lastActiveSegment() == null) {
//      jsonBuilder.append("null");
//    } else {
//      jsonBuilder.append("\"").append(metadata.lastActiveSegment()).append("\"");
//    }
//    jsonBuilder.append(",\n");
//    jsonBuilder.append("  \"segments\": [\n");
//
//    List<SegmentMetadata> segments = metadata.segments();
//    for (int i = 0; i < segments.size(); i++) {
//      SegmentMetadata seg = segments.get(i);
//      jsonBuilder.append("    {\n");
//      jsonBuilder.append("      \"filename\": \"").append(seg.filename()).append("\",\n");
//      jsonBuilder.append("      \"minTimestamp\": ").append(seg.minTimestamp()).append(",\n");
//      jsonBuilder.append("      \"maxTimestamp\": ").append(seg.maxTimestamp()).append("\n");
//      jsonBuilder.append("    }");
//      if (i < segments.size() - 1) jsonBuilder.append(",");
//      jsonBuilder.append("\n");
//    }
//
//    jsonBuilder.append("  ]\n");
//    jsonBuilder.append("}\n");
//    return jsonBuilder.toString();
//  }
//
//  public static WalMetadata parseJson(String json) {
//    String lastSegment = extractString(json, "lastActiveSegment");
//    List<SegmentMetadata> segments = parseSegmentsArray(json);
//    return new WalMetadata(lastSegment, segments);
//  }
//
//  private static String extractString(String json, String key) {
//    String pattern = "\"" + key + "\": \"";
//    int start = json.indexOf(pattern);
//    if (start == -1) return null;
//    start += pattern.length();
//    int end = json.indexOf("\"", start);
//    return json.substring(start, end);
//  }
//
//  private static List<SegmentMetadata> parseSegmentsArray(String json) {
//    List<SegmentMetadata> segments = new ArrayList<>();
//    int arrayStart = json.indexOf("\"segments\": [");
//    if (arrayStart == -1) return segments;
//
//    arrayStart = json.indexOf("[", arrayStart);
//    int arrayEnd = json.indexOf("]", arrayStart);
//    String arrayContent = json.substring(arrayStart + 1, arrayEnd);
//
//    int pos = 0;
//    while (pos < arrayContent.length()) {
//      int objStart = arrayContent.indexOf("{", pos);
//      if (objStart == -1) break;
//
//      int objEnd = arrayContent.indexOf("}", objStart);
//      if (objEnd == -1) break;
//
//      String objStr = arrayContent.substring(objStart, objEnd + 1);
//
//      String filename = extractString(objStr, "filename");
//      long minTs = extractLong(objStr, "minTimestamp");
//      long maxTs = extractLong(objStr, "maxTimestamp");
//      segments.add(new SegmentMetadata(filename, minTs, maxTs));
//
//      pos = objEnd + 1;
//    }
//
//    return segments;
//  }
//
//  private static long extractLong(String json, String key) {
//    String pattern = "\"" + key + "\": ";
//    int start = json.indexOf(pattern);
//    if (start == -1) return -1;
//    start += pattern.length();
//    int end = json.indexOf(",", start);
//    if (end == -1) end = json.indexOf("}", start);
//    String numStr = json.substring(start, end).trim();
//    numStr = numStr.split("\\s+")[0];
//    return Long.parseLong(numStr);
//  }
//
//  public static long parseLastEntryTimestamp(byte[] buffer) throws IOException {
//    try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buffer);
//        DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream)) {
//
//      long lastTimestamp = -1;
//
//      while (dataInputStream.available() > 0) {
//        try {
//          long timestamp = dataInputStream.readLong();
//          int size = dataInputStream.readInt();
//          byte[] data = new byte[size];
//          dataInputStream.readFully(data);
//          long crc = dataInputStream.readLong();
//          lastTimestamp = timestamp;
//        } catch (EOFException e) {
//          break;
//        }
//      }
//
//      if (lastTimestamp == -1) {
//        throw new IOException("No valid entries found in file");
//      }
//
//      return lastTimestamp;
//    }
//  }
//}
