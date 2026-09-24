package dev.monitoring.api.incident;

import dev.monitoring.common.domain.Incident;
import dev.monitoring.common.domain.IncidentResolution;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * An incident as seen by clients.
 *
 * @param durationSeconds time between start and resolution; for an open incident, until now
 */
public record IncidentResponse(
        UUID id,
        UUID monitorId,
        String monitorName,
        IncidentStatus status,
        Instant startedAt,
        Instant resolvedAt,
        IncidentResolution resolution,
        String cause,
        long durationSeconds) {

    public enum IncidentStatus {
        OPEN,
        RESOLVED
    }

    static IncidentResponse from(Incident incident, String monitorName, Instant now) {
        Instant end = incident.isOpen() ? now : incident.getResolvedAt();
        return new IncidentResponse(incident.getId(), incident.getMonitorId(), monitorName,
                incident.isOpen() ? IncidentStatus.OPEN : IncidentStatus.RESOLVED,
                incident.getStartedAt(), incident.getResolvedAt(), incident.getResolution(),
                incident.getCause(),
                Math.max(0, Duration.between(incident.getStartedAt(), end).toSeconds()));
    }
}
