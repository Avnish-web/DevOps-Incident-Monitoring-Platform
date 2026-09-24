package dev.monitoring.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Read-only view of an outbox row, used by the API to show delivery history. The worker
 * writes and updates deliveries with plain SQL.
 */
@Entity
@Immutable
@Table(name = "alert_deliveries")
public class AlertDelivery {

    public enum Status { PENDING, SENT, FAILED, CANCELLED }

    @Id
    private Long id;

    @Column(name = "channel_id")
    private UUID channelId;

    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "status")
    private String status;

    @Column(name = "attempts")
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected AlertDelivery() {
        // for JPA
    }

    public Long getId() {
        return id;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
