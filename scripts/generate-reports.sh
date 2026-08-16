#!/bin/bash

source scripts/report-utils.sh

log_info "Starting report generation pipeline..."

# Generate individual reports
log_info "Generating benchmark report..."
./scripts/generate-benchmark-report.sh

log_info "Generating JaCoCo report..."
./scripts/generate-jacoco-report.sh

log_info "Generating HTML report..."
./scripts/generate-html-report.sh

log_success "Report generation complete"
log_info "Generated files:"
log_info "  - BENCHMARK_RESULTS.md"
log_info "  - JACOCO_RESULTS.md"
log_info "  - reports/full-report.html"