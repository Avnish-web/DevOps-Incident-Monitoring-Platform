package dev.monitoring.worker.check;

import dev.monitoring.common.domain.CheckResult;
import dev.monitoring.common.domain.MonitorStatus;
import dev.monitoring.common.repository.CheckResultRepository;
import dev.monitoring.worker.incident.IncidentStateMachine;
import dev.monitoring.worker.incident.IncidentStateMachine.Event;
import dev.monitoring.worker.incident.IncidentStateMachine.State;
import dev.monitoring.worker.incident.IncidentStateMachine.Transition;
import dev.monitoring.worker.scheduling.ClaimedMonitor;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a check outcome and applies incident detection, all in one transaction:
 * <ol>
 *   <li>insert the {@code check_results} row;</li>
 *   <li>lock the monitor row ({@code FOR UPDATE}) so the API and other workers cannot
 *       interleave state changes;</li>
 *   <li>if this is the newest result for an enabled monitor, advance the state machine and
 *       update status, counters and {@code last_checked_at};</li>
 *   <li>open or resolve the incident when the state machine says so.</li>
 * </ol>
 * Monitor rows are updated with plain SQL so the user-facing JPA {@code version} is not bumped.
 * Results that arrive out of order, or for a paused monitor, are stored but do not change state.
 */
@Component
public class CheckResultRecorder {

    private record MonitorRow(boolean enabled, MonitorStatus status, int consecutiveFailures,
                              int consecutiveSuccesses, int failureThreshold,
                              int recoveryThreshold, Instant lastCheckedAt) {
    }

    private final CheckResultRepository checkResults;
    private final JdbcTemplate jdbc;

    public CheckResultRecorder(CheckResultRepository checkResults, JdbcTemplate jdbc) {
        this.checkResults = checkResults;
        this.jdbc = jdbc;
    }

    /**
     * @return the incident event caused by this result, if any
     * @throws org.springframework.dao.DataIntegrityViolationException if the monitor was
     *         deleted while the check was running
     */
    @Transactional
    public Event record(ClaimedMonitor monitor, CheckOutcome outcome) {
        checkResults.saveAndFlush(outcome.success()
                ? CheckResult.success(monitor.id(), outcome.checkedAt(), outcome.statusCode(),
                        outcome.latencyMs())
                : CheckResult.failure(monitor.id(), outcome.checkedAt(), outcome.statusCode(),
                        outcome.latencyMs(), outcome.errorType(), outcome.errorMessage()));

        MonitorRow row = lockMonitor(monitor.id());
        if (row == null) {
            return Event.NONE;
        }
        Timestamp checkedAt = Timestamp.from(outcome.checkedAt());
        if (row.lastCheckedAt() != null && !outcome.checkedAt().isAfter(row.lastCheckedAt())) {
            return Event.NONE; // an older check finished late: keep it as history only
        }
        if (!row.enabled()) {
            jdbc.update("UPDATE monitors SET last_checked_at = ? WHERE id = ?",
                    checkedAt, monitor.id());
            return Event.NONE;
        }

        Transition t = IncidentStateMachine.apply(
                new State(row.status(), row.consecutiveFailures(), row.consecutiveSuccesses()),
                outcome.success(), row.failureThreshold(), row.recoveryThreshold());
        State next = t.next();
        jdbc.update("""
                UPDATE monitors
                   SET status = ?, consecutive_failures = ?, consecutive_successes = ?,
                       last_checked_at = ?
                 WHERE id = ?
                """, next.status().name(), next.consecutiveFailures(),
                next.consecutiveSuccesses(), checkedAt, monitor.id());

        switch (t.event()) {
            case INCIDENT_OPENED -> openIncident(monitor.id(),
                    streakStart(monitor.id(), next.consecutiveFailures()), describe(outcome));
            case INCIDENT_RESOLVED -> resolveIncident(monitor.id(),
                    streakStart(monitor.id(), next.consecutiveSuccesses()));
            case NONE -> { }
        }
        return t.event();
    }

    private MonitorRow lockMonitor(UUID id) {
        List<MonitorRow> rows = jdbc.query("""
                SELECT enabled, status, consecutive_failures, consecutive_successes,
                       failure_threshold, recovery_threshold, last_checked_at
                  FROM monitors WHERE id = ? FOR UPDATE
                """, (rs, n) -> {
                    OffsetDateTime last = rs.getObject("last_checked_at", OffsetDateTime.class);
                    return new MonitorRow(rs.getBoolean("enabled"),
                            MonitorStatus.valueOf(rs.getString("status")),
                            rs.getInt("consecutive_failures"), rs.getInt("consecutive_successes"),
                            rs.getInt("failure_threshold"), rs.getInt("recovery_threshold"),
                            last == null ? null : last.toInstant());
                }, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Time of the first check in the current streak: the oldest of the monitor's latest
     * {@code length} results. Gives "down since" / "up again since" instead of the moment
     * the threshold happened to be crossed.
     */
    private Instant streakStart(UUID monitorId, int length) {
        OffsetDateTime start = jdbc.queryForObject("""
                SELECT min(checked_at) FROM (
                    SELECT checked_at FROM check_results
                     WHERE monitor_id = ? ORDER BY checked_at DESC LIMIT ?) streak
                """, OffsetDateTime.class, monitorId, length);
        return start.toInstant();
    }

    private void openIncident(UUID monitorId, Instant startedAt, String cause) {
        // The partial unique index guarantees a single open incident; never fail the result on it.
        jdbc.update("""
                INSERT INTO incidents (monitor_id, started_at, cause) VALUES (?, ?, ?)
                ON CONFLICT (monitor_id) WHERE resolved_at IS NULL DO NOTHING
                """, monitorId, Timestamp.from(startedAt), cause);
    }

    private void resolveIncident(UUID monitorId, Instant resolvedAt) {
        jdbc.update("""
                UPDATE incidents
                   SET resolved_at = GREATEST(started_at, ?), resolution = 'RECOVERED'
                 WHERE monitor_id = ? AND resolved_at IS NULL
                """, Timestamp.from(resolvedAt), monitorId);
    }

    private static String describe(CheckOutcome outcome) {
        String cause = outcome.errorType() + ": " + outcome.errorMessage();
        return cause.length() <= 512 ? cause : cause.substring(0, 512);
    }
}
