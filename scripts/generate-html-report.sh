#!/bin/bash

source scripts/report-utils.sh

OUTPUT_FILE="reports/full-report.html"

log_info "Generating HTML report..."

ensure_dir "reports"

# Read markdown files
BENCHMARK_CONTENT=""
if file_exists "BENCHMARK_RESULTS.md"; then
  BENCHMARK_CONTENT=$(cat BENCHMARK_RESULTS.md | sed 's/</\&lt;/g' | sed 's/>/\&gt;/g')
fi

JACOCO_CONTENT=""
if file_exists "JACOCO_RESULTS.md"; then
  JACOCO_CONTENT=$(cat JACOCO_RESULTS.md | sed 's/</\&lt;/g' | sed 's/>/\&gt;/g')
fi

TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

cat > "$OUTPUT_FILE" << 'HTMLEOF'
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>WAL System Report</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      line-height: 1.6;
      color: #333;
      background: #f5f5f5;
      margin: 0;
      padding: 0;
    }
    .container {
      max-width: 1200px;
      margin: 0 auto;
      padding: 20px;
    }
    header {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: 40px 20px;
      margin-bottom: 40px;
      border-radius: 8px;
    }
    header h1 {
      margin: 0 0 10px 0;
      font-size: 2.5em;
    }
    header p {
      margin: 0;
      font-size: 1.1em;
      opacity: 0.9;
    }
    .section {
      background: white;
      padding: 30px;
      margin-bottom: 20px;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    .section h2 {
      color: #667eea;
      border-bottom: 2px solid #667eea;
      padding-bottom: 10px;
      margin-top: 0;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      margin: 15px 0;
    }
    th, td {
      padding: 12px;
      text-align: left;
      border-bottom: 1px solid #ddd;
    }
    th {
      background: #f8f9fa;
      font-weight: 600;
    }
    tr:hover {
      background: #f8f9fa;
    }
    pre {
      background: #f4f4f4;
      padding: 15px;
      border-radius: 4px;
      overflow-x: auto;
    }
    .timestamp {
      color: #999;
      font-size: 0.9em;
      border-top: 1px solid #eee;
      padding-top: 20px;
      margin-top: 40px;
    }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <h1>Write-Ahead Log System Report</h1>
      <p>Comprehensive test and benchmark analysis</p>
    </header>

    <div class="section">
      <h2>Overview</h2>
      <p>This report contains performance benchmarks and code coverage metrics for the Write-Ahead Log system.</p>
      <ul>
        <li>Benchmarks: 5 angles of WAL performance</li>
        <li>Code Coverage: Line coverage metrics by source file</li>
        <li>Generated: <strong>TIMESTAMP_PLACEHOLDER</strong></li>
      </ul>
    </div>

    <div class="section">
      <h2>Performance Benchmarks</h2>
      <pre>BENCHMARK_PLACEHOLDER</pre>
    </div>

    <div class="section">
      <h2>Code Coverage</h2>
      <pre>JACOCO_PLACEHOLDER</pre>
    </div>

    <div class="section timestamp">
      <small>Report generated on <strong>TIMESTAMP_PLACEHOLDER</strong></small>
    </div>
  </div>
</body>
</html>
HTMLEOF

# Now substitute the placeholders
sed -i "s|TIMESTAMP_PLACEHOLDER|$TIMESTAMP|g" "$OUTPUT_FILE"
sed -i "s|BENCHMARK_PLACEHOLDER|$BENCHMARK_CONTENT|g" "$OUTPUT_FILE"
sed -i "s|JACOCO_PLACEHOLDER|$JACOCO_CONTENT|g" "$OUTPUT_FILE"

log_success "HTML report: $OUTPUT_FILE"