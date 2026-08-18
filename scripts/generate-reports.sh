#!/bin/bash

set -e

chmod +x scripts/*.sh

echo "Run chmod +x scripts/*.sh"

source scripts/report-utils.sh

log_info "Starting report generation pipeline..."

log_info "Generating benchmark report..."
bash scripts/generate-benchmark-report.sh
if [ $? -eq 0 ]; then
  log_success "Benchmark report: BENCHMARK_RESULTS.md"
else
  log_warn "Benchmark report generation failed, continuing..."
fi

log_info "Generating JaCoCo report..."
bash scripts/generate-jacoco-report.sh
if [ $? -eq 0 ]; then
  log_success "JaCoCo report: JACOCO_RESULTS.md"
else
  log_error "JaCoCo report generation failed"
  exit 1
fi

log_info "Generating HTML report..."
bash scripts/generate-html-report.sh BENCHMARK_RESULTS.md JACOCO_RESULTS.md
if [ $? -eq 0 ]; then
  log_success "HTML report: reports/full-report.html"
else
  log_warn "HTML report generation failed, continuing..."
fi

log_success "Report generation pipeline complete!"