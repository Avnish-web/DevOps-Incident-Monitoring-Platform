package dev.monitoring.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    protected Incident() {
        // for JPA
    }

    public Incident(UUID monitorId, Instant startedAt, String cause) {
        this.monitorId = Objects.requireNonNull(monitorId, "monitorId");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.cause = cause == null || cause.length() <= 512 ? cause : cause.substring(0, 512);
    }

    public void resolve(Instant at) {
        Objects.requireNonNull(at, "at");
        if (resolvedAt != null) {
            throw new IllegalStateException("Incident " + id + " is already resolved");
        }
        if (at.isBefore(startedAt)) {
            throw new IllegalArgumentException("resolvedAt must not be before startedAt");
        }
        this.resolvedAt = at;
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
}
