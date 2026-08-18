#!/bin/bash

source scripts/report-utils.sh

JACOCO_REPORT="target/site/jacoco/jacoco.xml"
OUTPUT_FILE="JACOCO_RESULTS.md"

log_info "Checking for JaCoCo report at: $JACOCO_REPORT"

if ! file_exists "$JACOCO_REPORT"; then
  log_warn "JaCoCo report not found"
  exit 0
fi

log_info "Generating JaCoCo report..."

{
  echo "# Code Coverage Report"
  echo ""
  echo "## Coverage Metrics"
  echo ""

  # Safely extract coverage values
  overall=$(grep -oP 'COVERED="\K[0-9]+' "$JACOCO_REPORT" | head -1)
  missed=$(grep -oP 'MISSED="\K[0-9]+' "$JACOCO_REPORT" | head -1)

  if [ -z "$overall" ] || [ -z "$missed" ]; then
    log_warn "Could not parse coverage metrics"
    echo "| Metric | Value |"
    echo "|--------|-------|"
    echo "| Status | Parse Error |"
  else
    total=$((overall + missed))
    coverage=$(echo "scale=1; ($overall * 100) / $total" | bc 2>/dev/null || echo "N/A")

    echo "| Metric | Value |"
    echo "|--------|-------|"
    echo "| Lines Covered | $overall |"
    echo "| Lines Missed | $missed |"
    echo "| Total Lines | $total |"
    echo "| Coverage | $coverage% |"
  fi

  echo ""
  echo "## Coverage by Source File"
  echo ""
  echo "| File | Covered | Missed | Total | % |"
  echo "|------|---------|--------|-------|---|"

  grep -E '<sourcefile' "$JACOCO_REPORT" | head -20 | while read line; do
    filename=$(echo "$line" | grep -oP 'name="\K[^"]+')
    covered=$(echo "$line" | grep -oP 'COVERED="\K[0-9]+')
    missed=$(echo "$line" | grep -oP 'MISSED="\K[0-9]+')

    if [ -n "$covered" ] && [ -n "$missed" ]; then
      total=$((covered + missed))
      pct=$(echo "scale=1; ($covered * 100) / $total" | bc 2>/dev/null || echo "N/A")
      echo "| $filename | $covered | $missed | $total | $pct% |"
    fi
  done

} > "$OUTPUT_FILE" 2>&1

if [ $? -eq 0 ]; then
  log_success "JaCoCo report: $OUTPUT_FILE"
else
  log_error "JaCoCo report generation failed"
  cat "$OUTPUT_FILE"
  exit 1
fi