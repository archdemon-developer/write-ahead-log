# Write-Ahead Log (WAL)

A single-node write-ahead log implementation in pure Java. Built from scratch to understand durability, atomicity, and crash recovery in database systems.

**Status:** ✅ Complete — single-node implementation in maintenance mode

## What This Is

A durable log that:
- Guarantees atomic writes with CRC32 validation
- Recovers deterministically from crashes
- Handles concurrent access safely (single virtual-thread writer, lock-free reads)
- Provides time-based queries and timestamp-based log truncation

## What This Is NOT

- Not a distributed system (single-node only)
- Not a replacement for Kafka/RocksDB
- Not a full database (just the durability layer)

## Quick Start

```bash
# Build
mvn clean compile
```

```java
WalConfiguration config = new WalConfiguration.Builder()
        .batchSize(50)
        .maxSegmentSize(10 * 1024 * 1024)
        .logDir("/var/log/wal")
        .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
        .build();

WriteAheadLog wal = new WriteAheadLog(config);
LogEntry entry = new LogEntry(data.length, data, System.currentTimeMillis());

wal.append(entry);                               // Enqueued for writer thread
wal.writeBatch();                                // Block until flushed to disk
wal.truncateBeforeTimestamp(cutoffMillis);       // Reclaim disk space

// After crash recovery...
WriteAheadLog wal2 = new WriteAheadLog(config);
List<LogEntry> recovered = wal2.readAllSegments();  // All safe on disk
```

## Architecture

```
WriteAheadLog (single virtual-thread writer, lock-free reads, LSN-based flush signaling)
    ↓
SegmentStoreManager (orchestrates rotation and state tracking)
    ├─ SegmentLifecycleManager  — creates and finalizes segment files
    ├─ SegmentWriter            — append-only writes, rotation
    ├─ SegmentReader            — reads finalized + current open segment
    ├─ SegmentTruncator         — deletes old segments, updates collection
    ├─ SegmentMetadataRecovery  — header/footer CRC validation on startup
    └─ SegmentCollection        — ordered metadata index
    ↓
Disk (.log segments: 48-byte header + entries + 36-byte footer, all with embedded CRC32)
```

## Key Design Decisions

| Decision | Why | Reference |
|----------|-----|-----------|
| Segment-based | Safe truncation, predictable file sizes | RocksDB, Kafka |
| Embedded metadata | Atomic with data, no divergence on crash | PostgreSQL XLOG |
| CRC32 per entry | ~99.99% corruption detection | PostgreSQL, RocksDB |
| Batching | 6-10x throughput vs single fsync | RocksDB, Kafka |
| Single virtual-thread writer | No lock contention on the write hot path | Kafka broker model |
| Lock-free reads | Volatile fields on SegmentWriter | Java Memory Model |
| Sequence-only filenames | Deterministic naming eliminates coordination hazards | RocksDB SST naming |
| LSN-based flush signaling | Callers block until their write is durable | PostgreSQL WAL |
| Write queue timeout | Backpressure propagated to producers after 5s | Kafka producer config |

## Current State

- ✅ Core durability layer complete
- ✅ Thread-safe concurrent writes (single writer, lock-free reads)
- ✅ Crash recovery deterministic (header/footer CRC, sequence-only filenames)
- ✅ Log truncation (timestamp-based, safe space reclamation)
- ✅ Comprehensive test suite (no mocks, no sleeps)

## Future Work

**Replication (beyond single-node scope)**
- Raft consensus
- Multi-node failover
- Leader/follower architecture

## Acknowledgements

This implementation was inspired by and learned from:

- **PostgreSQL** — XLOG design, CRC32 corruption detection, segment-based architecture, atomic metadata
- **RocksDB** — SSTable WAL design, batching strategy, async flushing, pluggable compaction policies
- **Apache Kafka** — Timestamp-based indexing, segment rotation, leader/follower architecture, segment-level optimizations
- **MySQL InnoDB** — Redo log design, crash recovery mechanics, double-write buffer concepts

## License

MIT

---

Built to understand: write-ahead logs, durability, atomicity, crash recovery, and concurrent system design.
