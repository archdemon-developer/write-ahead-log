# Write-Ahead Log (WAL)

A single-node write-ahead log implementation in pure Java. Built from scratch to understand durability, atomicity, and crash recovery in database systems.

**Status:** ✅ Phases 1-6.75 Complete | 🔧 Refactoring in Progress

## What This Is

A durable log that:
- Guarantees atomic writes with CRC32 validation
- Recovers deterministically from crashes
- Handles concurrent access safely
- Provides time-based queries

## What This Is NOT

- Not a distributed system (single-node only)
- Not production-ready (a learning artifact)
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

wal.append(entry);           // Buffered in memory
wal.close();                 // Flushed to disk with fsync

// After crash recovery...
WriteAheadLog wal2 = new WriteAheadLog(config);
List<LogEntry> recovered = wal2.readAllSegments();  // All safe on disk
```

## Architecture

```
WriteAheadLog (thread-safe batching with ReadWriteLock)
    ↓
SegmentStore [6 focused interfaces]
    ↓
SegmentStoreManager (orchestrates rotation, state tracking)
    ├─ SegmentLifecycleManager
    ├─ SegmentEntriesReader
    ├─ SegmentMetadataRecovery
    └─ SegmentCollection
    ↓
Disk (.log segments with embedded metadata)
```

## Key Design Decisions

| Decision | Why | Reference |
|----------|-----|-----------|
| Segment-based | Safe truncation, predictable file sizes | RocksDB, Kafka |
| Embedded metadata | Atomic with data, no divergence on crash | PostgreSQL XLOG |
| CRC32 per entry | ~99.99% corruption detection | PostgreSQL, RocksDB |
| Batching | 6-10x throughput vs single fsync | RocksDB, Kafka |
| ReadWriteLock | Prepared for future replication | Kafka broker model |
| State objects | Rich encapsulation, extensibility | Modern API design |
| Pluggable filters | Custom logic, segment optimization | Kafka filters |
| Interface segregation | Clients depend only on needed methods | SOLID ISP |

## Current State

- ✅ Core durability layer complete
- ✅ Thread-safe concurrent writes
- ✅ Crash recovery deterministic
- ✅ Architecture refactored for extensibility
- 🔧 Tests being rewritten (after package segregation + refactoring)

## Future Work

### Phase 7: Refactoring & Testing (10-12 weeks)

**Package Segregation**
- Organize by responsibility: config/, models/, segments/, fsync/, batch/, metrics/, etc.

**Component Extraction**
- Split SegmentStoreManager into focused classes (SegmentWriter, SegmentReader, SegmentTruncator)

**Java 21 Practices**
- Sealed exception hierarchy with pattern matching
- Use records and modern idioms

**Comprehensive Testing**
- Unit, integration, failure scenario, and stress tests
- Rewritten test suite for new package structure

**Observability**
- Structured logging
- JMH benchmarks (throughput, latency, memory)

**Bug Fixes**
- Truncation atomicity
- Schema versioning
- Backpressure on batch buffer
- Improved retry logic

### Future: Replication (After Phase 7)

- Raft consensus
- Multi-node failover
- No refactoring of current architecture needed

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