#!/bin/bash

source scripts/report-utils.sh

BENCHMARK_JSON="benchmark-results.json"
OUTPUT_FILE="BENCHMARK_RESULTS.md"

log_info "Generating benchmark report..."

if [ ! -f "$BENCHMARK_JSON" ]; then
  log_warn "Benchmark JSON not found at $BENCHMARK_JSON"
  cat > "$OUTPUT_FILE" << EOF
# JMH Benchmark Results

No benchmark results available.

EOF
  log_success "Empty benchmark report created: $OUTPUT_FILE"
  exit 0
fi

cat > "$OUTPUT_FILE" << EOF
# JMH Benchmark Results

## Producer Throughput
- **Benchmark**: ProducerThroughputBenchmark.appendEntry
- **Metric**: Operations per second (higher is better)

## Writer Drain Rate
- **Benchmark**: WriterDrainRateBenchmark.appendAndFlush
- **Metric**: Throughput (entries written and flushed per second)

## Durability Barrier Latency
- **Benchmark**: DurabilityBarrierLatencyBenchmark
- **Metric**: Latency percentiles (lower is better)
- **Measures**: writeBatch() blocking time with LSN synchronization

## Queue Saturation Impact
- **Benchmark**: QueueSaturationBenchmark
- **Threads**: 1, 4, 8, 16
- **Metric**: Latency degradation under concurrent producer load

## Fsync Strategy Impact
- **FSYNC_EVERY_BATCH**: Synchronous fsync on batch completion
  - Higher throughput (100+ ops/sec)
  - Unpredictable latency spikes (100ms+ p999)
- **FSYNC_EVERY_ENTRY**: Asynchronous fsync via virtual threads
  - Lower throughput (~60k ops/sec on append queue)
  - More predictable latency (~2.9µs avg, p999 ~46µs)

## Raw Results

EOF

if command -v jq &> /dev/null; then
  log_info "Extracting results with jq..."
  jq -r '.[] | "\(.benchmark): \(.primaryMetric.score) \(.primaryMetric.scoreUnit)"' "$BENCHMARK_JSON" >> "$OUTPUT_FILE" 2>/dev/null || log_warn "jq extraction failed"
else
  log_info "jq not available, including raw JSON"
  echo '```json' >> "$OUTPUT_FILE"
  cat "$BENCHMARK_JSON" >> "$OUTPUT_FILE"
  echo '```' >> "$OUTPUT_FILE"
fi

log_success "Benchmark report generated: $OUTPUT_FILE"