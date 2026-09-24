package dev.monitoring.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A period during which a monitor was DOWN. Open while {@code resolvedAt} is null; the
 * database allows at most one open incident per monitor.
 */
@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "monitor_id", nullable = false, updatable = false)
    private UUID monitorId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(length = 512)
    private String cause;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private IncidentResolution resolution;

    protected Incident() {
        // for JPA
    }

    public Incident(UUID monitorId, Instant startedAt, String cause) {
        this.monitorId = Objects.requireNonNull(monitorId, "monitorId");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.cause = cause == null || cause.length() <= 512 ? cause : cause.substring(0, 512);
    }

    /**
     * Closes the incident. A time before {@code startedAt} is clamped to {@code startedAt}
     * so the stored interval is never negative.
     */
    public void resolve(Instant at, IncidentResolution resolution) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(resolution, "resolution");
        if (resolvedAt != null) {
            throw new IllegalStateException("Incident " + id + " is already resolved");
        }
        this.resolvedAt = at.isBefore(startedAt) ? startedAt : at;
        this.resolution = resolution;
    }

    public boolean isOpen() {
        return resolvedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMonitorId() {
        return monitorId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getCause() {
        return cause;
    }

    public IncidentResolution getResolution() {
        return resolution;
    }
}
