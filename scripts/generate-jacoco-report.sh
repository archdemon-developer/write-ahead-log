#!/bin/bash

# Enhanced JaCoCo Report Generator (Pure Bash)
# Generates comprehensive coverage analysis with explanations

set -euo pipefail

XML_PATH="${1:-target/site/jacoco/jacoco.xml}"
OUTPUT_PATH="${2:-JACOCO_RESULTS.md}"

declare -A CRITICALITY=(
    [WriteAheadLog]=95 [SegmentWriter]=90 [SegmentReader]=95
    [SegmentMetadataRecovery]=100 [SegmentStoreManager]=85 [FsyncExecutor]=90
    [EveryEntryFsyncExecutor]=85 [EveryBatchFsyncExecutor]=85
)

calc_pct() {
    [ $((  $1 + $2)) -eq 0 ] && echo 0 || echo $(( ($1 * 100) / ($1 + $2) ))
}

extract_classes() {
    sed -E 's/<sourcefile/\n<sourcefile/g' "$XML_PATH" | grep "<sourcefile" | while read line; do
        name=$(echo "$line" | sed 's/.*name="\([^"]*\)\.java".*/\1/')
        covered=$(echo "$line" | grep -oP 'covered="\K[0-9]+' | head -1)
        missed=$(echo "$line" | grep -oP 'missed="\K[0-9]+' | head -1)
        [ -n "$covered" ] && [ -n "$missed" ] && [ $(( $covered + $missed )) -gt 0 ] && \
            echo "$name $covered $missed"
    done
}

main() {
    [ ! -f "$XML_PATH" ] && { echo "[ERROR] $XML_PATH not found" >&2; exit 1; }

    local overall_covered=$(grep -oP 'type="LINE".*?covered="\K[0-9]+' "$XML_PATH" | head -1)
    local overall_missed=$(grep -oP 'type="LINE".*?missed="\K[0-9]+' "$XML_PATH" | head -1)
    local overall_pct=$(calc_pct "$overall_covered" "$overall_missed")
    local total=$(( $overall_covered + $overall_missed ))

    {
        echo "# 📊 Code Coverage Analysis Report"
        echo ""
        echo "**Generated:** $(date -Iseconds)"
        echo "**Overall Coverage:** $overall_pct% ($overall_covered/$total lines tested)"
        echo ""

        if [ $overall_pct -ge 90 ]; then
            echo "**Status:** 🟢 EXCELLENT — Production-ready"
        elif [ $overall_pct -ge 80 ]; then
            echo "**Status:** 🟡 GOOD — Deploy with monitoring"
        else
            echo "**Status:** 🔴 POOR — Requires improvement before production"
        fi

        echo ""
        echo "---"
        echo ""
        echo "## Executive Summary"
        echo ""
        echo "Code coverage measures what percentage of your source code is executed by tests."
        echo "Higher coverage generally means lower risk of undetected bugs. This report shows"
        echo "which components need more test coverage and prioritizes by risk."
        echo ""
        echo "### Coverage Breakdown"
        echo "- **$overall_covered lines** are tested and verified to work"
        echo "- **$overall_missed lines** have no test coverage — potential gap in reliability"
        echo "- **${overall_pct}% coverage** means we have $overall_pct% confidence in tested behavior"
        echo ""

        if [ $overall_pct -ge 90 ]; then
            echo "### ✅ What This Means"
            echo "- Excellent coverage indicates mature, well-tested code"
            echo "- Most execution paths are verified to work correctly"
            echo "- Risk of undetected bugs is low"
            echo "- System is suitable for production deployment"
            echo "- Focus should be on maintaining current coverage level"
        elif [ $overall_pct -ge 80 ]; then
            echo "### ⚠️ What This Means"
            echo "- Good coverage, but some gaps exist ($((100 - overall_pct))% untested)"
            echo "- Most common paths are tested, but edge cases may not be"
            echo "- Some risk of undetected bugs in untested code"
            echo "- Recommended: Address gaps before critical deployment"
            echo "- Focus: Add tests for highest-risk components (see below)"
        else
            echo "### 🔴 What This Means"
            echo "- Significant gaps in test coverage ($((100 - overall_pct))% untested)"
            echo "- Many execution paths are unverified"
            echo "- High risk of undetected bugs"
            echo "- Serious risk if deployed to production"
            echo "- Urgent: Add tests for critical components before any deployment"
        fi

        echo ""
        echo "## Component-by-Component Analysis"
        echo ""
        echo "Risk is calculated as: **missed_lines × criticality_score / 100**"
        echo ""
        echo "Higher risk score = more important to test (either many missed lines or critical component)"
        echo ""
        echo "| Component | Coverage | Lines | Risk | Why It Matters |"
        echo "|-----------|----------|-------|------|----------------|"

        tmpfile=$(mktemp)
        while IFS=' ' read name covered missed; do
            pct=$(calc_pct "$covered" "$missed")
            crit=${CRITICALITY[$name]:-30}
            [ $crit -lt 15 ] && continue
            risk=$(( ($missed * $crit) / 100 ))
            echo "$risk|$name|$pct|$covered|$missed|$crit" >> "$tmpfile"
        done < <(extract_classes)

        sort -t'|' -k1 -rn "$tmpfile" | while IFS='|' read risk name pct covered missed crit; do
            icon="🟢"
            [ $pct -lt 90 ] && icon="🟡"
            [ $pct -lt 70 ] && icon="🔴"

            why_matters="Utility"
            [ "$name" = "SegmentMetadataRecovery" ] && why_matters="Critical for crash recovery"
            [ "$name" = "SegmentReader" ] && why_matters="Data validation & correctness"
            [ "$name" = "WriteAheadLog" ] && why_matters="Core API & concurrency"
            [ "$name" = "FsyncExecutor" ] && why_matters="Durability guarantee"

            echo "| $icon $name | $pct% | $covered/$((covered + missed)) | $risk | $why_matters |"
        done

        rm -f "$tmpfile"

        echo ""
        echo "## What Untested Code Means"
        echo ""
        echo "Each untested line represents a code path that has never been executed by tests."
        echo "This creates risk in several areas:"
        echo ""
        echo "### Logic Errors"
        echo "Code paths that are never executed can contain bugs that are never caught."
        echo "Example: An error handling branch that's never tested may fail when actually needed."
        echo ""
        echo "### Edge Cases"
        echo "Untested code often includes edge case handling (empty files, boundary conditions)."
        echo "These are exactly where bugs hide (off-by-one errors, null pointer exceptions)."
        echo ""
        echo "### Integration Issues"
        echo "Code might work in isolation but fail when integrated with other components."
        echo "Only tests can verify integration works correctly."
        echo ""
        echo "### Maintenance Risk"
        echo "When modifying untested code later, you can easily break it without knowing."
        echo "Tests would catch this; untested code won't."
        echo ""
        echo "## Recommended Actions"
        echo ""

        if [ $overall_pct -lt 90 ]; then
            echo "### 1. Target High-Risk Components First"
            echo "   Focus on components with:"
            echo "   - High criticality (crash recovery, durability, core API)"
            echo "   - Many missed lines (low coverage %)"
            echo "   - Both"
            echo ""
            echo "### 2. Typical Untested Scenarios"
            echo "   Add tests for:"
            echo "   - **Error cases:** Disk full, I/O errors, permission denied"
            echo "   - **Crash recovery:** What if power fails mid-operation?"
            echo "   - **Edge cases:** Empty files, boundary conditions, max sizes"
            echo "   - **Concurrency:** Multiple threads accessing simultaneously"
            echo "   - **State transitions:** State machine edge cases"
            echo ""
            echo "### 3. Expected Impact"
            echo "   Each untested line you add a test for:"
            echo "   - Catches potential bugs early"
            echo "   - Prevents regressions from future changes"
            echo "   - Increases confidence in reliability"
            echo "   - Reduces production incidents"
        else
            echo "### Continue Strong"
            echo "- Maintain 90%+ coverage going forward"
            echo "- Add tests before implementing new features"
            echo "- Monitor for coverage regressions in CI/CD"
        fi

        echo ""
        echo "## Coverage Targets"
        echo ""
        echo "| Threshold | Meaning | Action |"
        echo "|-----------|---------|--------|"
        echo "| 🟢 90%+ | Excellent | Production ready, maintain current level |"
        echo "| 🟡 80-89% | Good | Deploy with monitoring, add tests for gaps |"
        echo "| 🔴 <80% | Poor | Risky, add tests before deployment |"

    } > "$OUTPUT_PATH"

    echo "[SUCCESS] JaCoCo report: $OUTPUT_PATH"
}

main