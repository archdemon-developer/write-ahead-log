package io.writeahead.log.models;

import io.writeahead.log.utils.Crc32Utils;

import java.io.*;

public record SegmentFooter(
        int entryCount,
        long minTimestamp,
        long maxTimestamp,
        long completeMarker,
        long checksum) {

    private static final long COMPLETE_MARKER = 0xDEADBEEFL;
    private static final int FOOTER_SIZE = 36;

    public static SegmentFooter create(int entryCount, long minTimestamp, long maxTimestamp) throws IOException {
        long checksum = calculateChecksum(entryCount, minTimestamp, maxTimestamp, COMPLETE_MARKER);
        return new SegmentFooter(entryCount, minTimestamp, maxTimestamp, COMPLETE_MARKER, checksum);
    }

    public byte[] toBytes() throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(FOOTER_SIZE);
             DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
            dataOutputStream.writeInt(entryCount);
            dataOutputStream.writeLong(minTimestamp);
            dataOutputStream.writeLong(maxTimestamp);
            dataOutputStream.writeLong(completeMarker);
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
            long completeMarker = dataInputStream.readLong();
            long checksum = dataInputStream.readLong();
            return new SegmentFooter(entryCount, minTimestamp, maxTimestamp, completeMarker, checksum);
        }
    }

    public boolean isValid() {
        try {
            long computedChecksum = calculateChecksum(entryCount, minTimestamp, maxTimestamp, completeMarker);
            return computedChecksum == checksum;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean isComplete() {
        return completeMarker == COMPLETE_MARKER;
    }

    private static long calculateChecksum(int entryCount, long minTimestamp, long maxTimestamp, long completeMarker) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
            dataOutputStream.writeInt(entryCount);
            dataOutputStream.writeLong(minTimestamp);
            dataOutputStream.writeLong(maxTimestamp);
            dataOutputStream.writeLong(completeMarker);
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
                + ", completeMarker=0x" + String.format("%016X", completeMarker)
                + ", checksum=" + checksum
                + ", valid=" + isValid()
                + ", complete=" + isComplete()
                + '}';
    }

}
