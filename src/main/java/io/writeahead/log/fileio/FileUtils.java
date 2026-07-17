package io.writeahead.log.fileio;

import io.writeahead.log.models.LogEntry;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {

  private final File file;
  private final FileOutputStream fileOutputStream;
  private final DataOutputStream dataOutputStream;

  public FileUtils(String filePath) throws IOException {
    this.file = new File(filePath);
    this.fileOutputStream = new FileOutputStream(file, true);
    this.dataOutputStream = new DataOutputStream(fileOutputStream);
  }

  public void writeSingle(LogEntry logEntry) throws IOException {
    dataOutputStream.writeInt(logEntry.getSize());
    dataOutputStream.write(logEntry.getData());
    dataOutputStream.flush();
    fileOutputStream.getFD().sync();
  }

  public void writeAll(List<LogEntry> logEntries) throws IOException {
    for (LogEntry logEntry : logEntries) {
      dataOutputStream.writeInt(logEntry.getSize());
      dataOutputStream.write(logEntry.getData());
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
          int size = dataInputStream.readInt();
          byte[] data = new byte[size];
          dataInputStream.readFully(data);
          fileRead.add(new LogEntry(size, data));
        } catch (EOFException ex) {
          break;
        }
      }
    }
    return fileRead;
  }

  public void close() throws IOException {
    dataOutputStream.close();
  }
}
