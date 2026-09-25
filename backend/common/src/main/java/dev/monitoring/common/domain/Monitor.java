package dev.monitoring.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A monitored target and its current check state. Mapped to the {@code monitors} table.
 *
 * <p>{@code @DynamicUpdate} makes Hibernate write only changed columns. The worker updates
 * scheduling/state columns with direct SQL, so a configuration edit from the API must not
 * write back stale values for columns it did not touch.
 */
@Entity
@Table(name = "monitors")
@DynamicUpdate
public class Monitor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "owner_id", updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MonitorType type;

    @Column(nullable = false, length = 2048)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", nullable = false, length = 8)
    private HttpCheckMethod httpMethod = HttpCheckMethod.GET;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Column(name = "expected_status")
    private Integer expectedStatus;

    @Column(name = "failure_threshold", nullable = false)
    private int failureThreshold = 3;

    @Column(name = "recovery_threshold", nullable = false)
    private int recoveryThreshold = 2;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MonitorStatus status = MonitorStatus.UNKNOWN;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "consecutive_successes", nullable = false)
    private int consecutiveSuccesses;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "next_check_at", nullable = false)
    private Instant nextCheckAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Monitor() {
        // for JPA
    }

    public Monitor(String name, MonitorType type, String url, int intervalSeconds, int timeoutMs) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.url = Objects.requireNonNull(url, "url");
        this.intervalSeconds = intervalSeconds;
        this.timeoutMs = timeoutMs;
        this.nextCheckAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public MonitorType getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = Objects.requireNonNull(url, "url");
    }

    public HttpCheckMethod getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(HttpCheckMethod httpMethod) {
        this.httpMethod = Objects.requireNonNull(httpMethod, "httpMethod");
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Integer getExpectedStatus() {
        return expectedStatus;
    }

    public void setExpectedStatus(Integer expectedStatus) {
        this.expectedStatus = expectedStatus;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public int getRecoveryThreshold() {
        return recoveryThreshold;
    }

    public void setRecoveryThreshold(int recoveryThreshold) {
        this.recoveryThreshold = recoveryThreshold;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public MonitorStatus getStatus() {
        return status;
    }

    public void setStatus(MonitorStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public int getConsecutiveSuccesses() {
        return consecutiveSuccesses;
    }

    public void setConsecutiveSuccesses(int consecutiveSuccesses) {
        this.consecutiveSuccesses = consecutiveSuccesses;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public Instant getNextCheckAt() {
        return nextCheckAt;
    }

    public void setNextCheckAt(Instant nextCheckAt) {
        this.nextCheckAt = Objects.requireNonNull(nextCheckAt, "nextCheckAt");
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    /** The user who owns this resource; set once at creation. */
    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        if (this.ownerId != null && !this.ownerId.equals(ownerId)) {
            throw new IllegalStateException("Owner cannot be changed");
        }
        this.ownerId = ownerId;
    }
}
