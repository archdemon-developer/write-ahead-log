package io.writeahead.log.fileio;

import io.writeahead.log.models.LogEntry;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileUtils2 {

  private final File file;
  private final FileOutputStream fileOutputStream;
  private final DataOutputStream dataOutputStream;

  public FileUtils2(String filePath) throws IOException {
    this.file = new File(filePath);
    this.fileOutputStream = new FileOutputStream(file, true);
    this.dataOutputStream = new DataOutputStream(fileOutputStream);
  }

  public void writeSingle(LogEntry logEntry) throws IOException {
    dataOutputStream.writeLong(logEntry.timestamp());
    dataOutputStream.writeInt(logEntry.size());
    dataOutputStream.write(logEntry.data());
    dataOutputStream.flush();
    fileOutputStream.getFD().sync();
  }

  public void writeAll(List<LogEntry> logEntries) throws IOException {
    for (LogEntry logEntry : logEntries) {
      dataOutputStream.writeLong(logEntry.timestamp());
      dataOutputStream.writeInt(logEntry.size());
      dataOutputStream.write(logEntry.data());
    }
    dataOutputStream.flush();
    fileOutputStream.getFD().sync();
  }

  public List<LogEntry> readAll() throws IOException {
    List<LogEntry> fileRead = new ArrayList<>();
    try (DataInputStream dataInputStream =
        new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
      while (true) {
        try {
          long timestamp = dataInputStream.readLong();
          int size = dataInputStream.readInt();
          byte[] data = new byte[size];
          dataInputStream.readFully(data);
          fileRead.add(new LogEntry(size, data, timestamp));
        } catch (EOFException ex) {
          break;
        }
      }
    }
    return Collections.unmodifiableList(fileRead);
  }

  public List<LogEntry> readAllAfterTimestamp(long timestamp) throws IOException {
    List<LogEntry> entriesAfter = new ArrayList<>();
    for(LogEntry logEntry : readAll()) {
      if(logEntry.timestamp() > timestamp) {
        entriesAfter.add(logEntry);
      }
    }
    return Collections.unmodifiableList(entriesAfter);
  }

  public void close() throws IOException {
    dataOutputStream.close();
  }
}
