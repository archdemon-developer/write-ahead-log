#!/bin/bash

source scripts/report-utils.sh

BENCHMARK_JSON="benchmark-results.json"
OUTPUT_FILE="BENCHMARK_RESULTS.md"

if ! file_exists "$BENCHMARK_JSON"; then
  log_warn "benchmark-results.json not found"
  exit 0
fi

log_info "Generating benchmark report..."

# Use jq to extract values from JSON array
{
  cat << 'EOF'
# Benchmark Report

This report measures 5 angles of WAL performance.

## Angle 1: Producer Throughput

append() queueing rate (ops/sec)

EOF

  jq -r '.[0] | select(.benchmark | contains("ProducerThroughputBenchmark")) | .primaryMetric.score' "$BENCHMARK_JSON" | xargs -I {} echo "**{} ops/sec**"
  echo ""

  cat << 'EOF'
## Angle 2: Writer Drain Rate

Background writer thread processing rate (ops/sec)

EOF

  jq -r '.[1] | .primaryMetric.score' "$BENCHMARK_JSON" 2>/dev/null | xargs -I {} echo "**{} ops/sec**" || echo "**N/A**"
  echo ""

  cat << 'EOF'
## Angle 3: Durability Barrier Latency

writeBatch() blocking time distribution (microseconds)

| Percentile | Latency (µs) |
|-----------|-------------|
EOF

  jq -r '.[2] | .primaryMetric.scorePercentiles | "| p50 | \(.["50.0"]) |\n| p95 | \(.["95.0"]) |\n| p99 | \(.["99.0"]) |\n| p999 | \(.["99.9"]) |"' "$BENCHMARK_JSON" 2>/dev/null || echo "| - | - |"
  echo ""

} > "$OUTPUT_FILE"

log_success "Benchmark report: $OUTPUT_FILE"