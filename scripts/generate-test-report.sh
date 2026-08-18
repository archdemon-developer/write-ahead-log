#!/bin/bash

# Enhanced Test Report Generator (Pure Bash)
# Generates comprehensive test quality analysis with explanations

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
        echo "# 🧪 Test Execution & Quality Report"
        echo ""
        echo "**Generated:** $(date -Iseconds)"
        echo ""

        if [ ! -d "$SUREFIRE_DIR" ] || [ -z "$(find $SUREFIRE_DIR -name '*.xml' 2>/dev/null | head -1)" ]; then
            echo "No test results available. Run: \`mvn clean verify\` to execute tests."
            return
        fi

        local total=$(count_tests)
        local passed=$(count_passed)
        local failed=$(count_failed)
        local pass_rate=0

        [ $total -gt 0 ] && pass_rate=$((passed * 100 / total))

        echo "---"
        echo ""
        echo "## Executive Summary"
        echo ""
        echo "Tests are your safety net. They catch bugs before users see them. This report"
        echo "shows test execution results and identifies quality gaps."
        echo ""
        echo "### Test Results"
        echo "- **$total total tests** written and executed"
        echo "- **$passed tests passing** ✅"
        echo "- **$failed tests failing** ❌"
        echo "- **${pass_rate}% pass rate**"
        echo ""

        if [ $failed -eq 0 ] && [ $pass_rate -eq 100 ]; then
            echo "### ✅ Excellent Status"
            echo "All tests passing! Your code is working as designed. Continue adding tests"
            echo "as you implement new features to maintain this quality."
        elif [ $failed -lt 5 ]; then
            echo "### ⚠️ Minor Issues"
            echo "$failed tests are failing. Fix these before pushing to production."
        else
            echo "### 🔴 Critical Issues"
            echo "$failed tests failing. Do not deploy until these are fixed."
        fi

        echo ""
        echo "## Test Execution Details"
        echo ""
        echo "| Metric | Value | Meaning |"
        echo "|--------|-------|---------|"
        echo "| Total Tests | $total | Number of test methods executed |"
        echo "| Passing | $passed | Tests that passed (0 errors) |"
        echo "| Failing | $failed | Tests that failed (has errors) |"
        echo "| Pass Rate | ${pass_rate}% | (Passing / Total) × 100 |"
        echo ""

        if [ $pass_rate -lt 100 ]; then
            echo "### What Failed Tests Mean"
            echo ""
            echo "**Broken Functionality:** Each failing test indicates a feature or code path"
            echo "that does not work as expected. The test describes what *should* happen; the"
            echo "failure means the actual code does not match this expectation."
            echo ""
            echo "**Blocking Issues:** Failing tests should BLOCK deployment. Do not push broken"
            echo "code to production. Fix tests or fix code until all pass."
            echo ""
            echo "**Root Cause:** Each failure has a root cause:"
            echo "- **Code bug** — implementation has a logic error"
            echo "- **Test bug** — test expectation is wrong"
            echo "- **Environment** — test assumes something not available (file, network)"
            echo ""
            echo "**How to Fix:**"
            echo "1. Read the failure message"
            echo "2. Determine which of the above is the root cause"
            echo "3. Fix either the test or the code"
            echo "4. Re-run: \`mvn clean verify\`"
            echo "5. Repeat until all pass"
        fi

        echo ""
        echo "## Test Coverage by Category"
        echo ""
        echo "Different types of tests catch different kinds of bugs:"
        echo ""
        echo "| Category | What It Tests | Example |"
        echo "|----------|---------------|---------|"
        echo "| **Crash & Recovery** | System survives power loss | Write entry, kill process, verify recovery |"
        echo "| **Corruption** | Data integrity on disk errors | Corrupt segment, verify detection |"
        echo "| **Concurrency** | Multiple threads don't race | 16 threads writing simultaneously |"
        echo "| **Error Handling** | Handles errors gracefully | Disk full, permission denied |"
        echo "| **Edge Cases** | Boundary conditions work | Empty files, max sizes |"
        echo "| **Happy Path** | Normal operation works | Simple write/read operations |"
        echo ""

        crash=$(find "$SUREFIRE_DIR" -name "*.xml" -type f 2>/dev/null | xargs grep -l "crash\|recovery\|restart" 2>/dev/null | wc -l)
        corruption=$(find "$SUREFIRE_DIR" -name "*.xml" -type f 2>/dev/null | xargs grep -l "corrupt\|checksum\|crc" 2>/dev/null | wc -l)
        concurrency=$(find "$SUREFIRE_DIR" -name "*.xml" -type f 2>/dev/null | xargs grep -l "concurrent\|thread\|stress" 2>/dev/null | wc -l)
        error=$(find "$SUREFIRE_DIR" -name "*.xml" -type f 2>/dev/null | xargs grep -l "error\|exception" 2>/dev/null | wc -l)

        echo "### Your Coverage"
        echo ""
        [ $crash -gt 0 ] && echo "✅ **Crash & Recovery** — Tested" || echo "❌ **Crash & Recovery** — MISSING (Critical!)"
        [ $corruption -gt 0 ] && echo "✅ **Corruption Detection** — Tested" || echo "❌ **Corruption Detection** — MISSING"
        [ $concurrency -gt 0 ] && echo "✅ **Concurrency** — Tested" || echo "❌ **Concurrency** — MISSING"
        [ $error -gt 2 ] && echo "✅ **Error Handling** — Tested" || echo "⚠️ **Error Handling** — Insufficient ($error tests)"

        echo ""
        echo "## Recommended Actions"
        echo ""

        if [ $failed -gt 0 ]; then
            echo "### 🚨 URGENT: Fix Failing Tests"
            echo ""
            echo "Before doing anything else:"
            echo "1. Run: \`mvn clean verify\`"
            echo "2. Read failure messages in \`target/surefire-reports/\`"
            echo "3. Fix the broken code or tests"
            echo "4. Re-run until all pass"
            echo "5. Only then work on other improvements"
            echo ""
        fi

        if [ $crash -eq 0 ]; then
            echo "### 🔴 CRITICAL GAP: No Crash Recovery Tests"
            echo ""
            echo "**Why this matters:** Crash recovery is the core feature of a WAL. Without tests,"
            echo "you have no guarantee that data survives power loss."
            echo ""
            echo "**What to add (minimum 8 tests):**"
            echo "- Kill process mid-write, verify recovery"
            echo "- Simulate disk error, verify graceful handling"
            echo "- Write multiple batches, crash randomly, verify all data"
            echo "- Test recovery with corrupted segment footer"
            echo ""
            echo "**Impact:** Adding these tests will give you confidence that your system"
            echo "actually survives crashes (it might not currently!)."
            echo ""
        fi

        if [ $error -lt 3 ]; then
            echo "### 🟡 IMPORTANT GAP: Insufficient Error Handling Tests"
            echo ""
            echo "**Current:** $error error-related tests"
            echo "**Recommended:** 6+ tests for error scenarios"
            echo ""
            echo "**What to test:**"
            echo "- Disk full (ENOSPC) — can't write"
            echo "- Permission denied (EACCES) — no access"
            echo "- I/O timeout — slow disk"
            echo "- Corrupted metadata — detection"
            echo ""
            echo "**Impact:** Systems fail in production due to untested error paths."
            echo ""
        fi

        echo "## Test Quality Checklist"
        echo ""
        echo "- [$([ $failed -eq 0 ] && echo 'x' || echo ' ')] All tests passing"
        echo "- [$([ $crash -gt 0 ] && echo 'x' || echo ' ')] Crash recovery tested"
        echo "- [$([ $error -gt 2 ] && echo 'x' || echo ' ')] Error handling tested"
        echo "- [$([ $concurrency -gt 0 ] && echo 'x' || echo ' ')] Concurrency tested"
        echo "- [$([ $corruption -gt 0 ] && echo 'x' || echo ' ')] Corruption tested"
        echo ""

        if [ $failed -eq 0 ] && [ $crash -gt 0 ] && [ $error -gt 2 ]; then
            echo "✅ **TEST SUITE IS SOLID**"
        else
            echo "⚠️ **GAPS REMAIN** — See recommendations above"
        fi

    } > "$OUTPUT_PATH"

    echo "[SUCCESS] Test report: $OUTPUT_PATH"
}

main