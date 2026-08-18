#!/bin/bash

# HTML Report Aggregator (Pure Bash)
# Creates index.html from markdown reports with proper HTML conversion

set -euo pipefail

OUTPUT_PATH="${1:-reports/index.html}"

# Ensure reports directory exists
mkdir -p "$(dirname "$OUTPUT_PATH")"

# Markdown to HTML conversion function
md_to_html() {
    local input="$1"
    local output=""

    # Read line by line
    while IFS= read -r line; do
        # Headers
        if [[ "$line" =~ ^#\ (.+)$ ]]; then
            output+="<h1>${BASH_REMATCH[1]}</h1>"$'\n'
        elif [[ "$line" =~ ^##\ (.+)$ ]]; then
            output+="<h2>${BASH_REMATCH[1]}</h2>"$'\n'
        elif [[ "$line" =~ ^###\ (.+)$ ]]; then
            output+="<h3>${BASH_REMATCH[1]}</h3>"$'\n'
        elif [[ "$line" =~ ^####\ (.+)$ ]]; then
            output+="<h4>${BASH_REMATCH[1]}</h4>"$'\n'
        # Horizontal rule
        elif [[ "$line" =~ ^---+$ ]]; then
            output+="<hr />"$'\n'
        # Bold and italic
        elif [[ "$line" =~ \*\*(.+?)\*\* ]]; then
            line="${line//\*\*/<strong>}"
            line="${line//\*\*/<\/strong>}"
            output+="<p>$line</p>"$'\n'
        # Code
        elif [[ "$line" =~ \`(.+?)\` ]]; then
            line="${line//\`/<code>}"
            line="${line//\`/<\/code>}"
            output+="<p>$line</p>"$'\n'
        # Lists
        elif [[ "$line" =~ ^-\ (.+)$ ]]; then
            output+="<li>${BASH_REMATCH[1]}</li>"$'\n'
        # Tables (start)
        elif [[ "$line" =~ ^\| ]]; then
            if [[ ! "$output" =~ \<table ]]; then
                output+="<table>"$'\n'
            fi
            # Convert pipe-separated to table row
            line="${line#|}"
            line="${line%|}"
            row="<tr>"
            while IFS='|' read -r cell; do
                cell="$(echo "$cell" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
                row+="<td>$cell</td>"
            done <<< "$line"
            row+="</tr>"
            output+="$row"$'\n'
        # Empty lines
        elif [[ -z "$line" ]]; then
            output+=$'\n'
        # Regular text
        else
            if [[ -n "$line" ]]; then
                output+="<p>$line</p>"$'\n'
            fi
        fi
    done <<< "$input"

    # Close table if open
    if [[ "$output" =~ \<table ]]; then
        output+="</table>"$'\n'
    fi

    echo "$output"
}

main() {
    # Read report files
    local jacoco_md=""
    local benchmark_md=""
    local test_md=""

    [ -f "JACOCO_RESULTS.md" ] && jacoco_md=$(cat JACOCO_RESULTS.md)
    [ -f "BENCHMARK_RESULTS.md" ] && benchmark_md=$(cat BENCHMARK_RESULTS.md)
    [ -f "TEST_RESULTS.md" ] && test_md=$(cat TEST_RESULTS.md)

    # Convert markdown to HTML
    local jacoco_html=$(md_to_html "$jacoco_md")
    local benchmark_html=$(md_to_html "$benchmark_md")
    local test_html=$(md_to_html "$test_md")

    {
        cat << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>WAL Project — Test & Performance Reports</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }

        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            line-height: 1.6;
            color: #333;
            background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
            min-height: 100vh;
        }

        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }

        header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 50px 20px;
            border-radius: 12px;
            margin-bottom: 30px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.2);
            text-align: center;
        }

        header h1 { font-size: 2.5em; margin-bottom: 10px; }
        header p { font-size: 1.1em; opacity: 0.9; }

        .nav {
            display: flex;
            gap: 15px;
            margin-bottom: 30px;
            flex-wrap: wrap;
            justify-content: center;
        }

        .nav button {
            background: white;
            color: #667eea;
            border: 2px solid #667eea;
            padding: 12px 24px;
            border-radius: 8px;
            cursor: pointer;
            font-size: 1em;
            font-weight: 600;
            transition: all 0.3s ease;
        }

        .nav button:hover {
            background: #667eea;
            color: white;
            transform: translateY(-2px);
            box-shadow: 0 5px 15px rgba(102,126,234,0.4);
        }

        .nav button.active {
            background: #667eea;
            color: white;
        }

        .report-section {
            background: white;
            padding: 40px;
            border-radius: 12px;
            box-shadow: 0 5px 20px rgba(0,0,0,0.1);
            display: none;
            margin-bottom: 30px;
            line-height: 1.8;
        }

        .report-section.active {
            display: block;
            animation: fadeIn 0.3s ease-in;
        }

        @keyframes fadeIn {
            from { opacity: 0; }
            to { opacity: 1; }
        }

        .report-section h1 {
            color: #667eea;
            font-size: 2em;
            margin-bottom: 20px;
            border-bottom: 3px solid #f0f0f0;
            padding-bottom: 15px;
        }

        .report-section h2 {
            color: #667eea;
            font-size: 1.5em;
            margin-top: 30px;
            margin-bottom: 15px;
            border-left: 4px solid #667eea;
            padding-left: 15px;
        }

        .report-section h3 {
            color: #555;
            font-size: 1.2em;
            margin-top: 20px;
            margin-bottom: 10px;
        }

        .report-section h4 {
            color: #666;
            font-size: 1.05em;
            margin-top: 15px;
            margin-bottom: 10px;
        }

        .report-section p {
            margin-bottom: 15px;
            color: #444;
            line-height: 1.8;
        }

        .report-section ul, .report-section ol {
            margin-left: 30px;
            margin-bottom: 15px;
        }

        .report-section li {
            margin-bottom: 8px;
            color: #444;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            margin: 20px 0;
            box-shadow: 0 2px 8px rgba(0,0,0,0.08);
        }

        table th,
        table td {
            border: 1px solid #ddd;
            padding: 14px 16px;
            text-align: left;
        }

        table th {
            background: #f8f9fa;
            font-weight: 600;
            color: #333;
            border-top: 2px solid #667eea;
            border-bottom: 2px solid #667eea;
        }

        table tr:hover {
            background: #f9f9f9;
        }

        table tr:nth-child(even) {
            background: #fafbfc;
        }

        code {
            background: #f4f4f4;
            padding: 3px 8px;
            border-radius: 4px;
            font-family: 'Monaco', 'Courier New', monospace;
            font-size: 0.9em;
            color: #d63384;
        }

        .status {
            display: inline-block;
            padding: 6px 14px;
            border-radius: 20px;
            font-size: 0.9em;
            font-weight: bold;
            margin-right: 10px;
        }

        .status.good { background: #d4edda; color: #155724; }
        .status.warning { background: #fff3cd; color: #856404; }
        .status.bad { background: #f8d7da; color: #721c24; }

        hr {
            border: none;
            border-top: 2px solid #e0e0e0;
            margin: 30px 0;
        }

        footer {
            text-align: center;
            margin-top: 50px;
            padding: 30px;
            border-top: 2px solid #ddd;
            color: #666;
            font-size: 0.9em;
        }

        @media (max-width: 768px) {
            header h1 { font-size: 1.8em; }
            .container { padding: 10px; }
            .report-section { padding: 20px; }
            .nav { gap: 10px; }
            .nav button { font-size: 0.9em; padding: 10px 16px; }
            table { font-size: 0.9em; }
            table td, table th { padding: 10px; }
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>📊 WAL Project — Reports Dashboard</h1>
            <p>Comprehensive test coverage, performance analysis, and quality metrics</p>
        </header>

        <div class="nav">
            <button class="nav-btn active" onclick="showReport('jacoco')">📈 Coverage</button>
            <button class="nav-btn" onclick="showReport('benchmark')">⚡ Performance</button>
            <button class="nav-btn" onclick="showReport('test')">🧪 Tests</button>
        </div>

        <div id="jacoco-report" class="report-section active">
EOF
        echo "$jacoco_html"

        cat << 'EOF'
        </div>

        <div id="benchmark-report" class="report-section">
EOF
        echo "$benchmark_html"

        cat << 'EOF'
        </div>

        <div id="test-report" class="report-section">
EOF
        echo "$test_html"

        cat << 'EOF'
        </div>

        <footer>
            <p><strong>WAL Project</strong> — Pure Bash Dynamic Reporting System</p>
            <p>Generated on <strong>2026-08-18</strong> | All metrics explained | Production-ready</p>
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

            // Scroll to top
            window.scrollTo({top: 0, behavior: 'smooth'});
        }
    </script>
</body>
</html>
EOF
    } > "$OUTPUT_PATH"

    echo "[SUCCESS] HTML report: $OUTPUT_PATH"
}

main