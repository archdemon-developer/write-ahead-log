#!/bin/bash

source scripts/report-utils.sh

JACOCO_REPORT="target/site/jacoco/jacoco.xml"
OUTPUT_FILE="JACOCO_RESULTS.md"

log_info "=== JACOCO REPORT GENERATION START ==="
log_info "JACOCO_REPORT=$JACOCO_REPORT"
log_info "OUTPUT_FILE=$OUTPUT_FILE"
log_info "Current directory: $(pwd)"

log_info "=== Checking file existence ==="
if ! file_exists "$JACOCO_REPORT"; then
  log_error "JaCoCo report not found at $JACOCO_REPORT"
  exit 1
fi

log_success "JaCoCo file found"

log_info "=== Reading file size ==="
wc -l "$JACOCO_REPORT"
file "$JACOCO_REPORT"

log_info "=== Extracting coverage values ==="

# Extract OVERALL coverage (first counter with type LINE at report level)
# The root <report> element has a <counter> with type="LINE"
log_info 'Running: grep -oP "covered=\"\\K[0-9]+" "$JACOCO_REPORT" | head -1'
overall=$(grep -oP 'covered="\K[0-9]+' "$JACOCO_REPORT" | head -1)

log_info "overall=$overall"

log_info 'Running: grep -oP "missed=\"\\K[0-9]+" "$JACOCO_REPORT" | head -1'
missed=$(grep -oP 'missed="\K[0-9]+' "$JACOCO_REPORT" | head -1)

log_info "missed=$missed"

if [ -z "$overall" ] || [ -z "$missed" ]; then
  log_error "Failed to extract coverage values"
  log_info "First 100 lines of file:"
  head -100 "$JACOCO_REPORT"
  exit 1
fi

log_success "Extracted overall=$overall, missed=$missed"

# Calculate percentage
total=$((overall + missed))
if [ "$total" -gt 0 ]; then
  percentage=$((overall * 100 / total))
else
  percentage=0
fi

log_info "Calculated coverage: $percentage% ($overall covered, $missed missed)"

# Create markdown report
cat > "$OUTPUT_FILE" << EOF
# JaCoCo Code Coverage Report

## Overall Coverage

| Metric | Value |
|--------|-------|
| **Coverage %** | $percentage% |
| **Lines Covered** | $overall |
| **Lines Missed** | $missed |
| **Total Lines** | $total |

## Package-Level Coverage

EOF

# Extract package-level coverage
log_info "Extracting package-level coverage..."
grep -E '<package name=' "$JACOCO_REPORT" | while IFS= read -r line; do
  # Extract package name
  pkgname=$(echo "$line" | grep -oP 'name="\K[^"]+')

  # Get the counter for this package (first counter element following the package)
  counters=$(echo "$line" | grep -oP '<counter[^>]*>' | head -1)

  if [ -n "$counters" ]; then
    pkg_covered=$(echo "$counters" | grep -oP 'covered="\K[0-9]+')
    pkg_missed=$(echo "$counters" | grep -oP 'missed="\K[0-9]+')

    if [ -n "$pkg_covered" ] && [ -n "$pkg_missed" ]; then
      pkg_total=$((pkg_covered + pkg_missed))
      if [ "$pkg_total" -gt 0 ]; then
        pkg_pct=$((pkg_covered * 100 / pkg_total))
      else
        pkg_pct=0
      fi
      echo "| $pkgname | $pkg_pct% | $pkg_covered | $pkg_missed |" >> "$OUTPUT_FILE"
    fi
  fi
done

log_success "JaCoCo report generated: $OUTPUT_FILE"