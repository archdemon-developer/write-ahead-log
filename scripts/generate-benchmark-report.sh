#!/bin/bash

source scripts/report-utils.sh

BENCHMARK_JSON="benchmark-results.json"
OUTPUT_FILE="BENCHMARK_RESULTS.md"

log_info "Generating benchmark report..."

require_file "$BENCHMARK_JSON"

{
  md_h1 "Benchmark Report"

  echo "Generated: $(date)"
  echo ""

  md_h2 "Summary"

  echo "This report measures 5 angles of WAL performance:"
  echo ""
  echo "1. **Producer Throughput** - append() queueing rate"
  echo "2. **Writer Drain Rate** - Background thread processing rate"
  echo "3. **Durability Barrier Latency** - writeBatch() blocking time distribution"
  echo "4. **Queue Saturation** - Performance under concurrent load"
  echo "5. **Fsync Strategy Impact** - EVERY_ENTRY vs EVERY_BATCH"
  echo ""
  echo ""

  md_h2 "Angle 1: Producer Throughput"

  producer_throughput=$(grep "ProducerThroughputBenchmark.appendEntry" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1)

  echo "**$producer_throughput ops/sec**"
  echo ""
  echo "Rate at which append() can queue entries."
  echo ""

  md_h2 "Angle 2: Writer Drain Rate"

  drain_rate=$(grep "WriterDrainRateBenchmark.appendAndFlush" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1)

  echo "**$drain_rate ops/sec**"
  echo ""
  echo "Rate at which writer thread completes writeBatch() calls."
  echo ""

  md_h2 "Angle 3: Durability Barrier Latency"

  echo "writeBatch() latency percentiles:"
  echo ""

  p50=$(grep "DurabilityBarrierLatencyBenchmark.writeBatchLatency:p0.50" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1)
  p95=$(grep "DurabilityBarrierLatencyBenchmark.writeBatchLatency:p0.95" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1)
  p99=$(grep "DurabilityBarrierLatencyBenchmark.writeBatchLatency:p0.99" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1)
  p999=$(grep "DurabilityBarrierLatencyBenchmark.writeBatchLatency:p0.999" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1)

  echo "| Percentile | Latency (µs) |"
  echo "|-----------|-------------|"
  echo "| p50 | $p50 |"
  echo "| p95 | $p95 |"
  echo "| p99 | $p99 |"
  echo "| p999 | $p999 |"
  echo ""

  md_h2 "Angle 4: Queue Saturation"

  echo "Latency under concurrent load:"
  echo ""

  single=$(grep "QueueSaturationBenchmark.singleProducer" "$BENCHMARK_JSON" | grep -v "p0\." | grep -oE '[0-9]+\.[0-9]+' | head -1)
  four=$(grep "QueueSaturationBenchmark.fourProducers" "$BENCHMARK_JSON" | grep -v "p0\." | grep -oE '[0-9]+\.[0-9]+' | head -1)
  eight=$(grep "QueueSaturationBenchmark.eightProducers" "$BENCHMARK_JSON" | grep -v "p0\." | grep -oE '[0-9]+\.[0-9]+' | head -1)
  sixteen=$(grep "QueueSaturationBenchmark.sixteenProducers" "$BENCHMARK_JSON" | grep -v "p0\." | grep -oE '[0-9]+\.[0-9]+' | head -1)

  echo "| Threads | Average Latency (µs) |"
  echo "|---------|----------------------|"
  echo "| 1 | $single |"
  echo "| 4 | $four |"
  echo "| 8 | $eight |"
  echo "| 16 | $sixteen |"
  echo ""

  md_h2 "Angle 5: Fsync Strategy Impact"

  echo "EVERY_BATCH vs EVERY_ENTRY:"
  echo ""

  batch_avg=$(grep "FsyncStrategyImpactBenchmark" "$BENCHMARK_JSON" | grep "FSYNC_EVERY_BATCH" | grep -v "p0\." | grep -oE '[0-9]+\.[0-9]+' | head -1)
  batch_p99=$(grep "FsyncStrategyImpactBenchmark.*FSYNC_EVERY_BATCH:p0.99" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1)

  entry_avg=$(grep "FsyncStrategyImpactBenchmark" "$BENCHMARK_JSON" | grep "FSYNC_EVERY_ENTRY" | grep -v "p0\." | grep -oE '[0-9]+\.[0-9]+' | head -1)
  entry_p99=$(grep "FsyncStrategyImpactBenchmark.*FSYNC_EVERY_ENTRY:p0.99" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1)

  echo "**FSYNC_EVERY_BATCH**: avg=$batch_avg µs, p99=$batch_p99 µs"
  echo ""
  echo "**FSYNC_EVERY_ENTRY**: avg=$entry_avg µs, p99=$entry_p99 µs"
  echo ""

} > "$OUTPUT_FILE"

log_success "Benchmark report generated: $OUTPUT_FILE"