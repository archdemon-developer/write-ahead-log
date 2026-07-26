package io.writeahead.log.fsync;

public enum FsyncStrategy {
  FSYNC_EVERY_BATCH,
  FSYNC_EVERY_ENTRY
}
