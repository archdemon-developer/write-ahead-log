#!/bin/bash

# Main Report Orchestrator (Pure Bash)
# Generates all reports: Markdown + HTML

set -e

source scripts/report-utils.sh

log_info "════════════════════════════════════════════════════════════"
log_info "🚀 GENERATING ALL REPORTS (Markdown + HTML)"
log_info "════════════════════════════════════════════════════════════"

# Make scripts executable
chmod +x scripts/generate-*.sh 2>/dev/null || true

# === JaCoCo Report ===
if [ -f "target/site/jacoco/jacoco.xml" ]; then
    log_info ""
    log_info "→ Generating JaCoCo Coverage Report..."
    bash scripts/generate-jacoco-report.sh target/site/jacoco/jacoco.xml JACOCO_RESULTS.md
    log_success "✅ Coverage report complete"
else
    log_warn "⚠️  JaCoCo XML not found"
    echo "# JaCoCo Coverage Report" > JACOCO_RESULTS.md
    echo "No data available. Run: \`mvn clean verify\`" >> JACOCO_RESULTS.md
fi

# === Benchmark Report ===
if [ -f "benchmark-results.json" ]; then
    log_info ""
    log_info "→ Generating Benchmark Report..."
    bash scripts/generate-benchmark-report.sh benchmark-results.json BENCHMARK_RESULTS.md
    log_success "✅ Benchmark report complete"
else
    log_warn "⚠️  Benchmark JSON not found"
    bash scripts/generate-benchmark-report.sh "" BENCHMARK_RESULTS.md
fi

# === Test Report ===
if [ -d "target/surefire-reports" ] && [ "$(ls -A target/surefire-reports 2>/dev/null)" ]; then
    log_info ""
    log_info "→ Generating Test Report..."
    bash scripts/generate-test-report.sh target/surefire-reports TEST_RESULTS.md
    log_success "✅ Test report complete"
else
    log_warn "⚠️  Surefire reports not found"
    bash scripts/generate-test-report.sh "" TEST_RESULTS.md
fi

# === HTML Aggregator ===
log_info ""
log_info "→ Generating HTML Aggregator..."
bash scripts/generate-html-report.sh reports/index.html
log_success "✅ HTML report complete"

log_info ""
log_info "════════════════════════════════════════════════════════════"
log_info "📊 ALL REPORTS GENERATED SUCCESSFULLY"
log_info "════════════════════════════════════════════════════════════"

log_info ""
log_info "📄 Generated Files:"
log_info "  → JACOCO_RESULTS.md         (Coverage analysis)"
log_info "  → BENCHMARK_RESULTS.md      (Performance analysis)"
log_info "  → TEST_RESULTS.md           (Test quality analysis)"
log_info "  → reports/index.html        (Interactive HTML dashboard)"
log_info ""
log_info "📖 View Reports:"
log_info "  📑 Markdown: cat JACOCO_RESULTS.md"
log_info "  🌐 HTML:     open reports/index.html"
log_info ""

log_success "✅ Complete reporting pipeline finished"