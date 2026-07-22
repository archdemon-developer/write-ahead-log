package io.writeahead.log.serdes;

import java.io.*;

public class EntrySerdes {

    private EntrySerdes() {}

    public static byte[] serializeEntrySanseCrc(long timestamp, int size, byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeLong(timestamp);
        dos.writeInt(size);
        dos.write(data);

        byte[] result = baos.toByteArray();
        dos.close();
        baos.close();

        return result;
    }

    public static byte[] serializeEntryWithCrc(long timestamp, int size, byte[] data, long crc) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeLong(timestamp);
        dos.writeInt(size);
        dos.write(data);
        dos.writeLong(crc);

        byte[] result = baos.toByteArray();
        dos.close();
        baos.close();

        return result;
    }

    /**
     * Deserialize entry (exact position, assumes valid stream).
     * Reads: timestamp(8) + size(4) + data(size) + crc(8)
     * Returns: [timestamp, size, data, crc]
     */
    public static Object[] deserializeEntry(DataInputStream dis) throws IOException {
        long timestamp = dis.readLong();
        int size = dis.readInt();
        byte[] data = new byte[size];
        dis.readFully(data);
        long crc = dis.readLong();

        return new Object[]{timestamp, size, data, crc};
    }
}
