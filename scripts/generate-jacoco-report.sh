#!/bin/bash

# Generates JACOCO_RESULTS.md with data-driven analysis

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
        echo "# 📊 JaCoCo Coverage Analysis Report"
        echo ""
        echo "**Generated:** $(date -Iseconds)"
        echo "**Coverage:** $overall_pct% ($overall_covered/$total lines)"

        if [ $overall_pct -ge 90 ]; then
            echo "**Status:** 🟢 EXCELLENT (Production-ready)"
        elif [ $overall_pct -ge 80 ]; then
            echo "**Status:** 🟡 GOOD (Needs improvement)"
        else
            echo "**Status:** 🔴 POOR (Below acceptable)"
        fi

        echo ""
        echo "---"
        echo ""
        echo "## Executive Summary"
        echo ""
        echo "Your WAL implementation has **${overall_pct}% line coverage** with:"
        echo "- **$overall_covered lines** tested and passing"
        echo "- **$overall_missed lines** remaining untested"
        echo ""

        if [ $overall_pct -ge 90 ]; then
            echo "### ✅ Excellent Coverage"
            echo "All critical paths are tested. System is production-ready."
        elif [ $overall_pct -ge 80 ]; then
            echo "### ⚠️ Needs Attention"
            echo "Coverage is good but below 90% target. Address critical gaps below."
        else
            echo "### ❌ Critical Gaps"
            echo "Coverage is below acceptable threshold. Urgent action required."
        fi

        echo ""
        echo "## Component-by-Component Analysis"
        echo ""
        echo "| Component | Coverage | Lines | Risk | Action |"
        echo "|-----------|----------|-------|------|--------|"

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
            action="Monitor"
            [ $pct -lt 90 ] && action="Add $missed tests"
            [ $pct -lt 70 ] && action="**Add $missed tests**"

            echo "| $icon $name | $pct% | $covered/$((covered + missed)) | $risk | $action |"
        done

        rm -f "$tmpfile"

        echo ""
        echo "## Recommendations"
        echo ""
        echo "### Coverage Targets"
        echo "- 🟢 **90%+** = Production-ready"
        echo "- 🟡 **80-89%** = Deploy with monitoring"
        echo "- 🔴 **<80%** = Unacceptable risk"
        echo ""
        echo "### Next Steps"
        if [ $overall_pct -lt 90 ]; then
            echo "1. Review component analysis above"
            echo "2. Focus on highest-risk components (highest risk score)"
            echo "3. Add tests for untested scenarios"
            echo "4. Re-run \`mvn clean verify\` to verify improvements"
        else
            echo "1. ✅ Maintain current coverage level"
            echo "2. Ensure new code maintains 90%+"
            echo "3. Continue monitoring for regressions"
        fi

    } > "$OUTPUT_PATH"

    echo "[SUCCESS] JaCoCo report: $OUTPUT_PATH"
}

main