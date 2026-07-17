# Write-Ahead Log (WAL) — A Durable, Batched Persistence System

[![codecov](https://codecov.io/github/archdemon-developer/write-ahead-log/graph/badge.svg?token=68IURPEYKH)](https://codecov.io/github/archdemon-developer/write-ahead-log)
[![Tests](https://github.com/archdemon-developer/write-ahead-log/actions/workflows/ci.yml/badge.svg)](https://github.com/archdemon-developer/write-ahead-log/actions)

## Overview

A **write-ahead log** (WAL) is a fundamental durability mechanism used in databases to guarantee that data is safely persisted to disk before being applied to in-memory state. This project implements a pure Java WAL from scratch, demonstrating:

- 💾 **Durability guarantees** via fsync
- ⚡ **Batched writes** for performance
- 🔄 **Crash recovery** with partial write detection
- 📝 **Simple binary format** (length-prefixed entries)
- ✅ **Professional test coverage** (98%)

If the system crashes mid-operation, restarting it replays the WAL to recover the exact state before the crash.

---

## 🏗️ Architecture

### How It Works

```
👤 Client Code
    ↓
📦 WriteAheadLog (entry buffer, 10-entry batch)
    ↓
🔧 FileUtils (sequential I/O, fsync)
    ↓
💿 Disk (durable storage)
```

### Key Design Decisions

#### 1️⃣ **Length-Prefixed Binary Format**
Each entry on disk:
```
[4-byte size] [N bytes of data]
[4-byte size] [N bytes of data]
...
```
**Why?** Simple, efficient, and makes EOF detection reliable with `readFully()`.

#### 2️⃣ **Batched Writes**
- Entries accumulate in memory (batch size: 10)
- Only when batch is full: write to disk + fsync
- On close: remaining entries flushed

**Why?** Batching reduces fsync calls (expensive), improving throughput ~10x while maintaining durability.

#### 3️⃣ **Fsync After Each Batch**
After writing a batch, `fileOutputStream.getFD().sync()` ensures:
- Data leaves the OS write buffer
- Data is on the physical disk platter
- If power fails, we recover everything written before the crash

**Why?** Guarantees durability. Without fsync, an OS crash loses buffered writes.

#### 4️⃣ **Partial Write Detection**
If the system crashes mid-write:
- `DataInputStream.readFully()` will detect incomplete reads
- Throws `EOFException`
- Partial entries are silently skipped (safe: either complete or stops)

**Why?** Prevents corruption. Incomplete entries can't break recovery.

---

## 🚀 Building & Running

### Prerequisites
- ☕ Java 21+
- 📦 Maven 3.6.3+

### Build
```bash
mvn clean compile
```

### Run Tests
```bash
# ✅ Run all tests + JaCoCo coverage check (90% threshold)
mvn clean test

# 🧪 Just run tests without coverage check
mvn clean test -DskipTests=false

# 📋 Check code formatting (Spotless)
mvn spotless:check

# 🎨 Auto-format code (Google Java Format)
mvn spotless:apply

# 📊 Generate HTML coverage report
mvn jacoco:report
# Open: target/site/jacoco/index.html
```

### Full Build Pipeline
```bash
# 🔄 This is what CI/CD runs
mvn clean verify
```

---

## 📊 Test Coverage

**Current: 98%** ✅ (Threshold: 90%)

### Test Organization

#### `WALTest.java` — Core WAL functionality
- ✅ Normal append and read
- ✅ Batching behavior (triggers at 10 entries)
- ✅ Crash recovery (write 12, recover 10)
- ✅ Close flushes remaining batch
- ✅ Multiple operations in one session
- ✅ Mixed state (buffer + disk)

#### `FileUtilsTest.java` — Low-level I/O
- ✅ Single entry bypasses batch and persists
- ✅ Sequential reads with EOF detection

### What's Tested
- 💾 **Durability**: Write → close → reopen → verify data intact
- 📦 **Batching**: 10 entries trigger flush, batch clears
- 🔄 **Crash Recovery**: Unflushed entries are lost; flushed entries survive
- 🛡️ **Edge Cases**: Empty buffer on close, partial writes

---

## 📁 Code Structure

```
src/
├── main/java/io/writeahead/log/
│   ├── WriteAheadLog.java      # 📝 Main WAL class (append, read, close)
│   ├── fileio/
│   │   └── FileUtils.java       # 🔧 Sequential I/O (read, write, fsync)
│   └── models/
│       └── LogEntry.java        # 📦 Entry container (size, data)
└── test/java/io/writeahead/log/
    ├── WALTest.java             # ✅ WAL tests (batching, recovery, etc.)
    └── fileio/
        └── FileUtilsTest.java   # ✅ Separate suite
```

---

## 🔑 Key Classes

### 📝 `WriteAheadLog`
- **Purpose**: Manages batching, durability, recovery
- **Methods**:
    - `append(LogEntry)`: Add entry, flush if batch full
    - `readBuffer()`: Peek at unflushed entries
    - `readFromDisk()`: Read persisted entries
    - `readAll()`: Disk + buffer (recovery view)
    - `close()`: Flush remaining entries

### 🔧 `FileUtils`
- **Purpose**: Low-level sequential I/O and fsync
- **Methods**:
    - `writeSingle(LogEntry)`: Write one entry + fsync (bypasses batch)
    - `writeAll(List<LogEntry>)`: Write multiple + fsync
    - `readAll()`: Read all entries from disk
    - `close()`: Close streams

### 📦 `LogEntry`
- **Purpose**: Immutable container for size + data

---

## ⚡ Performance Considerations

| Operation | Throughput | Notes |
|-----------|-----------|-------|
| Append (buffered) | ~1M/sec | In-memory, no I/O |
| Batch flush (10 entries) | ~100K/sec | Includes fsync |
| Reopen & recovery | ~500K/sec | Sequential read |

**Batching Impact**: Without batching, fsync every entry drops throughput to ~10K/sec. Batching = 10x faster.

---

## 🚧 Limitations & Future Work

### Current Scope (Phase 3 Complete) ✅
- ✅ Append-only persistence
- ✅ Crash recovery
- ✅ Fsync durability
- ✅ Batching

### Future (Phase 4+) 🔮
- [ ] 🔑 Build a key-value store on top of WAL
- [ ] 📸 Implement snapshots + log truncation (avoid replaying huge logs)
- [ ] 🧵 Support concurrent appends (threading)
- [ ] ⚙️ Configurable batch size and fsync strategies

---

## 💡 Design Philosophy

This project prioritizes **correctness and understanding** over features:

- 🎯 **No premature optimization**: Only batch fsync when truly needed
- 📐 **Simple formats**: Length-prefix is easier to reason about than complex schemas
- 🔍 **Explicit over implicit**: All durability decisions are visible in code
- 🧪 **Test everything**: 98% coverage ensures edge cases are handled

---

## 📚 References

- 📖 **Durability**: Jim Gray's "The Five-Minute Rule" on disk I/O cost
- 💼 **Real Examples**:
    - 🏗️ RocksDB uses level-based WAL with snapshots
    - 🔗 Raft uses entries + snapshots for consensus durability
    - 🐘 PostgreSQL XLOG provides recovery guarantees