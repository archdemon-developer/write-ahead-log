package io.writeahead.log.models;


public record LogEntry(int size, byte[] data, long timestamp) {

  public LogEntry(int size, byte[] data, long timestamp) {
    this.size = size;
    this.data = data.clone();
    this.timestamp = timestamp;
  }

  @Override
  public int size() {
    return size;
  }


  @Override
  public byte[] data() {
    return data.clone();
  }

  @Override
  public long timestamp() {
    return timestamp;
  }
}
