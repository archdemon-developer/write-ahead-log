package io.writeahead.log;

import io.writeahead.log.fileio.FileUtils2;
import io.writeahead.log.models.LogEntry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WriteAheadLog {

  private final int batchSize;
  private final List<LogEntry> batch;
  private final FileUtils2 fileUtils2;

  public WriteAheadLog(int batchSize, String logPath) throws IOException {
    this.batchSize = batchSize;
    this.batch = new ArrayList<>();
    this.fileUtils2 = new FileUtils2(logPath);
  }

  public void append(LogEntry entry) throws IOException {
    batch.add(entry);
    if (batch.size() == batchSize) {
      fileUtils2.writeAll(batch);
      batch.clear();
    }
  }

  public List<LogEntry> read() throws IOException {
    return Collections.unmodifiableList(batch);
  }

  public List<LogEntry> readAll() throws IOException {
    List<LogEntry> entries = new ArrayList<>();
    entries.addAll(fileUtils2.readAll());
    entries.addAll(batch);
    return Collections.unmodifiableList(entries);
  }

  public List<LogEntry> readFromDisk() throws IOException {
    return Collections.unmodifiableList(fileUtils2.readAll());
  }

  public List<LogEntry> readBuffer() {
    return Collections.unmodifiableList(batch);
  }


  public void close() throws IOException {
    if (!batch.isEmpty()) {
      fileUtils2.writeAll(batch);
      batch.clear();
    }
    fileUtils2.close();
  }
}
