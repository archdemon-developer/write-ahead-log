package io.writeahead.log.fsync.factory;

import static org.junit.jupiter.api.Assertions.*;

import io.writeahead.log.config.WalConfiguration;
import io.writeahead.log.fsync.retryers.ExponentialBackoffRetryStrategy;
import io.writeahead.log.fsync.retryers.FsyncRetryStrategy;
import io.writeahead.log.fsync.retryers.FsyncRetryStrategyFactory;
import io.writeahead.log.metrics.SimpleWalMetrics;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FsyncRetryStrategyFactoryTest {

  private Path tempDirectory;
  private SimpleWalMetrics walMetrics;

  @BeforeEach
  void setUp() throws Exception {
    tempDirectory = Files.createTempDirectory("fsync-retry-factory-test-");
    walMetrics = new SimpleWalMetrics();
  }

  @AfterEach
  void tearDown() throws Exception {
    Files.walk(tempDirectory)
        .sorted((a, b) -> b.compareTo(a))
        .forEach(
            path -> {
              try {
                Files.delete(path);
              } catch (Exception ignored) {
              }
            });
  }

  @Test
  void createWithDefaultConfigReturnsExponentialBackoffRetryStrategy() {
    WalConfiguration walConfiguration =
        new WalConfiguration.Builder().logDir(tempDirectory.toString()).build();

    FsyncRetryStrategy createdRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfiguration, walMetrics);

    assertNotNull(createdRetryStrategy);
    assertInstanceOf(ExponentialBackoffRetryStrategy.class, createdRetryStrategy);
  }

  @Test
  void createReturnsFsyncRetryStrategyInterface() {
    WalConfiguration walConfiguration =
        new WalConfiguration.Builder().logDir(tempDirectory.toString()).build();

    FsyncRetryStrategy createdRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfiguration, walMetrics);

    assertInstanceOf(FsyncRetryStrategy.class, createdRetryStrategy);
  }

  @Test
  void createWithCustomMaxRetriesPassesValueCorrectly() {
    int customMaxRetries = 10;
    WalConfiguration walConfiguration =
        new WalConfiguration.Builder()
            .logDir(tempDirectory.toString())
            .maxRetries(customMaxRetries)
            .build();

    FsyncRetryStrategy createdRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfiguration, walMetrics);

    assertNotNull(createdRetryStrategy);
    assertInstanceOf(ExponentialBackoffRetryStrategy.class, createdRetryStrategy);
  }

  @Test
  void createWithCustomRetryBackoffMsPassesValueCorrectly() {
    long customBackoffMs = 50;
    WalConfiguration walConfiguration =
        new WalConfiguration.Builder()
            .logDir(tempDirectory.toString())
            .retryBackoffMs(customBackoffMs)
            .build();

    FsyncRetryStrategy createdRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfiguration, walMetrics);

    assertNotNull(createdRetryStrategy);
    assertInstanceOf(ExponentialBackoffRetryStrategy.class, createdRetryStrategy);
  }

  @Test
  void createWithCustomRetryBackoffMultiplierPassesValueCorrectly() {
    double customMultiplier = 2.5;
    WalConfiguration walConfiguration =
        new WalConfiguration.Builder()
            .logDir(tempDirectory.toString())
            .retryBackoffMultiplier(customMultiplier)
            .build();

    FsyncRetryStrategy createdRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfiguration, walMetrics);

    assertNotNull(createdRetryStrategy);
    assertInstanceOf(ExponentialBackoffRetryStrategy.class, createdRetryStrategy);
  }

  @Test
  void createWithAllCustomRetryValuesPassesCorrectly() {
    int customMaxRetries = 5;
    long customBackoffMs = 25;
    double customMultiplier = 3.0;

    WalConfiguration walConfiguration =
        new WalConfiguration.Builder()
            .logDir(tempDirectory.toString())
            .maxRetries(customMaxRetries)
            .retryBackoffMs(customBackoffMs)
            .retryBackoffMultiplier(customMultiplier)
            .build();

    FsyncRetryStrategy createdRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfiguration, walMetrics);

    assertNotNull(createdRetryStrategy);
    assertInstanceOf(ExponentialBackoffRetryStrategy.class, createdRetryStrategy);
  }

  @Test
  void createWithZeroMaxRetriesReturnStrategy() {
    WalConfiguration walConfiguration =
        new WalConfiguration.Builder().logDir(tempDirectory.toString()).maxRetries(0).build();

    FsyncRetryStrategy createdRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfiguration, walMetrics);

    assertNotNull(createdRetryStrategy);
    assertInstanceOf(ExponentialBackoffRetryStrategy.class, createdRetryStrategy);
  }

  @Test
  void createWithLargeMaxRetriesReturnStrategy() {
    WalConfiguration walConfiguration =
        new WalConfiguration.Builder().logDir(tempDirectory.toString()).maxRetries(100).build();

    FsyncRetryStrategy createdRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfiguration, walMetrics);

    assertNotNull(createdRetryStrategy);
  }

  @Test
  void createWithMinimalBackoffMsReturnStrategy() {
    WalConfiguration walConfiguration =
        new WalConfiguration.Builder().logDir(tempDirectory.toString()).retryBackoffMs(1).build();

    FsyncRetryStrategy createdRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfiguration, walMetrics);

    assertNotNull(createdRetryStrategy);
  }

  @Test
  void createWithLargeBackoffMsReturnStrategy() {
    WalConfiguration walConfiguration =
        new WalConfiguration.Builder()
            .logDir(tempDirectory.toString())
            .retryBackoffMs(10000)
            .build();

    FsyncRetryStrategy createdRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfiguration, walMetrics);

    assertNotNull(createdRetryStrategy);
  }

  @Test
  void createMultipleCallsSameConfigReturnsDifferentInstances() {
    WalConfiguration walConfiguration =
        new WalConfiguration.Builder().logDir(tempDirectory.toString()).build();

    FsyncRetryStrategy firstCreatedRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfiguration, walMetrics);
    FsyncRetryStrategy secondCreatedRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfiguration, walMetrics);

    assertNotSame(firstCreatedRetryStrategy, secondCreatedRetryStrategy);
  }

  @Test
  void createMultipleCallsDifferentConfigsReturnDifferentInstances() {
    WalConfiguration walConfigurationWithThreeRetries =
        new WalConfiguration.Builder().logDir(tempDirectory.toString()).maxRetries(3).build();

    WalConfiguration walConfigurationWithTenRetries =
        new WalConfiguration.Builder().logDir(tempDirectory.toString()).maxRetries(10).build();

    FsyncRetryStrategy firstCreatedRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfigurationWithThreeRetries, walMetrics);
    FsyncRetryStrategy secondCreatedRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfigurationWithTenRetries, walMetrics);

    assertNotSame(firstCreatedRetryStrategy, secondCreatedRetryStrategy);
  }

  @Test
  void createWithDifferentMetricsInstancesReturnStrategy() {
    WalConfiguration walConfiguration =
        new WalConfiguration.Builder().logDir(tempDirectory.toString()).build();

    SimpleWalMetrics firstMetricsInstance = new SimpleWalMetrics();
    SimpleWalMetrics secondMetricsInstance = new SimpleWalMetrics();

    FsyncRetryStrategy firstCreatedRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfiguration, firstMetricsInstance);
    FsyncRetryStrategy secondCreatedRetryStrategy =
        FsyncRetryStrategyFactory.create(walConfiguration, secondMetricsInstance);

    assertNotNull(firstCreatedRetryStrategy);
    assertNotNull(secondCreatedRetryStrategy);
  }
}
