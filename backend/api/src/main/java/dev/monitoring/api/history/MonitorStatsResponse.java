package dev.monitoring.api.history;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated check history for one monitor over a time range.
 *
 * <p>Latency figures cover successful checks only (a timeout's duration says little about
 * the service). Buckets with no checks are included with {@code checks = 0} and null
 * latencies, so charts show gaps instead of joining across missing data.
 */
public record MonitorStatsResponse(
        String range,
        Instant from,
        Instant to,
        long bucketSeconds,
        Summary summary,
        List<Bucket> series) {

    /**
     * @param uptimePercent successful / total checks × 100, or null if there were no checks
     */
    public record Summary(long totalChecks, long failedChecks, Double uptimePercent,
                          Integer avgLatencyMs, Integer p50LatencyMs, Integer p95LatencyMs,
                          Integer maxLatencyMs) {
    }

    public record Bucket(Instant bucketStart, long checks, long failures, Integer avgLatencyMs,
                         Integer p95LatencyMs) {
    }
}
