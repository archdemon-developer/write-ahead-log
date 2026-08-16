package io.writeahead.log.benchmarks;

import io.writeahead.log.models.LogEntry;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
    value = 3,
    jvmArgs = {"-Xmx2g", "-XX:+UseG1GC"})
public class ProducerThroughputBenchmark extends WalBenchmarkBase {

  private static final byte[] PAYLOAD = new byte[100];

  @Benchmark
  public void appendEntry() throws Exception {
    wal.append(new LogEntry(PAYLOAD.length, PAYLOAD, System.nanoTime()));
  }
}
