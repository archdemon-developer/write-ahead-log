package io.writeahead.log.segments;

import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.models.LogEntry;
import io.writeahead.log.serdes.EntrySerdes;
import io.writeahead.log.utils.Crc32Utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class SegmentEntriesReader {

    private final Logger log = LoggerFactory.getLogger(SegmentEntriesReader.class);

    public SegmentReadResult readEntriesFromRegion(byte[] entryRegionBytes) throws IOException {
        List<LogEntry> logEntries = new ArrayList<>();
        int entriesRead = 0;
        boolean hasCorruption = false;
        int corruptionAtEntry = -1;

        try (ByteArrayInputStream bais = new ByteArrayInputStream(entryRegionBytes);
             DataInputStream dis = new DataInputStream(bais)) {
            while (bais.available() > 0) {
                try {
                    Object[] entry = EntrySerdes.deserializeEntry(dis);
                    long timestamp = (long) entry[0];
                    int size = (int) entry[1];
                    byte[] data = (byte[]) entry[2];
                    long storedCrc = (long) entry[3];

                    long computedCrc = Crc32Utils.computeEntryCrc(timestamp, size, data);

                    if (computedCrc != storedCrc) {
                        log.error("Entry corruption at entry {}: computed CRC {} != stored CRC {}",
                                entriesRead, computedCrc, storedCrc);
                        hasCorruption = true;
                        corruptionAtEntry = entriesRead;
                        break;
                    }

                    logEntries.add(new LogEntry(size, data, timestamp));
                    entriesRead++;
                } catch (EOFException ex) {
                    log.debug("Reached end of entry region");
                    break;
                }
            }
        }

        return new SegmentReadResult(logEntries, entriesRead, hasCorruption, corruptionAtEntry);
    }

    public record SegmentReadResult(
            List<LogEntry> entries,
            int entriesRead,
            boolean hasCorruption,
            int corruptionAtEntry) {

        public boolean isValid() {
            return !hasCorruption;
        }
    }
}
