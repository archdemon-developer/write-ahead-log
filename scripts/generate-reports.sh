#!/bin/bash

set -e

source scripts/report-utils.sh

log_info "════════════════════════════════════════════════════════════"
log_info "🚀 DYNAMIC REPORT GENERATION PIPELINE (Pure Bash)"
log_info "════════════════════════════════════════════════════════════"

log_info "Checking for test/benchmark artifacts..."

# Ensure scripts are executable
chmod +x scripts/analyze-*.sh 2>/dev/null || true

# === JaCoCo Analysis ===
if [ -f "target/site/jacoco/jacoco.xml" ]; then
  log_info ""
  log_info "→ Analyzing JaCoCo Coverage (dynamic, Bash)..."
  bash scripts/generate-jacoco-report.sh target/site/jacoco/jacoco.xml JACOCO_DYNAMIC.md
  log_success "✅ Coverage analysis complete"
else
  log_warn "⚠️  JaCoCo XML not found. Run: mvn clean verify"
  echo "# 📊 JaCoCo Coverage Report" > JACOCO_DYNAMIC.md
  echo "" >> JACOCO_DYNAMIC.md
  echo "No coverage data available. Run: \`mvn clean verify\`" >> JACOCO_DYNAMIC.md
fi

# === Benchmark Analysis ===
if [ -f "benchmark-results.json" ]; then
  log_info ""
  log_info "→ Analyzing Benchmarks (dynamic, Bash)..."
  bash scripts/generate-benchmark-report.sh benchmark-results.json BENCHMARK_DYNAMIC.md
  log_success "✅ Benchmark analysis complete"
else
  log_warn "⚠️  Benchmark JSON not found. Benchmarks optional."
  bash scripts/generate-benchmark-report.sh "" BENCHMARK_DYNAMIC.md
fi

# === Test Analysis ===
if [ -d "target/surefire-reports" ] && [ "$(ls -A target/surefire-reports 2>/dev/null)" ]; then
  log_info ""
  log_info "→ Analyzing Test Results (dynamic, Bash)..."
  bash scripts/generate-test-report.sh target/surefire-reports TEST_DYNAMIC.md
  log_success "✅ Test analysis complete"
else
  log_warn "⚠️  Surefire reports not found. Run: mvn clean verify"
  bash scripts/analyze-tests.sh "" TEST_DYNAMIC.md
fi

log_info ""
log_info "════════════════════════════════════════════════════════════"
log_info "📊 DYNAMIC REPORT GENERATION COMPLETE"
log_info "════════════════════════════════════════════════════════════"

log_info ""
log_info "📄 Generated Dynamic Reports:"
log_info "  → JACOCO_DYNAMIC.md      (Coverage analysis with gaps identified)"
log_info "  → TEST_DYNAMIC.md        (Test quality & gap analysis)"
log_info "  → BENCHMARK_DYNAMIC.md   (Performance analysis with implications)"
log_info ""
log_info "📖 Read in order:"
log_info "  1️⃣  JACOCO_DYNAMIC.md       (Coverage gaps & component analysis)"
log_info "  2️⃣  TEST_DYNAMIC.md         (What tests exist, what's missing)"
log_info "  3️⃣  BENCHMARK_DYNAMIC.md    (Performance & system behavior)"
log_info ""

log_success "✅ All dynamic reports generated successfully"
log_info ""
log_info "💡 Key Differences from Static Reports:"
log_info "  ✅ Every metric is explained in context"
log_info "  ✅ Root causes identified"
log_info "  ✅ Specific components ranked by risk"
log_info "  ✅ Concrete next steps with effort estimates"
log_info "  ✅ Impact projections"
log_info ""