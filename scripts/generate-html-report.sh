#!/bin/bash

# Beautiful HTML Report Generator (Pure Bash)
# Properly converts markdown to HTML with no syntax creep

set -euo pipefail

OUTPUT_PATH="${1:-reports/index.html}"
mkdir -p "$(dirname "$OUTPUT_PATH")"

# Proper markdown to HTML conversion
md_to_html() {
    local md="$1"
    local html=""
    local line_num=0
    local in_table=0
    local in_list=0
    local in_code=0

    while IFS= read -r line; do
        # Code blocks
        if [[ "$line" =~ ^\`\`\`bash ]]; then
            html+="<pre><code class=\"language-bash\">"
            in_code=1
            continue
        elif [[ "$line" =~ ^\`\`\` ]] && [ $in_code -eq 1 ]; then
            html+="</code></pre>"
            in_code=0
            continue
        fi

        if [ $in_code -eq 1 ]; then
            line=$(echo "$line" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')
            html+="$line"$'\n'
            continue
        fi

        # Headers
        if [[ "$line" =~ ^#\ (.*) ]]; then
            [ $in_list -eq 1 ] && html+="</ul>" && in_list=0
            [ $in_table -eq 1 ] && html+="</tbody></table>" && in_table=0
            html+="<h1>${BASH_REMATCH[1]}</h1>"$'\n'
            continue
        fi
        if [[ "$line" =~ ^##\ (.*) ]]; then
            [ $in_list -eq 1 ] && html+="</ul>" && in_list=0
            [ $in_table -eq 1 ] && html+="</tbody></table>" && in_table=0
            html+="<h2>${BASH_REMATCH[1]}</h2>"$'\n'
            continue
        fi
        if [[ "$line" =~ ^###\ (.*) ]]; then
            [ $in_list -eq 1 ] && html+="</ul>" && in_list=0
            [ $in_table -eq 1 ] && html+="</tbody></table>" && in_table=0
            html+="<h3>${BASH_REMATCH[1]}</h3>"$'\n'
            continue
        fi
        if [[ "$line" =~ ^####\ (.*) ]]; then
            html+="<h4>${BASH_REMATCH[1]}</h4>"$'\n'
            continue
        fi

        # Horizontal rule
        if [[ "$line" =~ ^-{3,}$ ]]; then
            html+="<hr />"$'\n'
            continue
        fi

        # Tables (line starts with |)
        if [[ "$line" =~ ^\| ]]; then
            # Check if this is a separator line (contains dashes)
            if [[ "$line" =~ \-+\| ]]; then
                if [ $in_table -eq 0 ]; then
                    html+="<table><thead><tr>"
                    in_table=1
                else
                    html+="</tr></thead><tbody>"
                fi
            else
                if [ $in_table -eq 0 ]; then
                    html+="<table><tr>"
                    in_table=1
                else
                    html+="<tr>"
                fi

                # Parse cells
                line="${line#|}"
                line="${line%|}"

                while IFS='|' read -r cell; do
                    cell=$(echo "$cell" | xargs)
                    # Inline formatting
                    cell=$(echo "$cell" | sed 's/\*\*\([^*]*\)\*\*/<strong>\1<\/strong>/g')
                    cell=$(echo "$cell" | sed "s/\`\([^\`]*\)\`/<code>\1<\/code>/g")
                    html+="<td>$cell</td>"
                done <<< "$line"

                html+="</tr>"
            fi
            continue
        elif [ $in_table -eq 1 ]; then
            html+="</tbody></table>"$'\n'
            in_table=0
        fi

        # Lists
        if [[ "$line" =~ ^-\ (.*) ]]; then
            if [ $in_list -eq 0 ]; then
                html+="<ul>"
                in_list=1
            fi
            local item="${BASH_REMATCH[1]}"
            item=$(echo "$item" | sed 's/\*\*\([^*]*\)\*\*/<strong>\1<\/strong>/g')
            item=$(echo "$item" | sed "s/\`\([^\`]*\)\`/<code>\1<\/code>/g")
            html+="<li>$item</li>"$'\n'
            continue
        elif [ $in_list -eq 1 ]; then
            html+="</ul>"$'\n'
            in_list=0
        fi

        # Empty lines
        if [ -z "$line" ]; then
            html+=""$'\n'
            continue
        fi

        # Inline formatting and paragraphs
        line=$(echo "$line" | sed 's/\*\*\([^*]*\)\*\*/<strong>\1<\/strong>/g')
        line=$(echo "$line" | sed "s/\`\([^\`]*\)\`/<code>\1<\/code>/g")
        html+="<p>$line</p>"$'\n'

    done <<< "$md"

    # Close remaining open tags
    [ $in_list -eq 1 ] && html+="</ul>"
    [ $in_table -eq 1 ] && html+="</tbody></table>"

    echo "$html"
}

main() {
    # Read markdown files
    local jacoco_md=""
    local benchmark_md=""
    local test_md=""

    [ -f "JACOCO_RESULTS.md" ] && jacoco_md=$(cat JACOCO_RESULTS.md) || jacoco_md="# No Coverage Data\n\nNo JaCoCo report found. Run: \`mvn clean verify\`"
    [ -f "BENCHMARK_RESULTS.md" ] && benchmark_md=$(cat BENCHMARK_RESULTS.md) || benchmark_md="# No Performance Data\n\nNo benchmark results found. Run: \`mvn clean verify\`"
    [ -f "TEST_RESULTS.md" ] && test_md=$(cat TEST_RESULTS.md) || test_md="# No Test Data\n\nNo test results found. Run: \`mvn clean verify\`"

    # Convert markdown to HTML
    jacoco_html=$(md_to_html "$jacoco_md")
    benchmark_html=$(md_to_html "$benchmark_md")
    test_html=$(md_to_html "$test_md")

    # Generate HTML
    {
        cat << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>WAL Project — Analysis Reports</title>
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
        }

        .container {
            max-width: 1400px;
            margin: 0 auto;
            padding: 20px;
        }

        header {
            background: rgba(255, 255, 255, 0.95);
            backdrop-filter: blur(10px);
            padding: 50px 30px;
            border-radius: 16px;
            margin-bottom: 30px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.15);
        }

        header h1 {
            font-size: 2.8em;
            background: linear-gradient(135deg, #667eea, #764ba2);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
            font-weight: 800;
            margin-bottom: 10px;
        }

        header p {
            font-size: 1.1em;
            color: #7f8c8d;
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
            transition: all 0.3s;
            box-shadow: 0 4px 15px rgba(102, 126, 234, 0.2);
        }

        .nav-btn:hover {
            background: linear-gradient(135deg, #667eea, #764ba2);
            color: white;
            transform: translateY(-3px);
        }

        .nav-btn.active {
            background: linear-gradient(135deg, #667eea, #764ba2);
            color: white;
        }

        .report-section {
            background: white;
            padding: 50px;
            border-radius: 16px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.1);
            display: none;
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
            font-size: 2.2em;
            margin-bottom: 30px;
            border-bottom: 3px solid #667eea;
            padding-bottom: 20px;
            font-weight: 800;
        }

        .report-section h2 {
            color: #667eea;
            font-size: 1.8em;
            margin: 40px 0 20px 0;
            border-left: 5px solid #667eea;
            padding-left: 20px;
            font-weight: 700;
        }

        .report-section h3 {
            color: #34495e;
            font-size: 1.3em;
            margin: 30px 0 15px 0;
            font-weight: 600;
        }

        .report-section h4 {
            color: #555;
            font-size: 1.1em;
            margin: 20px 0 10px 0;
            font-weight: 600;
        }

        .report-section p {
            margin-bottom: 18px;
            color: #555;
            line-height: 1.9;
        }

        .report-section ul, .report-section ol {
            margin-left: 35px;
            margin-bottom: 20px;
        }

        .report-section li {
            margin-bottom: 12px;
            color: #555;
        }

        .report-section code {
            background: #f5f7fa;
            color: #e74c3c;
            padding: 2px 6px;
            border-radius: 4px;
            font-family: 'Courier New', monospace;
            font-size: 0.9em;
        }

        .report-section pre {
            background: #f5f7fa;
            padding: 20px;
            border-radius: 8px;
            overflow-x: auto;
            margin: 20px 0;
            border-left: 4px solid #667eea;
        }

        .report-section pre code {
            background: none;
            color: #333;
            padding: 0;
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
            font-size: 1em;
        }

        table th {
            background: linear-gradient(135deg, #667eea, #764ba2);
            color: white;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            font-size: 0.95em;
        }

        table tbody tr {
            transition: background-color 0.2s;
        }

        table tbody tr:hover {
            background: rgba(102, 126, 234, 0.05);
        }

        table tbody tr:nth-child(even) {
            background: rgba(102, 126, 234, 0.02);
        }

        hr {
            border: none;
            border-top: 2px solid #ecf0f1;
            margin: 40px 0;
        }

        footer {
            text-align: center;
            margin-top: 60px;
            padding: 40px;
            background: white;
            color: #7f8c8d;
            border-radius: 16px;
            box-shadow: 0 4px 15px rgba(0,0,0,0.08);
        }

        @media (max-width: 768px) {
            header h1 {
                font-size: 1.8em;
            }

            .report-section {
                padding: 25px;
            }

            .report-section h1 {
                font-size: 1.6em;
            }

            .report-section h2 {
                font-size: 1.4em;
            }

            .nav-btn {
                font-size: 0.9em;
                padding: 10px 16px;
            }

            table {
                font-size: 0.9em;
            }

            table th, table td {
                padding: 12px 10px;
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
        echo "$jacoco_html"

        cat << 'EOF'
        </div>

        <div class="report-section" id="benchmark">
EOF
        echo "$benchmark_html"

        cat << 'EOF'
        </div>

        <div class="report-section" id="test">
EOF
        echo "$test_html"

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

            document.querySelectorAll('.report-section').forEach(tab => {
                tab.classList.remove('active');
            });

            document.querySelectorAll('.nav-btn').forEach(btn => {
                btn.classList.remove('active');
            });

            document.getElementById(tabName).classList.add('active');
            event.target.classList.add('active');

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