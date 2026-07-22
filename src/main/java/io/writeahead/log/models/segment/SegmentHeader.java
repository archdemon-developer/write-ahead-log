package io.writeahead.log.models.segment;

import io.writeahead.log.constants.WalConstants;
import io.writeahead.log.utils.Crc32Utils;

import java.io.*;

public record SegmentHeader(
        byte magic,
        byte version,
        long createdAt,
        long segmentSequence,
        byte[] reserved,
        long checksum) {

    private static final byte MAGIC_BYTE = (byte) 0xAA;
    private static final byte VERSION = 0x01;
    private static final int HEADER_SIZE = WalConstants.SEGMENT_HEADER_SIZE;
    private static final int RESERVED_BYTE_SIZE = 22;

    public static SegmentHeader create(long createdAt, long segmentSequence) throws IOException {
        byte[] reserved = new byte[RESERVED_BYTE_SIZE];
        long checksum = calculateChecksum(MAGIC_BYTE, VERSION, createdAt, segmentSequence, reserved);
        return new SegmentHeader(MAGIC_BYTE, VERSION, createdAt, segmentSequence, reserved, checksum);
    }

    public byte[] toBytes() throws IOException {
        try(ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(HEADER_SIZE);
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
            dataOutputStream.writeByte(magic);
            dataOutputStream.writeByte(version);
            dataOutputStream.writeLong(createdAt);
            dataOutputStream.writeLong(segmentSequence);
            dataOutputStream.write(reserved);
            dataOutputStream.writeLong(checksum);
            return byteArrayOutputStream.toByteArray();
        }
    }

    public static SegmentHeader fromBytes(byte[] data) throws IOException {
        if(data.length < HEADER_SIZE) {
            throw new IOException("Header data too short: "+data.length+" < "+ HEADER_SIZE);
        }

        try(ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
        DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream)) {
            byte magic = dataInputStream.readByte();
            byte version = dataInputStream.readByte();
            long createdAt = dataInputStream.readLong();
            long segmentSequence = dataInputStream.readLong();
            byte[] reserved = new byte[RESERVED_BYTE_SIZE];
            dataInputStream.readFully(reserved);
            long checksum = dataInputStream.readLong();
            return new SegmentHeader(magic, version, createdAt, segmentSequence, reserved, checksum);
        }
    }

    public boolean isValid() {
        try {
            if (magic != MAGIC_BYTE) {
                return false;
            }

            long computedChecksum = calculateChecksum(magic, version, createdAt, segmentSequence, reserved);
            return computedChecksum == checksum;
        } catch(IOException e) {
            return false;
        }
    }

    private static long calculateChecksum(byte magic, byte version, long createdAt, long segmentSequence, byte[] reserved) throws IOException {
        ;

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
            dataOutputStream.writeByte(magic);
            dataOutputStream.writeByte(version);
            dataOutputStream.writeLong(createdAt);
            dataOutputStream.writeLong(segmentSequence);
            dataOutputStream.write(reserved);
            dataOutputStream.close();

            byte[] dataToCheck = byteArrayOutputStream.toByteArray();
            return Crc32Utils.compute(dataToCheck);
        }
    }

    @Override
    public String toString() {
        return "SegmentHeader{"
                + "magic=0x" + String.format("%02X", magic)
                + ", version=" + version
                + ", createdAt=" + createdAt
                + ", segmentSequence=" + segmentSequence
                + ", checksum=" + checksum
                + ", valid=" + isValid()
                + '}';
    }
}
