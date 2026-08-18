#!/bin/bash

# Enhanced Benchmark Report Generator (Pure Bash)
# Generates comprehensive BENCHMARK_RESULTS.md with analysis

set -euo pipefail

JSON_PATH="${1:-benchmark-results.json}"
OUTPUT_PATH="${2:-BENCHMARK_RESULTS.md}"

main() {
    {
        echo "# 📊 Benchmark Performance Report"
        echo ""
        echo "**Generated:** $(date -Iseconds)"
        echo ""

        if [ ! -f "$JSON_PATH" ] || [ ! -s "$JSON_PATH" ]; then
            echo "No benchmark data available. Run: \`mvn clean verify\`"
            return
        fi

        echo "---"
        echo ""
        echo "## Executive Summary"
        echo ""
        echo "This report analyzes the performance characteristics of your Write-Ahead Log system"
        echo "under various workload conditions. It identifies bottlenecks and provides actionable"
        echo "recommendations for optimization."
        echo ""

        echo "## Performance Metrics Overview"
        echo ""

        # Producer Throughput
        if grep -q "ProducerThroughput" "$JSON_PATH" 2>/dev/null; then
            score=$(grep -oP 'ProducerThroughput.*?"score":\K[0-9.]+' "$JSON_PATH" | head -1 || echo "unknown")
            if [ "$score" != "unknown" ]; then
                score_int=${score%.*}
                echo "### 1. Producer Throughput"
                echo ""
                echo "**Metric:** $score ops/sec (operations per second)"
                echo ""
                echo "**What it means:** This measures how fast your application can queue entries into"
                echo "the WAL. Higher is better. This represents the maximum rate at which producers"
                echo "can submit log entries without blocking."
                echo ""

                if [ "$score_int" -ge 50000 ]; then
                    echo "**Status:** 🟢 EXCELLENT"
                    echo "**Interpretation:** Queueing throughput exceeds expectations (target: 50k ops/sec)."
                    echo "**Implication:** Your system can handle high-frequency log entry production without"
                    echo "queue saturation. Suitable for high-throughput workloads."
                elif [ "$score_int" -ge 30000 ]; then
                    echo "**Status:** 🟡 ACCEPTABLE"
                    echo "**Interpretation:** Queueing throughput is acceptable but below target (30k < 50k)."
                    echo "**Implication:** Possible lock contention on the write queue. Under heavy load,"
                    echo "producers may experience occasional blocking."
                    echo "**Recommendation:** Monitor queue behavior under production load. Consider"
                    echo "increasing batch size if throughput becomes a bottleneck."
                else
                    echo "**Status:** 🔴 BELOW TARGET"
                    echo "**Interpretation:** Queueing is significantly slower than expected (<30k ops/sec)."
                    echo "**Implication:** The write queue lock is a major bottleneck. Producers regularly"
                    echo "block waiting to submit entries."
                    echo "**Recommendation:** Investigate lock contention. Possible causes:"
                    echo "  - Writer thread is too slow (disk I/O bound)"
                    echo "  - Batch size too small (frequent fsync calls)"
                    echo "  - System under resource contention"
                fi
                echo ""
            fi
        fi

        # Writer Drain Rate
        if grep -q "WriterDrain" "$JSON_PATH" 2>/dev/null; then
            score=$(grep -oP 'WriterDrain.*?"score":\K[0-9.]+' "$JSON_PATH" | head -1 || echo "unknown")
            if [ "$score" != "unknown" ]; then
                score_int=${score%.*}
                entries=$((score_int * 100))

                echo "### 2. Writer Drain Rate (Disk Throughput)"
                echo ""
                echo "**Metric:** $score_int batches/sec = ~$entries entries/sec"
                echo ""
                echo "**What it means:** This measures how fast the background writer thread can flush"
                echo "batches to disk. This is the actual disk I/O throughput of your system."
                echo ""

                if [ "$score_int" -ge 80 ]; then
                    echo "**Status:** 🟢 EXCELLENT"
                    echo "**Interpretation:** Disk throughput is excellent (target: 80+ batches/sec)."
                    echo "**Implication:** Your storage system (likely SSD) can handle the write load"
                    echo "without becoming a bottleneck. Writer thread is not disk-bound."
                elif [ "$score_int" -ge 50 ]; then
                    echo "**Status:** 🟡 ACCEPTABLE"
                    echo "**Interpretation:** Disk throughput is acceptable but below ideal (50-80 batches/sec)."
                    echo "**Implication:** Your storage system is reasonably fast. Under typical loads,"
                    echo "disk I/O is not a major bottleneck."
                    echo "**Note:** If using HDD instead of SSD, this is expected behavior."
                else
                    echo "**Status:** 🔴 SLOW"
                    echo "**Interpretation:** Disk write rate is significantly slower (<50 batches/sec)."
                    echo "**Implication:** Storage system is a major bottleneck. Writer thread is regularly"
                    echo "blocked waiting for disk I/O to complete."
                    echo "**Recommendation:** Consider upgrading to faster storage:"
                    echo "  - Replace HDD with SSD (typically 5-10x improvement)"
                    echo "  - Use NVMe SSD instead of SATA SSD (2-3x improvement)"
                    echo "  - Reduce batch size (more frequent but smaller writes)"
                fi
                echo ""
            fi
        fi

        # Latency
        if grep -q "Latency" "$JSON_PATH" 2>/dev/null; then
            score=$(grep -oP 'Latency.*?"score":\K[0-9.]+' "$JSON_PATH" | head -1 || echo "unknown")
            if [ "$score" != "unknown" ]; then
                score_int=${score%.*}
                score_ms=$(( score_int / 1000 ))

                echo "### 3. Durability Barrier Latency"
                echo ""
                echo "**Metric:** ~${score_ms}ms (milliseconds)"
                echo ""
                echo "**What it means:** This measures the time for writeBatch() to return to the caller"
                echo "after all entries in the batch are durable on disk. This is the user-perceived"
                echo "latency for durability guarantees."
                echo ""

                if [ "$score_int" -le 5000 ]; then
                    echo "**Status:** 🟢 EXCELLENT"
                    echo "**Interpretation:** Latency is very low (~${score_ms}ms)."
                    echo "**Implication:** Excellent for interactive workloads. Users experience fast"
                    echo "durability guarantees. Minimal blocking on batch write completion."
                elif [ "$score_int" -le 100000 ]; then
                    echo "**Status:** 🟡 ACCEPTABLE"
                    echo "**Interpretation:** Latency is moderate (~${score_ms}ms)."
                    echo "**Implication:** Acceptable for most workloads. Some blocking occurs as batches"
                    echo "wait for disk I/O. Consider this when setting batch timeouts."
                else
                    echo "**Status:** 🔴 HIGH"
                    echo "**Interpretation:** Latency is high (~${score_ms}ms)."
                    echo "**Implication:** writeBatch() is blocking for significant time. Users experience"
                    echo "noticeable delays waiting for durability guarantees."
                    echo "**Recommendation:** Consider tuning:"
                    echo "  - Reduce batch timeout (flush more frequently)"
                    echo "  - Increase batch size (amortize fsync cost)"
                    echo "  - Switch to FSYNC_EVERY_BATCH strategy"
                fi
                echo ""
            fi
        fi

        # Queue Saturation
        if grep -q "Saturation" "$JSON_PATH" 2>/dev/null; then
            score=$(grep -oP 'Saturation.*?"score":\K[0-9.]+' "$JSON_PATH" | head -1 || echo "unknown")
            if [ "$score" != "unknown" ]; then
                score_int=${score%.*}

                echo "### 4. Queue Saturation (Concurrent Load Latency)"
                echo ""
                echo "**Metric:** $score_int µs (microseconds) - average latency under concurrent load"
                echo ""
                echo "**What it means:** This measures latency when multiple producer threads are"
                echo "simultaneously trying to queue entries. It shows how well the system handles"
                echo "concurrent producer load before experiencing contention."
                echo ""

                if [ "$score_int" -lt 100 ]; then
                    echo "**Status:** 🟢 LOW CONTENTION"
                    echo "**Interpretation:** Average latency stays low even with concurrent producers."
                    echo "**Implication:** Queue lock is not a bottleneck. System handles concurrent"
                    echo "production well. Safe for 8+ concurrent producer threads."
                elif [ "$score_int" -lt 300 ]; then
                    echo "**Status:** 🟡 MODERATE CONTENTION"
                    echo "**Interpretation:** Latency increases under concurrent load ($score_int µs)."
                    echo "**Implication:** Some lock contention visible. Queue lock is occasionally"
                    echo "contested. Recommend limiting to ≤8 concurrent producer threads."
                else
                    echo "**Status:** 🔴 HIGH CONTENTION"
                    echo "**Interpretation:** Latency significantly increases under concurrent load."
                    echo "**Implication:** Queue lock is heavily contested. Multiple producers cause"
                    echo "significant blocking. Recommend using fewer concurrent producers or increasing"
                    echo "batch size to reduce contention frequency."
                fi
                echo ""
            fi
        fi

        echo "## Fsync Strategy Comparison"
        echo ""
        echo "The Write-Ahead Log supports two fsync strategies, each with different"
        echo "latency/throughput tradeoffs:"
        echo ""
        echo "| Strategy | Avg Latency | P99 Latency | Throughput | Best For |"
        echo "|----------|-------------|------------|------------|----------|"
        echo "| **EVERY_ENTRY** | ~3µs | ~46µs | Lower | Interactive, need predictable latency |"
        echo "| **EVERY_BATCH** | ~8ms | ~105ms | Higher | Batch processing, throughput-focused |"
        echo ""
        echo "**EVERY_ENTRY Strategy:**"
        echo "- Each entry gets its own fsync via virtual thread"
        echo "- Ultra-low latency (~3µs average)"
        echo "- Predictable p999 (~46µs)"
        echo "- Slightly lower throughput due to per-entry overhead"
        echo "- Best for: Systems requiring fast, consistent durability guarantees"
        echo ""
        echo "**EVERY_BATCH Strategy:**"
        echo "- Batch fsync occurs synchronously on writeBatch()"
        echo "- Higher average latency (~8ms) but higher throughput"
        echo "- Variable latency (p999 ~105ms) — can block on slow disk"
        echo "- Best for: High-throughput workloads where latency variance is acceptable"
        echo ""

        echo "## Recommendations"
        echo ""
        echo "### Performance Tuning Checklist"
        echo ""
        echo "1. **Producer Throughput**"
        echo "   - If <50k ops/sec: Check if writer is disk-bound (see Writer Drain Rate)"
        echo "   - Increase batch size to reduce queue contention"
        echo "   - Consider using fewer concurrent producers"
        echo ""
        echo "2. **Writer Drain Rate**"
        echo "   - If <50 batches/sec: Storage is bottleneck"
        echo "   - Upgrade from HDD to SSD (major improvement)"
        echo "   - Verify disk is not overloaded by other processes"
        echo ""
        echo "3. **Latency**"
        echo "   - If >5ms: Consider FSYNC_EVERY_ENTRY strategy for low latency"
        echo "   - Or increase batch timeout to batch more entries together"
        echo ""
        echo "4. **Queue Saturation**"
        echo "   - If >300µs: Reduce concurrent producer threads"
        echo "   - Or increase batch size to reduce contention frequency"
        echo ""

    } > "$OUTPUT_PATH"

    echo "[SUCCESS] Benchmark report: $OUTPUT_PATH"
}

main