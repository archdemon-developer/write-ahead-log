package io.writeahead.log;

import io.writeahead.log.fileio.FileUtils;
import io.writeahead.log.models.LogEntry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WriteAheadLog {

  private final int batchSize;
  private final List<LogEntry> batch;
  private final FileUtils fileUtils;

  public WriteAheadLog(int batchSize, String logPath) throws IOException {
    this.batchSize = batchSize;
    this.batch = new ArrayList<>();
    this.fileUtils = new FileUtils(logPath);
  }

  public void append(LogEntry entry) throws IOException {
    batch.add(entry);
    if (batch.size() == batchSize) {
      fileUtils.writeAll(batch);
      batch.clear();
    }
  }

  public List<LogEntry> read() throws IOException {
    return batch;
  }

  public List<LogEntry> readAll() throws IOException {
    List<LogEntry> entries = new ArrayList<>();
    entries.addAll(fileUtils.readAll());
    entries.addAll(batch);
    return entries;
  }

  public List<LogEntry> readFromDisk() throws IOException {
    return fileUtils.readAll();
  }

  public List<LogEntry> readBuffer() {
    return batch;
  }

  public void close() throws IOException {
    if (!batch.isEmpty()) {
      fileUtils.writeAll(batch);
      batch.clear();
    }
    fileUtils.close();
  }
}
