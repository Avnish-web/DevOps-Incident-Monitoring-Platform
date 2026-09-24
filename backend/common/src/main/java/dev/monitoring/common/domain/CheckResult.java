package dev.monitoring.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Outcome of a single check. Immutable once written; this is the high-volume history table.
 * The monitor is referenced by ID only, so writing a result never loads the monitor entity.
 */
@Entity
@Table(name = "check_results")
public class CheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "check_results_seq")
    @SequenceGenerator(name = "check_results_seq", sequenceName = "check_results_seq",
            allocationSize = 50)
    private Long id;

    @Column(name = "monitor_id", nullable = false, updatable = false)
    private UUID monitorId;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt;

    @Column(nullable = false, updatable = false)
    private boolean success;

    @Column(name = "status_code", updatable = false)
    private Integer statusCode;

    @Column(name = "latency_ms", updatable = false)
    private Integer latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", length = 32, updatable = false)
    private CheckErrorType errorType;

    @Column(name = "error_message", length = 512, updatable = false)
    private String errorMessage;

    protected CheckResult() {
        // for JPA
    }

    private CheckResult(UUID monitorId, Instant checkedAt, boolean success, Integer statusCode,
                        Integer latencyMs, CheckErrorType errorType, String errorMessage) {
        this.monitorId = Objects.requireNonNull(monitorId, "monitorId");
        this.checkedAt = Objects.requireNonNull(checkedAt, "checkedAt");
        this.success = success;
        this.statusCode = statusCode;
        this.latencyMs = latencyMs;
        this.errorType = errorType;
        this.errorMessage = truncate(errorMessage, 512);
    }

    public static CheckResult success(UUID monitorId, Instant checkedAt, int statusCode,
                                      int latencyMs) {
        return new CheckResult(monitorId, checkedAt, true, statusCode, latencyMs, null, null);
    }

    public static CheckResult failure(UUID monitorId, Instant checkedAt, Integer statusCode,
                                      Integer latencyMs, CheckErrorType errorType,
                                      String errorMessage) {
        return new CheckResult(monitorId, checkedAt, false, statusCode, latencyMs,
                Objects.requireNonNull(errorType, "errorType"), errorMessage);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public UUID getMonitorId() {
        return monitorId;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public CheckErrorType getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
