#!/bin/bash

source scripts/report-utils.sh

JACOCO_REPORT="target/site/jacoco/index.xml"
OUTPUT_FILE="JACOCO_RESULTS.md"

if ! file_exists "$JACOCO_REPORT"; then
  log_warn "JaCoCo report not found"
  exit 0
fi

log_info "Generating JaCoCo report..."

{
  cat << 'EOF'
# Code Coverage Report

## Coverage Metrics

EOF

  overall=$(grep -oP 'COVERED="\K[0-9]+' "$JACOCO_REPORT" | head -1)
  missed=$(grep -oP 'MISSED="\K[0-9]+' "$JACOCO_REPORT" | head -1)

  if [ -n "$overall" ] && [ -n "$missed" ]; then
    total=$((overall + missed))
    coverage=$(echo "scale=1; ($overall * 100) / $total" | bc)

    cat << EOF
| Metric | Value |
|--------|-------|
| Lines Covered | $overall |
| Lines Missed | $missed |
| Total Lines | $total |
| Coverage | $coverage% |

EOF
  fi

  cat << 'EOF'
## Coverage by Source File

| File | Covered | Missed | Total | % |
|------|---------|--------|-------|---|
EOF

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

} > "$OUTPUT_FILE"

log_success "JaCoCo report: $OUTPUT_FILE"