#!/bin/bash

# Generates TEST_RESULTS.md with test quality analysis

set -euo pipefail

SUREFIRE_DIR="${1:-target/surefire-reports}"
OUTPUT_PATH="${2:-TEST_RESULTS.md}"

count_tests() {
    find "$SUREFIRE_DIR" -name "*.xml" -type f 2>/dev/null | while read f; do
        grep -c "testcase" "$f" 2>/dev/null || echo 0
    done | awk '{s+=$1} END {print s+0}'
}

count_passed() {
    find "$SUREFIRE_DIR" -name "*.xml" -type f 2>/dev/null | while read f; do
        grep "testcase" "$f" | grep -v "<failure\|<error\|<skipped" | wc -l
    done | awk '{s+=$1} END {print s+0}'
}

count_failed() {
    find "$SUREFIRE_DIR" -name "*.xml" -type f 2>/dev/null | while read f; do
        grep -c "<failure\|<error" "$f" 2>/dev/null || echo 0
    done | awk '{s+=$1} END {print s+0}'
}

main() {
    {
        echo "# 🧪 Test Results Report"
        echo ""
        echo "**Generated:** $(date -Iseconds)"
        echo ""

        if [ ! -d "$SUREFIRE_DIR" ] || [ -z "$(find $SUREFIRE_DIR -name '*.xml' 2>/dev/null | head -1)" ]; then
            echo "No test results available."
            echo ""
            echo "Run: \`mvn clean verify\` to execute tests."
            return
        fi

        local total=$(count_tests)
        local passed=$(count_passed)
        local failed=$(count_failed)
        local pass_rate=0

        [ $total -gt 0 ] && pass_rate=$((passed * 100 / total))

        echo "---"
        echo ""
        echo "## Test Execution Summary"
        echo ""
        echo "| Metric | Value |"
        echo "|--------|-------|"
        echo "| Total Tests | $total |"
        echo "| Passed | $passed ✅ |"
        echo "| Failed | $failed ❌ |"
        echo "| Pass Rate | ${pass_rate}% |"
        echo ""

        if [ $failed -eq 0 ]; then
            echo "**Status:** 🟢 All tests passing"
        else
            echo "**Status:** 🔴 $failed tests failing"
        fi

        echo ""
        echo "## Test Coverage by Category"
        echo ""
        echo "| Category | Count | Status |"
        echo "|----------|-------|--------|"

        crash=$(find "$SUREFIRE_DIR" -name "*.xml" -type f 2>/dev/null | xargs grep -l "crash\|recovery\|restart" 2>/dev/null | wc -l)
        corruption=$(find "$SUREFIRE_DIR" -name "*.xml" -type f 2>/dev/null | xargs grep -l "corrupt\|checksum\|crc" 2>/dev/null | wc -l)
        concurrency=$(find "$SUREFIRE_DIR" -name "*.xml" -type f 2>/dev/null | xargs grep -l "concurrent\|thread\|stress" 2>/dev/null | wc -l)
        error=$(find "$SUREFIRE_DIR" -name "*.xml" -type f 2>/dev/null | xargs grep -l "error\|exception" 2>/dev/null | wc -l)

        [ $crash -gt 0 ] && echo "| ✅ Crash & Recovery | Multiple | Covered |" || echo "| ⚠️ Crash & Recovery | 0 | MISSING |"
        [ $corruption -gt 0 ] && echo "| ✅ Corruption | Multiple | Covered |" || echo "| ⚠️ Corruption | 0 | MISSING |"
        [ $concurrency -gt 0 ] && echo "| ✅ Concurrency | Multiple | Covered |" || echo "| ⚠️ Concurrency | 0 | MISSING |"
        [ $error -gt 2 ] && echo "| ✅ Error Handling | Multiple | Covered |" || echo "| ⚠️ Error Handling | $error | INSUFFICIENT |"

        echo ""
        echo "## Test Quality Assessment"
        echo ""

        if [ $failed -eq 0 ]; then
            echo "### ✅ All Tests Passing"
            echo "System is stable. No broken tests."
        else
            echo "### ❌ Failing Tests Detected"
            echo "**Action Required:** Fix failing tests before proceeding."
            echo ""
            echo "Debug with: \`mvn test -X -Dtest=FailingTestClass\`"
        fi

        echo ""

        if [ $crash -eq 0 ]; then
            echo "### 🔴 No Crash Recovery Tests"
            echo "**Issue:** Crash recovery is untested — critical for WAL reliability."
            echo "**Action:** Add minimum 8 crash recovery test cases."
            echo ""
        fi

        if [ $error -lt 3 ]; then
            echo "### 🟡 Insufficient Error Handling Tests"
            echo "**Current:** $error error tests"
            echo "**Needed:** 6+ error scenario tests"
            echo "**Missing Scenarios:** Disk full, permissions, I/O timeout"
            echo ""
        fi

        echo "## Recommendations"
        echo ""
        if [ $failed -gt 0 ]; then
            echo "1. 🔴 **CRITICAL:** Fix $failed failing test(s) immediately"
        else
            echo "1. ✅ **GOOD:** All tests passing"
        fi

        if [ $crash -eq 0 ]; then
            echo "2. 🔴 **URGENT:** Add crash recovery tests (min 8 cases)"
        else
            echo "2. ✅ Crash recovery tested"
        fi

        if [ $concurrency -eq 0 ]; then
            echo "3. 🟡 **IMPORTANT:** Add concurrent access stress tests"
        else
            echo "3. ✅ Concurrency tested"
        fi

        echo ""
        echo "### Next Steps"
        echo ""
        echo "- Run: \`mvn clean verify\` frequently to catch regressions"
        echo "- Aim for: 90%+ code coverage"
        echo "- Monitor: Test pass rate (target: 100%)"
        echo "- Add tests for: Each new feature before implementation"

    } > "$OUTPUT_PATH"

    echo "[SUCCESS] Test report: $OUTPUT_PATH"
}

main