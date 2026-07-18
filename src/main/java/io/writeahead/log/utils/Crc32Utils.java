package io.writeahead.log.utils;

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

  public static long compute(long timestamp, int size, byte[] data) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

    dataOutputStream.writeLong(timestamp);
    dataOutputStream.writeInt(size);
    dataOutputStream.write(data);

    byte[] serialized = byteArrayOutputStream.toByteArray();

    byteArrayOutputStream.close();
    dataOutputStream.close();

    return compute(serialized);
  }
}
