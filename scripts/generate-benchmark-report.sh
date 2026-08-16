#!/bin/bash

source scripts/report-utils.sh

BENCHMARK_JSON="benchmark-results.json"
OUTPUT_FILE="BENCHMARK_RESULTS.md"

if ! file_exists "$BENCHMARK_JSON"; then
  log_warn "benchmark-results.json not found"
  exit 0
fi

log_info "Generating benchmark report..."

{
  cat << 'EOF'
# Benchmark Report

This report measures 5 angles of WAL performance.

## Angle 1: Producer Throughput

append() queueing rate (ops/sec)

EOF

  grep "ProducerThroughputBenchmark.appendEntry" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1 | xargs -I {} echo "**{} ops/sec**"
  echo ""

  cat << 'EOF'
## Angle 2: Writer Drain Rate

Background writer thread processing rate (ops/sec)

EOF

  grep "WriterDrainRateBenchmark.appendAndFlush" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1 | xargs -I {} echo "**{} ops/sec**"
  echo ""

  cat << 'EOF'
## Angle 3: Durability Barrier Latency

writeBatch() blocking time distribution (microseconds)

| Percentile | Latency (µs) |
|-----------|-------------|
EOF

  p50=$(grep "DurabilityBarrierLatencyBenchmark.writeBatchLatency:p0.50" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1)
  p95=$(grep "DurabilityBarrierLatencyBenchmark.writeBatchLatency:p0.95" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1)
  p99=$(grep "DurabilityBarrierLatencyBenchmark.writeBatchLatency:p0.99" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1)
  p999=$(grep "DurabilityBarrierLatencyBenchmark.writeBatchLatency:p0.999" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1)

  echo "| p50 | $p50 |"
  echo "| p95 | $p95 |"
  echo "| p99 | $p99 |"
  echo "| p999 | $p999 |"
  echo ""

  cat << 'EOF'
## Angle 4: Queue Saturation

Latency under concurrent load (microseconds average)

| Threads | Latency (µs) |
|---------|-------------|
EOF

  single=$(grep "QueueSaturationBenchmark.singleProducer" "$BENCHMARK_JSON" | grep -v "p0\." | grep -oE '[0-9]+\.[0-9]+' | head -1)
  four=$(grep "QueueSaturationBenchmark.fourProducers" "$BENCHMARK_JSON" | grep -v "p0\." | grep -oE '[0-9]+\.[0-9]+' | head -1)
  eight=$(grep "QueueSaturationBenchmark.eightProducers" "$BENCHMARK_JSON" | grep -v "p0\." | grep -oE '[0-9]+\.[0-9]+' | head -1)
  sixteen=$(grep "QueueSaturationBenchmark.sixteenProducers" "$BENCHMARK_JSON" | grep -v "p0\." | grep -oE '[0-9]+\.[0-9]+' | head -1)

  echo "| 1 | $single |"
  echo "| 4 | $four |"
  echo "| 8 | $eight |"
  echo "| 16 | $sixteen |"
  echo ""

  cat << 'EOF'
## Angle 5: Fsync Strategy Impact

Latency comparison (microseconds)

### FSYNC_EVERY_BATCH
EOF

  batch_avg=$(grep "FsyncStrategyImpactBenchmark" "$BENCHMARK_JSON" | grep "FSYNC_EVERY_BATCH" | grep -v "p0\." | grep -oE '[0-9]+\.[0-9]+' | head -1)
  batch_p99=$(grep "FsyncStrategyImpactBenchmark.*FSYNC_EVERY_BATCH:p0.99" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1)

  echo "Average: $batch_avg µs"
  echo "p99: $batch_p99 µs"
  echo ""

  cat << 'EOF'
### FSYNC_EVERY_ENTRY
EOF

  entry_avg=$(grep "FsyncStrategyImpactBenchmark" "$BENCHMARK_JSON" | grep "FSYNC_EVERY_ENTRY" | grep -v "p0\." | grep -oE '[0-9]+\.[0-9]+' | head -1)
  entry_p99=$(grep "FsyncStrategyImpactBenchmark.*FSYNC_EVERY_ENTRY:p0.99" "$BENCHMARK_JSON" | grep -oE '[0-9]+\.[0-9]+' | head -1)

  echo "Average: $entry_avg µs"
  echo "p99: $entry_p99 µs"
  echo ""

} > "$OUTPUT_FILE"

log_success "Benchmark report: $OUTPUT_FILE"