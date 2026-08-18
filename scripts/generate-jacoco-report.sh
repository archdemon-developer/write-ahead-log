#!/bin/bash

source scripts/report-utils.sh

JACOCO_REPORT="target/site/jacoco/jacoco.xml"
OUTPUT_FILE="JACOCO_RESULTS.md"

log_info "Generating JaCoCo report..."

if ! file_exists "$JACOCO_REPORT"; then
  log_warn "JaCoCo report not found"
  exit 0
fi

# Extract values first
overall=$(grep -oP 'COVERED="\K[0-9]+' "$JACOCO_REPORT" | head -1)
missed=$(grep -oP 'MISSED="\K[0-9]+' "$JACOCO_REPORT" | head -1)

# Create markdown file
cat > "$OUTPUT_FILE" << EOF
# Code Coverage Report

## Coverage Metrics

| Metric | Value |
|--------|-------|
| Lines Covered | $overall |
| Lines Missed | $missed |
| Total Lines | $((overall + missed)) |
| Coverage | N/A |

## Coverage by Source File

| File | Covered | Missed | Total | % |
|------|---------|--------|-------|---|
EOF

# Append file coverage
grep -E '<sourcefile' "$JACOCO_REPORT" | head -20 | while IFS= read -r line; do
  filename=$(echo "$line" | grep -oP 'name="\K[^"]+')
  covered=$(echo "$line" | grep -oP 'COVERED="\K[0-9]+')
  missed=$(echo "$line" | grep -oP 'MISSED="\K[0-9]+')

  if [ -n "$covered" ] && [ -n "$missed" ]; then
    total=$((covered + missed))
    pct=$((covered * 100 / total))
    echo "| $filename | $covered | $missed | $total | $pct% |" >> "$OUTPUT_FILE"
  fi
done

log_success "JaCoCo report: $OUTPUT_FILE"