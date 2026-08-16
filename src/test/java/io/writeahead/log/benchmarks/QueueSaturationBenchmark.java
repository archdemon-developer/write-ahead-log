package io.writeahead.log.benchmarks;

import io.writeahead.log.models.LogEntry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgs = {"-Xmx2g", "-XX:+UseG1GC"})
public class QueueSaturationBenchmark extends WalBenchmarkBase {

    private static final byte[] PAYLOAD = new byte[100];

    @Benchmark
    @Threads(1)
    public void singleProducer() throws Exception {
        wal.append(new LogEntry(PAYLOAD.length, PAYLOAD, System.nanoTime()));
    }

    @Benchmark
    @Threads(4)
    public void fourProducers() throws Exception {
        wal.append(new LogEntry(PAYLOAD.length, PAYLOAD, System.nanoTime()));
    }

    @Benchmark
    @Threads(8)
    public void eightProducers() throws Exception {
        wal.append(new LogEntry(PAYLOAD.length, PAYLOAD, System.nanoTime()));
    }

    @Benchmark
    @Threads(16)
    public void sixteenProducers() throws Exception {
        wal.append(new LogEntry(PAYLOAD.length, PAYLOAD, System.nanoTime()));
    }
}