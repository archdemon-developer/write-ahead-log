#!/bin/bash

source scripts/report-utils.sh

log_info "Starting report generation pipeline..."

# Check prerequisites
if [ ! -f "benchmark-results.json" ]; then
  log_warn "benchmark-results.json not found, skipping benchmark report"
else
  ./scripts/generate-benchmark-report.sh
fi

if [ ! -f "target/site/jacoco/index.xml" ]; then
  log_warn "JaCoCo report not found, skipping coverage report"
else
  ./scripts/generate-jacoco-report.sh
fi

# Convert markdown to HTML and embed
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

BENCHMARK_HTML=""
if [ -f "BENCHMARK_RESULTS.md" ]; then
  BENCHMARK_HTML=$(cat BENCHMARK_RESULTS.md | sed 's/^# /## /' | sed 's/^## /### /')
fi

JACOCO_HTML=""
if [ -f "JACOCO_RESULTS.md" ]; then
  JACOCO_HTML=$(cat JACOCO_RESULTS.md | sed 's/^# /## /' | sed 's/^## /### /')
fi

# Create final HTML report
ensure_dir "reports"

sed -e "s|TIMESTAMP_PLACEHOLDER|$TIMESTAMP|g" \
    -e "s|BENCHMARK_CONTENT_PLACEHOLDER|$BENCHMARK_HTML|g" \
    -e "s|JACOCO_CONTENT_PLACEHOLDER|$JACOCO_HTML|g" \
    <(./scripts/generate-html-report.sh 2>/dev/null | grep -v "^\\[" || true) > reports/full-report.html 2>/dev/null || {

  # Fallback: generate HTML directly
  cat > reports/full-report.html << EOF
<!DOCTYPE html>
<html>
<head>
  <title>WAL System Report</title>
  <meta charset="UTF-8">
  <style>
    body { font-family: sans-serif; margin: 40px; }
    h1 { color: #667eea; }
    table { border-collapse: collapse; width: 100%; }
    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
    th { background: #f0f0f0; }
  </style>
</head>
<body>
  <h1>Write-Ahead Log System Report</h1>
  <p><strong>Generated:</strong> $TIMESTAMP</p>

  <h2>Benchmarks</h2>
  <pre>$BENCHMARK_HTML</pre>

  <h2>Coverage</h2>
  <pre>$JACOCO_HTML</pre>
</body>
</html>
EOF
}

log_success "Report generation complete!"
log_info "Generated files:"
log_info "  - BENCHMARK_RESULTS.md"
log_info "  - JACOCO_RESULTS.md"
log_info "  - reports/full-report.html"