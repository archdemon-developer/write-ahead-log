package io.writeahead.log.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IntegrationTestMetrics {

  private final List<Long> appendLatenciesNs = new ArrayList<>();
  private final List<Long> batchLatenciesNs = new ArrayList<>();
  private final List<Long> readLatenciesNs = new ArrayList<>();
  private final List<Long> recoveryLatenciesNs = new ArrayList<>();

  public void recordAppendLatency(long nanos) {
    appendLatenciesNs.add(nanos);
  }

  public void recordBatchLatency(long nanos) {
    batchLatenciesNs.add(nanos);
  }

  public void recordReadLatency(long nanos) {
    readLatenciesNs.add(nanos);
  }

  public void recordRecoveryLatency(long nanos) {
    recoveryLatenciesNs.add(nanos);
  }

  public double getAppendP50LatencyUs() {
    return percentileUs(appendLatenciesNs, 50);
  }

  public double getAppendP99LatencyUs() {
    return percentileUs(appendLatenciesNs, 99);
  }

  public double getAppendP999LatencyUs() {
    return percentileUs(appendLatenciesNs, 999);
  }

  public double getBatchP50LatencyUs() {
    return percentileUs(batchLatenciesNs, 50);
  }

  public double getBatchP99LatencyUs() {
    return percentileUs(batchLatenciesNs, 99);
  }

  public double getReadP50LatencyUs() {
    return percentileUs(readLatenciesNs, 50);
  }

  public double getReadP99LatencyUs() {
    return percentileUs(readLatenciesNs, 99);
  }

  public double getAverageAppendLatencyUs() {
    if (appendLatenciesNs.isEmpty()) return 0;
    long sum = appendLatenciesNs.stream().mapToLong(Long::longValue).sum();
    return (sum / 1000.0) / appendLatenciesNs.size();
  }

  public double getAverageBatchLatencyUs() {
    if (batchLatenciesNs.isEmpty()) return 0;
    long sum = batchLatenciesNs.stream().mapToLong(Long::longValue).sum();
    return (sum / 1000.0) / batchLatenciesNs.size();
  }

  public double getAppendThroughput(int totalEntries, long durationNs) {
    if (durationNs == 0) return 0;
    return (totalEntries * 1_000_000_000.0) / durationNs;
  }

  public long getRecoveryDurationMs(long recoveryNs) {
    return recoveryNs / 1_000_000;
  }

  public int getAppendCount() {
    return appendLatenciesNs.size();
  }

  public int getBatchCount() {
    return batchLatenciesNs.size();
  }

  public int getReadCount() {
    return readLatenciesNs.size();
  }

  public int getRecoveryCount() {
    return recoveryLatenciesNs.size();
  }

  public void printSummary() {
    System.out.println("\n=== Integration Test Metrics Summary ===");

    if (!appendLatenciesNs.isEmpty()) {
      System.out.println(
          "Append Latency: "
              + "p50="
              + formatUs(getAppendP50LatencyUs())
              + ", p99="
              + formatUs(getAppendP99LatencyUs())
              + ", avg="
              + formatUs(getAverageAppendLatencyUs()));
    }

    if (!batchLatenciesNs.isEmpty()) {
      System.out.println(
          "Batch Latency: "
              + "p50="
              + formatUs(getBatchP50LatencyUs())
              + ", p99="
              + formatUs(getBatchP99LatencyUs())
              + ", avg="
              + formatUs(getAverageBatchLatencyUs()));
    }

    if (!readLatenciesNs.isEmpty()) {
      System.out.println(
          "Read Latency: "
              + "p50="
              + formatUs(getReadP50LatencyUs())
              + ", p99="
              + formatUs(getReadP99LatencyUs()));
    }

    if (!recoveryLatenciesNs.isEmpty()) {
      System.out.println(
          "Recovery: "
              + recoveryLatenciesNs.size()
              + " operations, "
              + getRecoveryDurationMs(recoveryLatenciesNs.get(0))
              + "ms");
    }

    System.out.println("========================================\n");
  }

  private double percentileUs(List<Long> values, int percentile) {
    if (values.isEmpty()) return 0;

    List<Long> sorted = new ArrayList<>(values);
    Collections.sort(sorted);

    int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
    if (index < 0) index = 0;
    if (index >= sorted.size()) index = sorted.size() - 1;

    return sorted.get(index) / 1000.0;
  }

  private String formatUs(double latencyUs) {
    return String.format("%.2fμs", latencyUs);
  }

  public void reset() {
    appendLatenciesNs.clear();
    batchLatenciesNs.clear();
    readLatenciesNs.clear();
    recoveryLatenciesNs.clear();
  }
}
