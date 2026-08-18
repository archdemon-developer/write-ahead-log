#!/bin/bash

set -x  # Debug mode: print all commands

source scripts/report-utils.sh

JACOCO_REPORT="target/site/jacoco/jacoco.xml"
OUTPUT_FILE="JACOCO_RESULTS.md"

log_info "=== JACOCO REPORT GENERATION START ==="
log_info "JACOCO_REPORT=$JACOCO_REPORT"
log_info "OUTPUT_FILE=$OUTPUT_FILE"
log_info "Current directory: $(pwd)"

log_info "=== Checking file existence ==="
if ! file_exists "$JACOCO_REPORT"; then
  log_error "JACOCO FILE NOT FOUND: $JACOCO_REPORT"
  log_info "Contents of target/site/jacoco/:"
  ls -la target/site/jacoco/ || log_error "Directory doesn't exist"
  exit 0
fi
log_success "JaCoCo file found"

log_info "=== Reading file size ==="
wc -l "$JACOCO_REPORT"
file "$JACOCO_REPORT"

log_info "=== Extracting coverage values ==="
log_info "Running: grep -oP 'COVERED=\"\\K[0-9]+' \"$JACOCO_REPORT\" | head -1"
overall=$(grep -oP 'COVERED="\K[0-9]+' "$JACOCO_REPORT" | head -1)
log_info "overall=$overall"

log_info "Running: grep -oP 'MISSED=\"\\K[0-9]+' \"$JACOCO_REPORT\" | head -1"
missed=$(grep -oP 'MISSED="\K[0-9]+' "$JACOCO_REPORT" | head -1)
log_info "missed=$missed"

if [ -z "$overall" ]; then
  log_error "Failed to extract COVERED value"
  log_info "First 100 lines of file:"
  head -100 "$JACOCO_REPORT"
  exit 1
fi

if [ -z "$missed" ]; then
  log_error "Failed to extract MISSED value"
  exit 1
fi

log_info "Values extracted successfully: overall=$overall, missed=$missed"

log_info "=== Creating markdown file ==="
log_info "Writing to: $OUTPUT_FILE"

cat > "$OUTPUT_FILE" << EOF
# Code Coverage Report

## Coverage Metrics

| Metric | Value |
|--------|-------|
| Lines Covered | $overall |
| Lines Missed | $missed |
| Total Lines | $((overall + missed)) |

## Coverage by Source File

| File | Covered | Missed | Total | % |
|------|---------|--------|-------|---|
EOF

log_success "Header written"

log_info "=== Extracting source file coverage ==="
log_info "Running: grep -E '<sourcefile' \"$JACOCO_REPORT\" | head -20"
sourcefile_count=$(grep -E '<sourcefile' "$JACOCO_REPORT" | head -20 | wc -l)
log_info "Found $sourcefile_count sourcefile entries"

grep -E '<sourcefile' "$JACOCO_REPORT" | head -20 | while IFS= read -r line; do
  log_info "Processing line: $line"

  filename=$(echo "$line" | grep -oP 'name="\K[^"]+')
  log_info "  filename=$filename"

  covered=$(echo "$line" | grep -oP 'COVERED="\K[0-9]+')
  log_info "  covered=$covered"

  missed=$(echo "$line" | grep -oP 'MISSED="\K[0-9]+')
  log_info "  missed=$missed"

  if [ -n "$covered" ] && [ -n "$missed" ]; then
    total=$((covered + missed))
    pct=$((covered * 100 / total))
    row="| $filename | $covered | $missed | $total | $pct% |"
    log_info "  Writing row: $row"
    echo "$row" >> "$OUTPUT_FILE"
  else
    log_warn "  Skipping: missing covered or missed"
  fi
done

log_info "=== Verifying output file ==="
if file_exists "$OUTPUT_FILE"; then
  log_success "Output file created: $OUTPUT_FILE"
  log_info "File contents:"
  cat "$OUTPUT_FILE"
else
  log_error "Output file NOT CREATED: $OUTPUT_FILE"
  exit 1
fi

log_success "JaCoCo report generation complete"