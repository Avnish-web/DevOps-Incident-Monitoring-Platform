package dev.monitoring.worker.scheduling;

import dev.monitoring.common.domain.HttpCheckMethod;
import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot of the monitor fields a check needs, taken at claim time.
 *
 * @param dueAt when the check was due (for scheduler-lag metrics); null if unknown
 */
public record ClaimedMonitor(
        UUID id,
        String url,
        HttpCheckMethod httpMethod,
        int timeoutMs,
        Integer expectedStatus,
        int intervalSeconds,
        Instant dueAt) {

    public ClaimedMonitor(UUID id, String url, HttpCheckMethod httpMethod, int timeoutMs,
                          Integer expectedStatus, int intervalSeconds) {
        this(id, url, httpMethod, timeoutMs, expectedStatus, intervalSeconds, null);
    }
}
