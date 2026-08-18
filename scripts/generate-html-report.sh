#!/bin/bash

# Beautiful HTML Report Generator (Pure Bash)
# Creates attractive, professional HTML dashboard from markdown reports

set -euo pipefail

OUTPUT_PATH="${1:-reports/index.html}"
mkdir -p "$(dirname "$OUTPUT_PATH")"

main() {
    # Read markdown files (or use defaults if missing)
    local jacoco_md=""
    local benchmark_md=""
    local test_md=""

    [ -f "JACOCO_RESULTS.md" ] && jacoco_md=$(cat JACOCO_RESULTS.md)
    [ -f "BENCHMARK_RESULTS.md" ] && benchmark_md=$(cat BENCHMARK_RESULTS.md)
    [ -f "TEST_RESULTS.md" ] && test_md=$(cat TEST_RESULTS.md)

    {
        cat << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>WAL Project — Professional Test & Performance Reports</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Roboto", "Helvetica Neue", Arial, sans-serif;
            line-height: 1.7;
            color: #2c3e50;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding-bottom: 60px;
        }

        .container {
            max-width: 1400px;
            margin: 0 auto;
            padding: 20px;
        }

        header {
            background: rgba(255, 255, 255, 0.95);
            backdrop-filter: blur(10px);
            color: #2c3e50;
            padding: 50px 30px;
            border-radius: 16px;
            margin-bottom: 40px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.15);
            border: 1px solid rgba(255,255,255,0.5);
        }

        header h1 {
            font-size: 3em;
            margin-bottom: 10px;
            background: linear-gradient(135deg, #667eea, #764ba2);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
            font-weight: 800;
            letter-spacing: -1px;
        }

        header p {
            font-size: 1.2em;
            color: #7f8c8d;
            font-weight: 500;
        }

        .nav-container {
            display: flex;
            gap: 12px;
            margin-bottom: 30px;
            flex-wrap: wrap;
            justify-content: center;
        }

        .nav-btn {
            background: white;
            color: #667eea;
            border: 2px solid #667eea;
            padding: 14px 28px;
            border-radius: 12px;
            cursor: pointer;
            font-size: 1.05em;
            font-weight: 600;
            transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
            box-shadow: 0 4px 15px rgba(102, 126, 234, 0.2);
        }

        .nav-btn:hover {
            background: linear-gradient(135deg, #667eea, #764ba2);
            color: white;
            transform: translateY(-3px);
            box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
        }

        .nav-btn.active {
            background: linear-gradient(135deg, #667eea, #764ba2);
            color: white;
            box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
        }

        .reports-grid {
            display: grid;
            grid-template-columns: 1fr;
            gap: 20px;
        }

        .report-section {
            background: white;
            padding: 50px;
            border-radius: 16px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.1);
            display: none;
            margin-bottom: 0;
            border: 1px solid #ecf0f1;
            animation: slideIn 0.5s ease-out;
        }

        .report-section.active {
            display: block;
        }

        @keyframes slideIn {
            from {
                opacity: 0;
                transform: translateY(10px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }

        .report-section h1 {
            color: #667eea;
            font-size: 2.5em;
            margin-bottom: 30px;
            border-bottom: 3px solid #667eea;
            padding-bottom: 20px;
            font-weight: 800;
        }

        .report-section h2 {
            color: #667eea;
            font-size: 1.8em;
            margin-top: 40px;
            margin-bottom: 20px;
            border-left: 5px solid #667eea;
            padding-left: 20px;
            font-weight: 700;
        }

        .report-section h3 {
            color: #34495e;
            font-size: 1.3em;
            margin-top: 30px;
            margin-bottom: 15px;
            font-weight: 600;
        }

        .report-section h4 {
            color: #555;
            font-size: 1.1em;
            margin-top: 20px;
            margin-bottom: 12px;
            font-weight: 600;
        }

        .report-section p {
            margin-bottom: 18px;
            color: #555;
            line-height: 1.9;
            font-size: 1.05em;
        }

        .report-section ul,
        .report-section ol {
            margin-left: 35px;
            margin-bottom: 20px;
        }

        .report-section li {
            margin-bottom: 12px;
            color: #555;
            line-height: 1.8;
            font-size: 1.05em;
        }

        .metric-box {
            background: linear-gradient(135deg, rgba(102, 126, 234, 0.1), rgba(118, 75, 162, 0.1));
            border-left: 5px solid #667eea;
            padding: 25px;
            margin: 25px 0;
            border-radius: 8px;
            border: 1px solid rgba(102, 126, 234, 0.2);
        }

        .metric-box strong {
            color: #667eea;
            font-weight: 700;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            margin: 30px 0;
            box-shadow: 0 8px 20px rgba(0,0,0,0.08);
            border-radius: 8px;
            overflow: hidden;
        }

        table th,
        table td {
            border: 1px solid #ecf0f1;
            padding: 18px 20px;
            text-align: left;
            font-size: 1.05em;
        }

        table th {
            background: linear-gradient(135deg, #667eea, #764ba2);
            color: white;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            font-size: 1em;
        }

        table tbody tr {
            border-bottom: 1px solid #ecf0f1;
            transition: background-color 0.3s ease;
        }

        table tbody tr:hover {
            background: rgba(102, 126, 234, 0.05);
        }

        table tbody tr:nth-child(even) {
            background: rgba(102, 126, 234, 0.02);
        }

        code {
            background: #f5f7fa;
            color: #e74c3c;
            padding: 4px 10px;
            border-radius: 6px;
            font-family: 'Monaco', 'Courier New', monospace;
            font-size: 0.95em;
            font-weight: 600;
        }

        .status-badge {
            display: inline-block;
            padding: 8px 16px;
            border-radius: 20px;
            font-size: 0.95em;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            margin-right: 10px;
        }

        .status-good {
            background: #d5f4e6;
            color: #27ae60;
        }

        .status-warning {
            background: #ffeaa7;
            color: #d63031;
        }

        .status-bad {
            background: #fab1a0;
            color: #d63031;
        }

        .status-info {
            background: #dfe6e9;
            color: #2d3436;
        }

        hr {
            border: none;
            border-top: 2px solid #ecf0f1;
            margin: 40px 0;
        }

        .highlight {
            background: rgba(255, 193, 7, 0.2);
            padding: 20px;
            border-radius: 8px;
            border-left: 4px solid #ffc107;
            margin: 20px 0;
            color: #555;
        }

        .highlight strong {
            color: #f39c12;
        }

        footer {
            text-align: center;
            margin-top: 60px;
            padding: 40px;
            background: white;
            color: #7f8c8d;
            font-size: 1em;
            border-radius: 16px;
            box-shadow: 0 4px 15px rgba(0,0,0,0.08);
            border: 1px solid #ecf0f1;
        }

        footer p {
            margin: 8px 0;
        }

        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
            gap: 20px;
            margin: 30px 0;
        }

        .metric-card {
            background: linear-gradient(135deg, #667eea15, #764ba215);
            border: 1px solid rgba(102, 126, 234, 0.3);
            padding: 25px;
            border-radius: 12px;
            text-align: center;
            box-shadow: 0 4px 15px rgba(102, 126, 234, 0.1);
        }

        .metric-card h3 {
            color: #667eea;
            margin-top: 0;
            font-size: 0.95em;
            text-transform: uppercase;
            letter-spacing: 1px;
            font-weight: 700;
        }

        .metric-card .value {
            font-size: 2em;
            color: #764ba2;
            font-weight: 800;
            margin: 15px 0;
        }

        .metric-card .unit {
            color: #95a5a6;
            font-size: 0.9em;
        }

        @media (max-width: 768px) {
            header h1 {
                font-size: 2em;
            }

            .report-section {
                padding: 25px;
            }

            .report-section h1 {
                font-size: 1.8em;
            }

            .report-section h2 {
                font-size: 1.4em;
            }

            .nav-container {
                gap: 8px;
            }

            .nav-btn {
                font-size: 0.9em;
                padding: 10px 16px;
            }

            table {
                font-size: 0.9em;
            }

            table th,
            table td {
                padding: 12px 10px;
            }

            .metrics-grid {
                grid-template-columns: 1fr;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>📊 WAL Project — Analysis Reports</h1>
            <p>Comprehensive coverage, performance, and quality analysis</p>
        </header>

        <div class="nav-container">
            <button class="nav-btn active" onclick="showTab('jacoco', event)">📈 Coverage</button>
            <button class="nav-btn" onclick="showTab('benchmark', event)">⚡ Performance</button>
            <button class="nav-btn" onclick="showTab('test', event)">🧪 Tests</button>
        </div>

        <div class="report-section active" id="jacoco">
EOF
        # Insert JACOCO markdown converted to clean HTML
        echo "$jacoco_md" | sed 's/^# /\n<h1>/; s/$/<\/h1>/' | sed 's/^## /\n<h2>/; s/$/<\/h2>/' | sed 's/^\*\*/<strong>/; s/\*\*$/<\/strong>/'

        cat << 'EOF'
        </div>

        <div class="report-section" id="benchmark">
EOF
        # Insert BENCHMARK markdown converted to clean HTML
        echo "$benchmark_md" | sed 's/^# /\n<h1>/; s/$/<\/h1>/' | sed 's/^## /\n<h2>/; s/$/<\/h2>/' | sed 's/^\*\*/<strong>/; s/\*\*$/<\/strong>/'

        cat << 'EOF'
        </div>

        <div class="report-section" id="test">
EOF
        # Insert TEST markdown converted to clean HTML
        echo "$test_md" | sed 's/^# /\n<h1>/; s/$/<\/h1>/' | sed 's/^## /\n<h2>/; s/$/<\/h2>/' | sed 's/^\*\*/<strong>/; s/\*\*$/<\/strong>/'

        cat << 'EOF'
        </div>

        <footer>
            <p><strong>WAL Project</strong> — Professional Analysis Dashboard</p>
            <p>Generated with Pure Bash | All metrics explained | Production-ready</p>
        </footer>
    </div>

    <script>
        function showTab(tabName, event) {
            event.preventDefault();

            // Hide all tabs
            document.querySelectorAll('.report-section').forEach(tab => {
                tab.classList.remove('active');
            });

            // Deactivate all buttons
            document.querySelectorAll('.nav-btn').forEach(btn => {
                btn.classList.remove('active');
            });

            // Show selected tab
            document.getElementById(tabName).classList.add('active');

            // Activate clicked button
            event.target.classList.add('active');

            // Scroll to top smoothly
            window.scrollTo({top: 0, behavior: 'smooth'});
        }
    </script>
</body>
</html>
EOF
    } > "$OUTPUT_PATH"

    echo "[SUCCESS] Beautiful HTML report: $OUTPUT_PATH"
}

main