#!/bin/bash

source scripts/report-utils.sh

JACOCO_REPORT="target/site/jacoco/index.xml"
OUTPUT_FILE="JACOCO_RESULTS.md"

log_info "Generating JaCoCo report..."

require_file "$JACOCO_REPORT"

{
  md_h1 "Code Coverage Report"

  echo "Generated: $(date)"
  echo ""

  md_h2 "Coverage Metrics"

  overall=$(grep -oP 'COVERED="\K[0-9]+' "$JACOCO_REPORT" | head -1)
  missed=$(grep -oP 'MISSED="\K[0-9]+' "$JACOCO_REPORT" | head -1)

  if [ -n "$overall" ] && [ -n "$missed" ]; then
    total=$((overall + missed))
    coverage=$(echo "scale=1; ($overall * 100) / $total" | bc)

    echo "| Metric | Value |"
    echo "|--------|-------|"
    echo "| Lines Covered | $overall |"
    echo "| Lines Missed | $missed |"
    echo "| Total Lines | $total |"
    echo "| Coverage | $coverage% |"
    echo ""
  fi

  md_h2 "Coverage by Source File"

  echo "| File | Covered | Missed | Total | % |"
  echo "|------|---------|--------|-------|---|"

  grep -E '<sourcefile' "$JACOCO_REPORT" | head -20 | while read line; do
    filename=$(echo "$line" | grep -oP 'name="\K[^"]+')
    covered=$(echo "$line" | grep -oP 'COVERED="\K[0-9]+')
    missed=$(echo "$line" | grep -oP 'MISSED="\K[0-9]+')

    if [ -n "$covered" ] && [ -n "$missed" ]; then
      total=$((covered + missed))
      pct=$(echo "scale=1; ($covered * 100) / $total" | bc)
      echo "| $filename | $covered | $missed | $total | $pct% |"
    fi
  done

  echo ""

} > "$OUTPUT_FILE"

log_success "JaCoCo report generated: $OUTPUT_FILE"