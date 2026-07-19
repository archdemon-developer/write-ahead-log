# Write-Ahead Log (WAL)

[![codecov](https://codecov.io/github/archdemon-developer/write-ahead-log/graph/badge.svg?token=68IURPEYKH)](https://codecov.io/github/archdemon-developer/write-ahead-log)

A production-grade Write-Ahead Log implementation in pure Java. **Zero external dependencies.** Achieves **96K+ ops/sec** throughput with concurrent writes, automatic crash recovery, and structured observability—designed to learn from and mirror real systems like RocksDB and PostgreSQL.

## Features

- 💾 **Durable writes** with fsync guarantees (atomic metadata via temp-file + rename)
- ⚡ **96K+ ops/sec throughput** with batched I/O (batch size configurable)
- 🧵 **Thread-safe concurrent writes** (ReadWriteLock, no data loss under concurrent load)
- 🔄 **Automatic segment rotation** (configurable size, default 10MB)
- 🛡️ **CRC32 checksums** detect bit corruption (fail-hard semantics)
- 📊 **Timestamp-based queries** enable snapshot recovery and time-range reads
- 🔐 **Crash recovery** verified via integration tests (power-loss simulation)
- 📋 **Structured logging** (custom zero-dependency logger with timestamps + thread names)
- 🏗️ **Extensible architecture** (pluggable interfaces, dependency injection, configuration-driven)
- ✅ **90% test coverage** (150+ tests, stress tests included)

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.6.3+

### Build & Test

```bash
# Compile
mvn clean compile

# Run all tests (excludes stress tests to spare SSD)
mvn clean test -Dtest=!LoadStressTest,!ConcurrentWriteReadTest

# Run with coverage report
mvn clean verify
open target/site/jacoco/index.html
```

## Usage

```java
// Create WAL with configuration
WalConfiguration config = new WalConfiguration.Builder()
    .batchSize(10)
    .segmentSizeBytes(10 * 1024 * 1024)
    .logDir("/var/log/wal")
    .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
    .build();
WriteAheadLog wal = new WriteAheadLog(config);

// Append entries (buffered in memory)
byte[] data = "important data".getBytes();
LogEntry entry = new LogEntry(data.length, data, System.currentTimeMillis());
wal.append(entry);  // Non-blocking, returns immediately

// Flush remaining batch on close
wal.close();  // Blocks until all data fsynced

// Recover from crash
WriteAheadLog wal2 = new WriteAheadLog(config);
List<LogEntry> recovered = wal2.readFromDisk();  // All entries safe on disk
```

## Performance

**GitHub Actions (Cloud Hardware, Better SSD):**
- Light Load (2 threads, 500 entries): **32K ops/sec**
- Heavy Load (10 threads, 2K entries): **40K ops/sec**
- Extreme Load (20 threads, 5K entries): **31K ops/sec**
- Batch Size 100 (optimal): **219K ops/sec** (write-only, no fsync)

**Throughput with Fsync (realistic):**
- Batch size 10: ~15K ops/sec
- Batch size 50: ~96K ops/sec (sweet spot)

*Note: Local SSD throughput limited by hardware fsync latency (~154ms). Cloud hardware achieves better results.*

## Architecture

```
Application
    ↓
WriteAheadLog (batching, locks)
    ↓
SegmentStore [Interface]
    ↓
SegmentManager (rotation, queries)
    ↓
MetadataStore [Interface]
    ↓
MetaDataManager (atomic persistence)
    ↓
FileUtils (raw I/O, fsync)
    ↓
Disk Files (.log segments + .meta)
```

**Design Principles:**
- **Extensibility First:** Configuration + interfaces enable Phase 8 (replication) without refactoring
- **Fail-Hard Philosophy:** Corruption detected = exception thrown (no silent data loss)
- **Single Responsibility:** Each layer has one job (WriteAheadLog → SegmentManager → MetaDataManager → FileUtils)
- **Zero Dependencies:** Pure Java, no external libraries (including logging)

## Configuration

```java
WalConfiguration config = new WalConfiguration.Builder()
    .batchSize(50)                              // Entries per batch (default: 10)
    .segmentSizeBytes(100 * 1024 * 1024)        // Rotate at 100MB (default: 10MB)
    .logDir("/data/wal")                        // Directory for .log + .meta files
    .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)  // Durability level
    .timestampFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")   // Segment naming format
    .build();

// Adjust log level at runtime
LoggerFactory.setLogLevel(LogLevel.WARN);  // DEBUG, INFO, WARN, ERROR
```

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Segments** | Safe truncation, predictable file sizes, parallel reads (RocksDB pattern) |
| **JSON metadata** | Human-readable, debuggable, atomic rename prevents corruption |
| **CRC32** | Fast (~1μs per entry), ~99.99% detection rate for bit corruption |
| **Batching** | Amortizes expensive fsync calls (~6-10x throughput gain) |
| **ReadWriteLock** | Supports concurrent reads (prepared for Phase 8 replication) |
| **Custom logger** | Zero dependencies, structured output, configurable levels |
| **Immutable LogEntry** | Thread-safe by design, enables defensive programming |

## Test Coverage

- **90% line coverage** (threshold enforced in CI)
- **150+ test cases** across 7 test suites
- **Crash recovery tests** (simulated power loss via JVM exit)
- **Corruption detection tests** (bit-flip injection)
- **Concurrent stress tests** (10-20 threads, 100K+ entries)
- **Load benchmarks** (3 load profiles, throughput measurement)

View coverage report:
```bash
mvn clean verify
open target/site/jacoco/index.html
```

## Phases Completed

| Phase | Feature | Status | Details |
|-------|---------|--------|---------|
| 1 | Append-only persistence | ✅ | Basic read/write, batch buffering |
| 2 | Crash recovery | ✅ | Simulated power loss, EOFException handling |
| 3 | Fsync durability | ✅ | Batched fsync, 10x throughput vs per-entry |
| 4 | Segment rotation | ✅ | Auto-rotation, atomic metadata, timestamp tracking |
| 5 | Corruption detection | ✅ | CRC32 checksums, fail-hard semantics |
| 6 | Thread safety | ✅ | ReadWriteLock, concurrent append/read, stress tested |
| 6.5 | Optimizations + Logging | ✅ | Removed cloning, single-lock design, structured logging |

**Next Phases:**
- Phase 7: Resilience & Configuration (metadata recovery, retry policies, metrics)
- Phase 8: Replication (Raft consensus, leader election, multi-node failover)

## Inspired By

- **RocksDB** — Segment-based WAL, async flushing
- **PostgreSQL** — CRC32 corruption detection (XLOG), atomic metadata
- **Kafka** — Timestamp-based indexing, segment rotation

## License

MIT
