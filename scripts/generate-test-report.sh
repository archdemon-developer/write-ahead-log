#!/bin/bash

# Comprehensive Test Report Generator (Pure Bash)
# Parses Maven test results and generates quality analysis

set -euo pipefail

OUTPUT_PATH="${1:-TEST_RESULTS.md}"
SUREFIRE_DIR="target/surefire-reports"

count_tests() {
    find "$SUREFIRE_DIR" -name "*.xml" -type f 2>/dev/null | while read f; do
        grep -c "<testcase" "$f" 2>/dev/null || echo 0
    done | awk '{s+=$1} END {print s+0}'
}

count_passed() {
    find "$SUREFIRE_DIR" -name "*.xml" -type f 2>/dev/null | while read f; do
        grep "<testcase" "$f" 2>/dev/null | grep -c -v "<failure\|<error\|<skipped" || echo 0
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

        # Check if tests have been run
        if [ ! -d "$SUREFIRE_DIR" ] || [ -z "$(find "$SUREFIRE_DIR" -name '*.xml' 2>/dev/null | head -1)" ]; then
            echo "## Test Results"
            echo ""
            echo "No test results found. To generate test results, run:"
            echo ""
            echo "\`\`\`bash"
            echo "mvn clean verify"
            echo "\`\`\`"
            echo ""
            echo "This will:"
            echo "1. Clean previous builds"
            echo "2. Compile the code"
            echo "3. Run all unit tests"
            echo "4. Run all integration tests"
            echo "5. Generate JaCoCo coverage reports"
            echo "6. Generate Surefire test reports in \`target/surefire-reports/\`"
            echo ""
            echo "After running Maven, re-generate this report:"
            echo ""
            echo "\`\`\`bash"
            echo "bash scripts/generate-test-report.sh"
            echo "\`\`\`"
            return
        fi

        echo "---"
        echo ""
        echo "## Executive Summary"
        echo ""
        echo "Tests are your safety net. They catch bugs before users see them. This report"
        echo "shows test execution results and identifies quality gaps."
        echo ""

        local total=$(count_tests)
        local passed=$(count_passed)
        local failed=$(count_failed)
        local pass_rate=0

        [ $total -gt 0 ] && pass_rate=$((passed * 100 / total))

        echo "### Test Results"
        echo ""
        echo "- **$total total tests** executed"
        echo "- **$passed tests passed** ✅"
        echo "- **$failed tests failed** ❌"
        echo "- **${pass_rate}% pass rate**"
        echo ""

        if [ $failed -eq 0 ] && [ $pass_rate -eq 100 ]; then
            echo "### ✅ Excellent Status"
            echo ""
            echo "All $total tests passing! Your code works as designed. This is a strong"
            echo "confidence signal. Continue adding tests as you implement new features."
        elif [ $failed -lt 5 ]; then
            echo "### ⚠️ Minor Issues"
            echo ""
            echo "$failed tests are failing. Fix these before pushing to production."
        else
            echo "### 🔴 Critical Issues"
            echo ""
            echo "$failed tests failing! Do not deploy until fixed."
        fi

        echo ""
        echo "## Test Quality Analysis"
        echo ""
        echo "Different test types catch different bugs:"
        echo ""
        echo "| Type | What It Tests | Status |"
        echo "|------|---------------|--------|"

        # Count test types by grepping for keywords in test class names
        crash_files=$(find "$SUREFIRE_DIR" -name "*Crash*Test.xml" -o -name "*Recovery*Test.xml" -o -name "*Restart*Test.xml" 2>/dev/null | wc -l)
        corrupt_files=$(find "$SUREFIRE_DIR" -name "*Corrupt*Test.xml" -o -name "*Checksum*Test.xml" -o -name "*Crc*Test.xml" 2>/dev/null | wc -l)
        concur_files=$(find "$SUREFIRE_DIR" -name "*Concurrent*Test.xml" -o -name "*Thread*Test.xml" -o -name "*Stress*Test.xml" -o -name "*Concurrency*Test.xml" 2>/dev/null | wc -l)
        error_files=$(find "$SUREFIRE_DIR" -name "*Error*Test.xml" -o -name "*Exception*Test.xml" -o -name "*Handle*Test.xml" 2>/dev/null | wc -l)
        edge_files=$(find "$SUREFIRE_DIR" -name "*Edge*Test.xml" -o -name "*Boundary*Test.xml" 2>/dev/null | wc -l)

        [ $crash_files -gt 0 ] && echo "| Crash & Recovery | Power loss, restart | ✅ Tested |" || echo "| Crash & Recovery | Power loss, restart | 🔴 MISSING |"
        [ $corrupt_files -gt 0 ] && echo "| Corruption | Data integrity | ✅ Tested |" || echo "| Corruption | Data integrity | 🔴 MISSING |"
        [ $concur_files -gt 0 ] && echo "| Concurrency | Thread safety | ✅ Tested |" || echo "| Concurrency | Thread safety | 🔴 MISSING |"
        [ $error_files -gt 2 ] && echo "| Error Handling | Disk errors, timeouts | ✅ Tested |" || echo "| Error Handling | Disk errors, timeouts | ⚠️ Only $error_files |"
        [ $edge_files -gt 0 ] && echo "| Edge Cases | Boundaries, empty | ✅ Tested |" || echo "| Edge Cases | Boundaries, empty | ⚠️ MISSING |"

        echo ""
        echo "## Critical Gaps Analysis"
        echo ""

        if [ $crash_files -eq 0 ]; then
            echo "### 🔴 CRITICAL: No Crash Recovery Tests"
            echo ""
            echo "Crash recovery is the core feature of a WAL. Without tests, you have no"
            echo "guarantee that data survives power loss. This is a high-risk gap."
            echo ""
            echo "**Create test file:** \`CrashRecoveryTest.java\` with 8+ tests:"
            echo ""
            echo "1. \`testKillProcessMidWrite\` — Kill during write, verify recovery"
            echo "2. \`testDiskErrorRecovery\` — Simulate I/O error, verify handling"
            echo "3. \`testRandomCrashScenarios\` — Write batches, crash randomly, verify"
            echo "4. \`testCorruptSegmentFooterRecovery\` — Corrupted footer, recovery works"
            echo "5. \`testMissingSegmentRecovery\` — Segment deleted, recovery handles it"
            echo "6. \`testPartialWriteRecovery\` — Incomplete write, recovery skips it"
            echo "7. \`testPowerLossSimulation\` — Simulate power loss at various points"
            echo "8. \`testRecoveryFromCorruptedMetadata\` — Metadata corruption handled"
            echo ""
        fi

        if [ $corrupt_files -eq 0 ]; then
            echo "### 🔴 CRITICAL: No Corruption Detection Tests"
            echo ""
            echo "Data corruption is the #1 threat to reliability. Tests must verify"
            echo "that corrupted data is detected and reported."
            echo ""
            echo "**Create test file:** \`CorruptionDetectionTest.java\` with 6+ tests:"
            echo ""
            echo "1. \`testCorruptedEntryChecksum\` — CRC mismatch detected"
            echo "2. \`testCorruptedHeaderDetection\` — Header corruption caught"
            echo "3. \`testCorruptedFooterDetection\` — Footer corruption caught"
            echo "4. \`testCorruptedMetadata\` — Metadata CRC validation"
            echo "5. \`testPartialWriteDetection\` — Incomplete write detected"
            echo "6. \`testMultipleCorruptedSegments\` — Multiple corrupted segments handled"
            echo ""
        fi

        if [ $error_files -lt 3 ]; then
            echo "### 🟡 IMPORTANT: Insufficient Error Handling Tests"
            echo ""
            echo "**Current:** $error_files error-related tests"
            echo "**Recommended:** 6+ tests"
            echo ""
            echo "**Create test file:** \`ErrorHandlingTest.java\` with tests for:"
            echo ""
            echo "1. \`testDiskFullError\` — ENOSPC handled gracefully"
            echo "2. \`testPermissionDenied\` — EACCES handled gracefully"
            echo "3. \`testIOTimeout\` — Timeout errors handled"
            echo "4. \`testCorruptedMetadataHandling\` — Invalid metadata"
            echo "5. \`testMissingSegmentFile\` — File not found"
            echo "6. \`testInvalidSegmentFormat\` — Malformed segment"
            echo ""
        fi

        if [ $failed -gt 0 ]; then
            echo "### 🚨 URGENT: Fix $failed Failing Test(s)"
            echo ""
            echo "**Before doing anything else:**"
            echo ""
            echo "1. Review failing tests in \`target/surefire-reports/\`"
            echo "2. Determine root cause:"
            echo "   - Is the code broken? (fix implementation)"
            echo "   - Is the test broken? (fix test expectations)"
            echo "   - Is the environment wrong? (fix test setup)"
            echo "3. Fix the issue"
            echo "4. Re-run: \`mvn clean verify\`"
            echo "5. Re-generate this report: \`bash scripts/generate-test-report.sh\`"
            echo ""
        fi

        echo "## Test Coverage Quality Checklist"
        echo ""
        echo "- [$([ $failed -eq 0 ] && echo 'x' || echo ' ')] All tests passing"
        echo "- [$([ $crash_files -gt 0 ] && echo 'x' || echo ' ')] Crash recovery tested"
        echo "- [$([ $corrupt_files -gt 0 ] && echo 'x' || echo ' ')] Corruption tested"
        echo "- [$([ $concur_files -gt 0 ] && echo 'x' || echo ' ')] Concurrency tested"
        echo "- [$([ $error_files -gt 2 ] && echo 'x' || echo ' ')] Error handling tested"
        echo "- [$([ $edge_files -gt 0 ] && echo 'x' || echo ' ')] Edge cases tested"
        echo ""

        if [ $failed -eq 0 ] && [ $crash_files -gt 0 ] && [ $corrupt_files -gt 0 ] && [ $error_files -gt 2 ]; then
            echo "✅ **TEST SUITE IS SOLID** — Good quality, high confidence"
        else
            echo "⚠️ **GAPS REMAIN** — Address them above"
        fi

    } > "$OUTPUT_PATH"

    echo "[SUCCESS] Test report: $OUTPUT_PATH"
}

main