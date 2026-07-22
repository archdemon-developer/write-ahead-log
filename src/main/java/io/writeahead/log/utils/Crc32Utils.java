package io.writeahead.log.utils;

import io.writeahead.log.serdes.EntrySerdes;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

public class Crc32Utils {

  private Crc32Utils() {}

  public static long compute(byte[] data) {
    if (data == null) {
      throw new NullPointerException("data should not be null");
    }

    CRC32 crc32 = new CRC32();
    crc32.update(data);
    return crc32.getValue();
  }

  public static long computeEntryCrc(long timestamp, int size, byte[] data) throws IOException {
    byte[] serialized = EntrySerdes.serializeEntrySanseCrc(timestamp, size, data);
    return compute(serialized);
  }
}
