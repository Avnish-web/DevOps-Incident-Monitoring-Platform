package dev.monitoring.api.monitor;

import dev.monitoring.common.domain.HttpCheckMethod;
import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.domain.MonitorStatus;
import dev.monitoring.common.domain.MonitorType;
import java.time.Instant;
import java.util.UUID;

/** Public representation of a monitor. Internal counters are intentionally not exposed. */
public record MonitorResponse(
        UUID id,
        String name,
        MonitorType type,
        String url,
        HttpCheckMethod httpMethod,
        int intervalSeconds,
        int timeoutMs,
        Integer expectedStatus,
        int failureThreshold,
        int recoveryThreshold,
        boolean enabled,
        MonitorStatus status,
        Instant lastCheckedAt,
        Instant nextCheckAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static MonitorResponse from(Monitor m) {
        return new MonitorResponse(m.getId(), m.getName(), m.getType(), m.getUrl(),
                m.getHttpMethod(), m.getIntervalSeconds(), m.getTimeoutMs(),
                m.getExpectedStatus(), m.getFailureThreshold(), m.getRecoveryThreshold(),
                m.isEnabled(), m.getStatus(), m.getLastCheckedAt(), m.getNextCheckAt(),
                m.getCreatedAt(), m.getUpdatedAt(), m.getVersion());
    }
}
