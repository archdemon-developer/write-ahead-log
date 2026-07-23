//package io.writeahead.log;
//
//import io.writeahead.log.concurrency.LockableOperation;
//import io.writeahead.log.logging.Logger;
//import io.writeahead.log.logging.LoggerFactory;
//import io.writeahead.log.metrics.WalMetrics;
//import io.writeahead.log.metrics.WalPerformanceMetrics;
//import io.writeahead.log.models.LogEntry;
//import io.writeahead.log.models.wal.WalConfiguration;
//import io.writeahead.log.storage.SegmentStore;
//import io.writeahead.log.storage.SegmentStoreManager;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//
//public class WriteAheadLog {
//
//  private final int batchSize;
//  private final List<LogEntry> batch;
//  private final SegmentStore segmentStore;
//  private final LockableOperation appendLock;
//
//  private static final Logger log = LoggerFactory.getLogger(WriteAheadLog.class);
//
//  public WriteAheadLog(WalConfiguration walConfiguration) throws IOException {
//    this.batchSize = walConfiguration.batchSize();
//    this.batch = new ArrayList<>();
//    this.segmentStore = new SegmentStoreManager(walConfiguration);
//    this.appendLock = new LockableOperation();
//
//    log.info("WriteAheadLog initialized: batchSize={}, logDir={}", batchSize, walConfiguration.logDir());
//  }
//
//  public void append(LogEntry entry) throws IOException {
//    appendLock.executeWithWriteLock(
//        () -> {
//          batch.add(entry);
//          if (batch.size() == batchSize) {
//            log.debug("Flushing batch: {} entries", batch.size());
//            segmentStore.writeBatch(batch);
//            batch.clear();
//          }
//          return null;
//        });
//  }
//
//  public List<LogEntry> read() {
//    try {
//      return appendLock.executeWithReadLock(() -> Collections.unmodifiableList(batch));
//    } catch (IOException ex) {
//      throw new RuntimeException(ex);
//    }
//  }
//
//  public List<LogEntry> readAll() throws IOException {
//    return appendLock.executeWithReadLock(
//        () -> {
//          List<LogEntry> entries = new ArrayList<>();
//          entries.addAll(segmentStore.readAllSegments());
//          entries.addAll(batch);
//          return Collections.unmodifiableList(entries);
//        });
//  }
//
//  public List<LogEntry> readFromDisk() throws IOException {
//    return appendLock.executeWithReadLock(segmentStore::readAllSegments);
//  }
//
//  public List<LogEntry> readBuffer() {
//    try {
//      return appendLock.executeWithReadLock(() -> Collections.unmodifiableList(batch));
//    } catch (IOException e) {
//      throw new RuntimeException(e);
//    }
//  }
//
//  public List<LogEntry> readAllAfterTimestamp(long timestamp) throws IOException {
//    return appendLock.executeWithReadLock(() -> segmentStore.readAllAfterTimestamp(timestamp));
//  }
//
//  public void truncateBeforeTimestamp(long timestamp) throws IOException {
//    appendLock.executeWithWriteLock(
//        () -> {
//          segmentStore.truncateBeforeTimestamp(timestamp);
//          return null;
//        });
//  }
//
//  public void close() throws IOException {
//    appendLock.executeWithWriteLock(
//        () -> {
//          if (!batch.isEmpty()) {
//            log.info("Flushing remaining batch on close: {} entries", batch.size());
//            segmentStore.writeBatch(batch);
//            batch.clear();
//          }
//          segmentStore.close();
//          log.info("WriteAheadLog closed");
//          return null;
//        });
//  }
//
//    public WalMetrics getMetrics() {
//        return segmentStore.getMetrics();
//    }
//
//    public WalPerformanceMetrics getPerformanceMetrics() {
//        return segmentStore.getMetrics();
//    }
//}
