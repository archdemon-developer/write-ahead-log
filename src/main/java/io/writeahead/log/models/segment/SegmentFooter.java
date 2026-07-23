package io.writeahead.log.models.segment;

import io.writeahead.log.constants.WalConstants;
import io.writeahead.log.utils.Crc32Utils;

import java.io.*;

public record SegmentFooter(
        int entryCount,
        long minTimestamp,
        long maxTimestamp,
        long checksum) {

    private static final long COMPLETE_MARKER = 0xDEADBEEFL;
    private static final int FOOTER_SIZE = WalConstants.SEGMENT_FOOTER_SIZE;

    public static SegmentFooter create(int entryCount, long minTimestamp, long maxTimestamp) throws IOException {
        long checksum = calculateChecksum(entryCount, minTimestamp, maxTimestamp);
        return new SegmentFooter(entryCount, minTimestamp, maxTimestamp, checksum);
    }

    public byte[] toBytes() throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(FOOTER_SIZE);
             DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
            dataOutputStream.writeInt(entryCount);
            dataOutputStream.writeLong(minTimestamp);
            dataOutputStream.writeLong(maxTimestamp);
            dataOutputStream.writeLong(checksum);
            return byteArrayOutputStream.toByteArray();
        }
    }

    public static SegmentFooter fromBytes(byte[] data) throws IOException {
        if (data.length < FOOTER_SIZE) {
            throw new IOException("Footer data too short: " + data.length + " < " + FOOTER_SIZE);
        }

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
             DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream)) {
            int entryCount = dataInputStream.readInt();
            long minTimestamp = dataInputStream.readLong();
            long maxTimestamp = dataInputStream.readLong();
            long checksum = dataInputStream.readLong();
            return new SegmentFooter(entryCount, minTimestamp, maxTimestamp, checksum);
        }
    }

    public boolean isValid() {
        try {
            long computedChecksum = calculateChecksum(entryCount, minTimestamp, maxTimestamp);
            return computedChecksum == checksum;
        } catch (IOException e) {
            return false;
        }
    }

    private static long calculateChecksum(int entryCount, long minTimestamp, long maxTimestamp) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
            dataOutputStream.writeInt(entryCount);
            dataOutputStream.writeLong(minTimestamp);
            dataOutputStream.writeLong(maxTimestamp);
            byte[] dataToCheck = byteArrayOutputStream.toByteArray();
            return Crc32Utils.compute(dataToCheck);
        }
    }

    @Override
    public String toString() {
        return "SegmentFooter{"
                + "entryCount=" + entryCount
                + ", minTimestamp=" + minTimestamp
                + ", maxTimestamp=" + maxTimestamp
                + ", checksum=" + checksum
                + ", valid=" + isValid()
                + '}';
    }

}
