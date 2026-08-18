#!/bin/bash

# Creates index.html from markdown reports

set -euo pipefail

OUTPUT_PATH="${1:-reports/index.html}"

# Ensure reports directory exists
mkdir -p "$(dirname "$OUTPUT_PATH")"

# Read markdown files if they exist
jacoco_content=""
[ -f "JACOCO_RESULTS.md" ] && jacoco_content=$(cat JACOCO_RESULTS.md)

benchmark_content=""
[ -f "BENCHMARK_RESULTS.md" ] && benchmark_content=$(cat BENCHMARK_RESULTS.md)

test_content=""
[ -f "TEST_RESULTS.md" ] && test_content=$(cat TEST_RESULTS.md)

# Convert markdown to basic HTML
md2html() {
    echo "$1" | sed -E '
        s/^# (.*?)$/<h1>\1<\/h1>/
        s/^## (.*?)$/<h2>\1<\/h2>/
        s/^### (.*?)$/<h3>\1<\/h3>/
        s/\*\*(.*?)\*\*/<strong>\1<\/strong>/g
        s/\*(.*?)\*/<em>\1<\/em>/g
        s/`(.*?)`/<code>\1<\/code>/g
        s/^- (.*?)$/<li>\1<\/li>/
        s/^---$/<hr \/>/
        s/^\| /<tr><td>/g
        s/ \|/<\/td><td>/g
        s/\|$/<\/td><\/tr>/
    '
}

main() {
    {
        cat << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>WAL Project — Dynamic Reports</title>
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
            padding: 20px;
        }
        header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 40px 20px;
            margin: -20px -20px 40px -20px;
            border-radius: 8px 8px 0 0;
        }
        header h1 { font-size: 2.5em; margin-bottom: 10px; }
        header p { font-size: 1.1em; opacity: 0.9; }
        .nav {
            display: flex;
            gap: 20px;
            margin-bottom: 30px;
            border-bottom: 2px solid #ddd;
            padding-bottom: 20px;
        }
        .nav button {
            background: #667eea;
            color: white;
            border: none;
            padding: 10px 20px;
            border-radius: 4px;
            cursor: pointer;
            font-size: 1em;
            transition: background 0.3s;
        }
        .nav button:hover { background: #764ba2; }
        .nav button.active {
            background: #764ba2;
            border-bottom: 3px solid white;
            border-radius: 0;
        }
        .report-section {
            background: white;
            padding: 30px;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            display: none;
            margin-bottom: 30px;
        }
        .report-section.active { display: block; }
        .report-section h2 {
            color: #667eea;
            margin-top: 30px;
            margin-bottom: 15px;
            border-bottom: 2px solid #f0f0f0;
            padding-bottom: 10px;
        }
        .report-section h3 {
            color: #555;
            margin-top: 20px;
            margin-bottom: 10px;
        }
        .report-section table {
            width: 100%;
            border-collapse: collapse;
            margin: 15px 0;
        }
        .report-section table th,
        .report-section table td {
            border: 1px solid #ddd;
            padding: 12px;
            text-align: left;
        }
        .report-section table th {
            background: #f5f5f5;
            font-weight: bold;
        }
        code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; }
        .status {
            display: inline-block;
            padding: 6px 12px;
            border-radius: 20px;
            font-size: 0.9em;
            font-weight: bold;
        }
        .status.good { background: #d4edda; color: #155724; }
        .status.warning { background: #fff3cd; color: #856404; }
        .status.bad { background: #f8d7da; color: #721c24; }
        footer {
            text-align: center;
            margin-top: 40px;
            padding-top: 20px;
            border-top: 1px solid #ddd;
            color: #666;
            font-size: 0.9em;
        }
        .metric {
            background: #f9f9f9;
            padding: 15px;
            border-left: 4px solid #667eea;
            margin: 15px 0;
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>📊 WAL Project — Dynamic Reports</h1>
            <p>Data-driven analysis of coverage, performance, and test quality</p>
        </header>

        <div class="nav">
            <button class="nav-btn active" onclick="showReport('jacoco')">📈 Coverage</button>
            <button class="nav-btn" onclick="showReport('benchmark')">⚡ Performance</button>
            <button class="nav-btn" onclick="showReport('test')">🧪 Tests</button>
        </div>

        <div id="jacoco-report" class="report-section active">
            <h2>📊 JaCoCo Coverage Analysis</h2>
            <div id="jacoco-content">
EOF

        # Add Jacoco content
        if [ -n "$jacoco_content" ]; then
            echo "$jacoco_content" | sed -E '
                s/^# (.*?)$/<h2>\1<\/h2>/
                s/^## (.*?)$/<h3>\1<\/h3>/
                s/^### (.*?)$/<h4>\1<\/h4>/
                s/\*\*(.*?)\*\*/<strong>\1<\/strong>/g
                s/`(.*?)`/<code>\1<\/code>/g
            '
        else
            echo "<p>No coverage data available. Run: <code>mvn clean verify</code></p>"
        fi

        cat << 'EOF'
            </div>
        </div>

        <div id="benchmark-report" class="report-section">
            <h2>⚡ Benchmark Performance Analysis</h2>
            <div id="benchmark-content">
EOF

        # Add Benchmark content
        if [ -n "$benchmark_content" ]; then
            echo "$benchmark_content" | sed -E '
                s/^# (.*?)$/<h2>\1<\/h2>/
                s/^## (.*?)$/<h3>\1<\/h3>/
                s/^### (.*?)$/<h4>\1<\/h4>/
                s/\*\*(.*?)\*\*/<strong>\1<\/strong>/g
                s/`(.*?)`/<code>\1<\/code>/g
            '
        else
            echo "<p>No benchmark data available. Run: <code>mvn clean verify</code></p>"
        fi

        cat << 'EOF'
            </div>
        </div>

        <div id="test-report" class="report-section">
            <h2>🧪 Test Results Analysis</h2>
            <div id="test-content">
EOF

        # Add Test content
        if [ -n "$test_content" ]; then
            echo "$test_content" | sed -E '
                s/^# (.*?)$/<h2>\1<\/h2>/
                s/^## (.*?)$/<h3>\1<\/h3>/
                s/^### (.*?)$/<h4>\1<\/h4>/
                s/\*\*(.*?)\*\*/<strong>\1<\/strong>/g
                s/`(.*?)`/<code>\1<\/code>/g
            '
        else
            echo "<p>No test data available. Run: <code>mvn clean verify</code></p>"
        fi

        cat << 'EOF'
            </div>
        </div>

        <footer>
            <p>Generated: <strong>2026-08-18</strong></p>
            <p>WAL Project — Dynamic Reporting System</p>
            <p><small>100% Pure Bash • Zero Dependencies • Data-Driven Insights</small></p>
        </footer>
    </div>

    <script>
        function showReport(reportName) {
            // Hide all reports
            document.querySelectorAll('.report-section').forEach(el => {
                el.classList.remove('active');
            });

            // Show selected report
            document.getElementById(reportName + '-report').classList.add('active');

            // Update nav buttons
            document.querySelectorAll('.nav-btn').forEach(btn => {
                btn.classList.remove('active');
            });
            event.target.classList.add('active');
        }
    </script>
</body>
</html>
EOF
    } > "$OUTPUT_PATH"

    echo "[SUCCESS] HTML report: $OUTPUT_PATH"
}

main