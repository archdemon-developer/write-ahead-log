package io.writeahead.log.benchmarks;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkRunner {

    @Test
    void runBenchmarks() throws Exception {
        Options opt = new OptionsBuilder()
                .include(".*Benchmark.*")
                .warmupIterations(3)
                .measurementIterations(3)
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}