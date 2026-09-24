package dev.monitoring.worker.alert;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * JSON document stored in the outbox and sent to webhooks. It is a snapshot taken when the
 * incident changed, so a later rename or deletion of the monitor does not alter the alert.
 */
public record AlertPayload(
        String event,
        Instant occurredAt,
        MonitorInfo monitor,
        IncidentInfo incident,
        String message) {

    public record MonitorInfo(UUID id, String name, String url) {
    }

    public record IncidentInfo(UUID id, Instant startedAt, Instant resolvedAt, String cause,
                               Long durationSeconds) {
    }

    public static AlertPayload opened(Instant now, UUID monitorId, String name, String url,
                                      UUID incidentId, Instant startedAt, String cause) {
        return new AlertPayload("INCIDENT_OPENED", now, new MonitorInfo(monitorId, name, url),
                new IncidentInfo(incidentId, startedAt, null, cause, null), null);
    }

    public static AlertPayload resolved(Instant now, UUID monitorId, String name, String url,
                                        UUID incidentId, Instant startedAt, Instant resolvedAt,
                                        String cause) {
        return new AlertPayload("INCIDENT_RESOLVED", now, new MonitorInfo(monitorId, name, url),
                new IncidentInfo(incidentId, startedAt, resolvedAt, cause,
                        Duration.between(startedAt, resolvedAt).toSeconds()), null);
    }

    public boolean isTest() {
        return "TEST".equals(event);
    }

    /** Short human-readable title, e.g. for an e-mail subject. */
    public String title() {
        if (isTest()) {
            return "Test notification";
        }
        String name = monitor == null ? "Monitor" : monitor.name();
        return "INCIDENT_OPENED".equals(event) ? name + " is DOWN" : name + " has RECOVERED";
    }

    /** Plain-text body shared by e-mail and Slack. */
    public String body() {
        if (isTest()) {
            return message != null ? message : "Test notification from the Monitoring Platform";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(title()).append('\n');
        sb.append("URL: ").append(monitor.url()).append('\n');
        sb.append("Started: ").append(incident.startedAt()).append('\n');
        if (incident.resolvedAt() != null) {
            sb.append("Resolved: ").append(incident.resolvedAt()).append('\n');
            sb.append("Duration: ").append(formatDuration(incident.durationSeconds())).append('\n');
        }
        if (incident.cause() != null) {
            sb.append("Cause: ").append(incident.cause()).append('\n');
        }
        return sb.toString();
    }

    static String formatDuration(Long seconds) {
        if (seconds == null) {
            return "unknown";
        }
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return (h > 0 ? h + "h " : "") + (h > 0 || m > 0 ? m + "m " : "") + s + "s";
    }
}
