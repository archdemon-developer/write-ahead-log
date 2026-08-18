#!/bin/bash

source scripts/report-utils.sh

OUTPUT_FILE="reports/full-report.html"

ensure_dir "reports"

log_info "Generating HTML report..."

# Read markdown files with defaults if missing
BENCHMARK_MD="${1:-BENCHMARK_RESULTS.md}"
JACOCO_MD="${2:-JACOCO_RESULTS.md}"

benchmark_content=""
if [ -f "$BENCHMARK_MD" ]; then
  benchmark_content=$(cat "$BENCHMARK_MD")
else
  log_warn "$BENCHMARK_MD not found"
  benchmark_content="# Benchmark Results\n\nNo benchmark results available."
fi

jacoco_content=""
if [ -f "$JACOCO_MD" ]; then
  jacoco_content=$(cat "$JACOCO_MD")
else
  log_warn "$JACOCO_MD not found"
  jacoco_content="# JaCoCo Coverage\n\nNo coverage results available."
fi

# Generate HTML
cat > "$OUTPUT_FILE" << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>WAL Project Report</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      line-height: 1.6;
      color: #333;
      background: #f5f5f5;
    }
    .container {
      max-width: 1200px;
      margin: 0 auto;
      padding: 40px 20px;
    }
    header {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: 40px 20px;
      margin: -40px -20px 40px;
      border-radius: 0 0 8px 8px;
    }
    h1 { font-size: 2.5em; margin-bottom: 10px; }
    h2 { color: #667eea; margin: 30px 0 15px; border-bottom: 2px solid #667eea; padding-bottom: 10px; }
    h3 { color: #555; margin: 20px 0 10px; }
    pre {
      background: #f8f8f8;
      border: 1px solid #ddd;
      border-radius: 4px;
      padding: 15px;
      overflow-x: auto;
      margin: 10px 0;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      margin: 15px 0;
      background: white;
    }
    th, td {
      border: 1px solid #ddd;
      padding: 12px;
      text-align: left;
    }
    th {
      background: #f5f5f5;
      font-weight: 600;
      color: #333;
    }
    tr:hover { background: #f9f9f9; }
    .section {
      background: white;
      padding: 20px;
      margin: 20px 0;
      border-radius: 4px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    }
    code {
      background: #f0f0f0;
      padding: 2px 6px;
      border-radius: 3px;
      font-family: 'Monaco', 'Courier New', monospace;
      font-size: 0.9em;
    }
    a { color: #667eea; text-decoration: none; }
    a:hover { text-decoration: underline; }
    .success { color: #27ae60; font-weight: 600; }
    .warning { color: #f39c12; font-weight: 600; }
    .error { color: #e74c3c; font-weight: 600; }
    .timestamp {
      text-align: center;
      color: #999;
      font-size: 0.9em;
      margin-top: 40px;
      border-top: 1px solid #eee;
      padding-top: 20px;
    }
  </style>
</head>
<body>
  <header>
    <div class="container">
      <h1>Write-Ahead Log (WAL) Project</h1>
      <p>Complete Test & Performance Report</p>
    </div>
  </header>

  <div class="container">

    <div class="section" id="benchmark">
      <h2>Performance Benchmarks</h2>
      <pre id="benchmark-content"></pre>
    </div>

    <div class="section" id="coverage">
      <h2>Code Coverage Analysis</h2>
      <pre id="coverage-content"></pre>
    </div>

    <div class="timestamp">
      <p>Report generated: <span id="timestamp"></span></p>
      <p>WAL Project Phase 7.25 - Production Ready</p>
    </div>

  </div>

  <script>
    document.getElementById('benchmark-content').textContent = `BENCHMARK_PLACEHOLDER`;
    document.getElementById('coverage-content').textContent = `JACOCO_PLACEHOLDER`;
    document.getElementById('timestamp').textContent = new Date().toISOString();
  </script>

</body>
</html>
EOF

# Replace placeholders
sed -i "s|BENCHMARK_PLACEHOLDER|${benchmark_content}|g" "$OUTPUT_FILE"
sed -i "s|JACOCO_PLACEHOLDER|${jacoco_content}|g" "$OUTPUT_FILE"

log_success "HTML report generated: $OUTPUT_FILE"