package dev.monitoring.worker.scheduling;

import dev.monitoring.common.domain.HttpCheckMethod;
import java.util.UUID;

/** Snapshot of the monitor fields a check needs, taken at claim time. */
public record ClaimedMonitor(
        UUID id,
        String url,
        HttpCheckMethod httpMethod,
        int timeoutMs,
        Integer expectedStatus,
        int intervalSeconds) {
}
