#!/bin/bash

source scripts/report-utils.sh

BENCHMARK_MD="BENCHMARK_RESULTS.md"
JACOCO_MD="JACOCO_RESULTS.md"
OUTPUT_FILE="reports/full-report.html"

log_info "Generating HTML report..."

ensure_dir "reports"
require_file "$BENCHMARK_MD"
require_file "$JACOCO_MD"

{
  cat << 'HTMLEOF'
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>WAL System Report</title>
  <style>
    * {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
    }

    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
      line-height: 1.6;
      color: #333;
      background: #f5f5f5;
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
      box-shadow: 0 4px 6px rgba(0,0,0,0.1);
    }

    header h1 {
      font-size: 2.5em;
      margin-bottom: 10px;
    }

    header p {
      font-size: 1.1em;
      opacity: 0.9;
    }

    nav {
      display: flex;
      gap: 20px;
      margin-bottom: 40px;
      flex-wrap: wrap;
    }

    nav button {
      padding: 10px 20px;
      background: white;
      border: 2px solid #667eea;
      color: #667eea;
      cursor: pointer;
      border-radius: 4px;
      font-size: 1em;
      transition: all 0.3s ease;
    }

    nav button:hover {
      background: #667eea;
      color: white;
    }

    nav button.active {
      background: #667eea;
      color: white;
    }

    .section {
      background: white;
      padding: 30px;
      margin-bottom: 20px;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
      display: none;
    }

    .section.active {
      display: block;
    }

    .section h2 {
      color: #667eea;
      margin-top: 30px;
      margin-bottom: 15px;
      border-bottom: 2px solid #667eea;
      padding-bottom: 10px;
    }

    .section h3 {
      color: #555;
      margin-top: 20px;
      margin-bottom: 10px;
    }

    table {
      width: 100%;
      border-collapse: collapse;
      margin: 15px 0;
    }

    table th, table td {
      padding: 12px;
      text-align: left;
      border-bottom: 1px solid #ddd;
    }

    table th {
      background: #f8f9fa;
      font-weight: 600;
      color: #333;
    }

    table tr:hover {
      background: #f8f9fa;
    }

    code {
      background: #f4f4f4;
      padding: 2px 6px;
      border-radius: 3px;
      font-family: 'Courier New', monospace;
    }

    pre {
      background: #f4f4f4;
      padding: 15px;
      border-radius: 4px;
      overflow-x: auto;
      margin: 15px 0;
    }

    .timestamp {
      color: #999;
      font-size: 0.9em;
      margin-top: 20px;
      padding-top: 20px;
      border-top: 1px solid #eee;
    }

    a {
      color: #667eea;
      text-decoration: none;
    }

    a:hover {
      text-decoration: underline;
    }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <h1>Write-Ahead Log System Report</h1>
      <p>Comprehensive test and benchmark analysis</p>
    </header>

    <nav>
      <button class="nav-btn active" data-section="overview">Overview</button>
      <button class="nav-btn" data-section="benchmarks">Benchmarks</button>
      <button class="nav-btn" data-section="coverage">Coverage</button>
    </nav>

    <div id="overview" class="section active">
      <h2>Test Run Summary</h2>
      <p>Generated: <strong>TIMESTAMP_PLACEHOLDER</strong></p>

      <h3>Report Contents</h3>
      <ul>
        <li><strong>Benchmarks:</strong> 5 angles of WAL performance (producer throughput, writer drain rate, durability latency, queue saturation, fsync strategy impact)</li>
        <li><strong>Code Coverage:</strong> Line coverage metrics by source file</li>
        <li><strong>Test Results:</strong> Unit tests, integration tests, and performance benchmarks</li>
      </ul>
    </div>

    <div id="benchmarks" class="section">
      <h2>Performance Benchmarks</h2>
      BENCHMARK_CONTENT_PLACEHOLDER
    </div>

    <div id="coverage" class="section">
      <h2>Code Coverage Analysis</h2>
      JACOCO_CONTENT_PLACEHOLDER
    </div>

    <div class="timestamp">
      <small>Report generated on <strong>TIMESTAMP_PLACEHOLDER</strong></small>
    </div>
  </div>

  <script>
    document.querySelectorAll('.nav-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));

        btn.classList.add('active');
        const section = document.getElementById(btn.dataset.section);
        if (section) section.classList.add('active');
      });
    });
  </script>
</body>
</html>
HTMLEOF

} > "$OUTPUT_FILE"

log_success "HTML report generated: $OUTPUT_FILE"