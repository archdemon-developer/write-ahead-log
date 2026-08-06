package io.writeahead.log.serdes;

import java.io.*;

public class EntrySerdes {

  private EntrySerdes() {}

  public static byte[] serializeEntrySanseCrc(long timestamp, int size, byte[] data)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (DataOutputStream dos = new DataOutputStream(baos)) {
      dos.writeLong(timestamp);
      dos.writeInt(size);
      dos.write(data);
    }
    return baos.toByteArray();
  }

  public static byte[] serializeEntryWithCrc(long timestamp, int size, byte[] data, long crc)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (DataOutputStream dos = new DataOutputStream(baos)) {
      dos.writeLong(timestamp);
      dos.writeInt(size);
      dos.write(data);
      dos.writeLong(crc);
    }
    return baos.toByteArray();
  }

  public static Object[] deserializeEntry(DataInputStream dis) throws IOException {
    long timestamp = dis.readLong();
    int size = dis.readInt();
    byte[] data = new byte[size];
    dis.readFully(data);
    long crc = dis.readLong();

    return new Object[] {timestamp, size, data, crc};
  }
}
