# Write-Ahead Log (WAL)

[![codecov](https://codecov.io/github/archdemon-developer/write-ahead-log/graph/badge.svg?token=68IURPEYKH)](https://codecov.io/github/archdemon-developer/write-ahead-log)

A production-grade Write-Ahead Log implementation in pure Java. Guarantees durable, checksummed persistence with automatic segment rotation and crash recovery—designed to learn from and mirror real systems like RocksDB and PostgreSQL.

## Features

- 💾 Durable writes with fsync guarantees
- ⚡ Batched I/O for ~10x throughput
- 🔄 Automatic segment rotation at 10MB
- 🛡️ CRC32 checksums detect bit corruption
- 📊 Timestamp-based queries (snapshot recovery)
- 🔐 Atomic metadata via temp-file + rename
- ✅ 98% test coverage (150+ tests)

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.6.3+

### Build
```bash
mvn clean compile
```

### Run Tests
```bash
mvn clean test
```

## Usage

```java
// Create WAL
WriteAheadLog wal = new WriteAheadLog(10, "/var/log/wal");

// Append entries (buffered)
byte[] data = "important data".getBytes();
LogEntry entry = new LogEntry(
  data.length, 
  data, 
  System.currentTimeMillis()
);
wal.append(entry);

// Close flushes remaining batch
wal.close();

// Recover from crash
WriteAheadLog wal2 = new WriteAheadLog(10, "/var/log/wal");
List<LogEntry> recovered = wal2.readFromDisk();
```

## Architecture

```
Application
    ↓
WriteAheadLog (batching)
    ↓
SegmentManager (rotation + queries)
    ↓
MetaDataManager (atomic metadata)
    ↓
FileUtils (raw I/O + fsync)
    ↓
Disk Files (.log segments + .meta)
```

**Key Design:**
- **Segments:** Auto-rotate at 10MB (not in-file rewrites like RocksDB/Kafka)
- **Metadata:** JSON with atomic rename (crash-safe, no corruption)
- **CRC32:** ~99.99% detection rate for bit corruption
- **Recovery:** Flushed entries survive power loss; unflushed entries lost
- **Layers:** Each layer has single responsibility (WriteAheadLog → SegmentManager → MetaDataManager → FileUtils)

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Segments** | Safe truncation, predictable file sizes, parallel reads (RocksDB pattern) |
| **JSON metadata** | Human-readable, debuggable, atomic rename prevents corruption |
| **CRC32** | Fast (~1μs), sufficient for single-machine durability |
| **Batching** | Amortizes expensive fsync calls (~10x throughput gain) |
| **Immutable LogEntry** | Thread-safe by design, enables defensive programming |

## Test Coverage

- 98% line coverage (threshold: 90%)
- 150+ test cases across 7 test suites
- Crash recovery simulation
- Bit-flip corruption detection (integration tests)

Run with coverage report:
```bash
mvn clean verify
open target/site/jacoco/index.html
```

## Phases Completed

| Phase | Feature | Status |
|-------|---------|--------|
| 1 | Append-only persistence | ✅ |
| 2 | Crash recovery | ✅ |
| 3 | Fsync durability + batching | ✅ |
| 4 | Segment rotation + metadata | ✅ |
| 5 | CRC32 checksums | ✅ |

**Next:** Phase 6 (concurrency), Phase 7 (resilience & configuration)

## Inspired By

- **RocksDB** — Segment-based WAL architecture
- **PostgreSQL** — CRC32 corruption detection (XLOG)
- **Kafka** — Timestamp-based indexing and queries

## License

MIT