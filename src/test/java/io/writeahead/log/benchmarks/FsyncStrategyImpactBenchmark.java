package io.writeahead.log.benchmarks;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.enums.strategies.RotationPolicyType;
import io.writeahead.log.models.LogEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
    value = 3,
    jvmArgs = {"-Xmx2g", "-XX:+UseG1GC"})
public class FsyncStrategyImpactBenchmark {

  private static final byte[] PAYLOAD = new byte[100];

  @Param({"FSYNC_EVERY_BATCH", "FSYNC_EVERY_ENTRY"})
  public String strategy;

  private WriteAheadLog wal;
  private Path tempDir;

  @Setup
  public void setup() throws IOException {
    tempDir = Files.createTempDirectory("wal-fsync-benchmark-");

    FsyncStrategy fsyncStrategy = FsyncStrategy.valueOf(strategy);
    int batchSize = strategy.equals("FSYNC_EVERY_ENTRY") ? 1 : 100;

    WalConfiguration config =
        new WalConfiguration.Builder()
            .logDir(tempDir.toString())
            .batchSize(batchSize)
            .maxSegmentSize(50 * 1024 * 1024)
            .fsyncStrategy(fsyncStrategy)
            .rotationPolicyType(RotationPolicyType.SIZE_BASED)
            .build();
    wal = new WriteAheadLog(config);
  }

  @TearDown
  public void teardown() throws IOException {
    if (wal != null) {
      wal.close();
    }
    if (tempDir != null) {
      deleteRecursive(tempDir.toFile());
    }
  }

  @Benchmark
  public void appendWithFsyncStrategy() throws Exception {
    if (strategy.equals("FSYNC_EVERY_ENTRY")) {
      wal.append(new LogEntry(PAYLOAD.length, PAYLOAD, System.nanoTime()));
    } else {
      for (int i = 0; i < 100; i++) {
        wal.append(new LogEntry(PAYLOAD.length, PAYLOAD, System.nanoTime()));
      }
      wal.writeBatch();
    }
  }

  private static void deleteRecursive(java.io.File file) {
    if (file.isDirectory()) {
      java.io.File[] files = file.listFiles();
      if (files != null) {
        for (java.io.File child : files) {
          deleteRecursive(child);
        }
      }
    }
    file.delete();
  }
}
