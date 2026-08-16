package io.writeahead.log.benchmarks;

import io.writeahead.log.WriteAheadLog;
import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.enums.strategies.FsyncStrategy;
import io.writeahead.log.enums.strategies.RotationPolicyType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class WalBenchmarkBase {

    protected WriteAheadLog wal;
    protected Path tempDir;

    @Setup
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("wal-benchmark-");
        WalConfiguration config =
                new WalConfiguration.Builder()
                        .logDir(tempDir.toString())
                        .batchSize(100)
                        .maxSegmentSize(50 * 1024 * 1024)
                        .fsyncStrategy(FsyncStrategy.FSYNC_EVERY_BATCH)
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

    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}