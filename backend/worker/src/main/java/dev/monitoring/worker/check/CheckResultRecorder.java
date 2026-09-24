package dev.monitoring.worker.check;

import dev.monitoring.common.domain.CheckResult;
import dev.monitoring.common.repository.CheckResultRepository;
import dev.monitoring.worker.scheduling.ClaimedMonitor;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a check outcome: one {@code check_results} row plus the monitor's
 * {@code last_checked_at}, in one transaction.
 *
 * <p>The monitor row is updated with plain SQL so the user-facing JPA {@code version} is not
 * bumped, and only when this result is newer (results can finish out of order).
 */
@Component
public class CheckResultRecorder {

    private final CheckResultRepository checkResults;
    private final JdbcTemplate jdbc;

    public CheckResultRecorder(CheckResultRepository checkResults, JdbcTemplate jdbc) {
        this.checkResults = checkResults;
        this.jdbc = jdbc;
    }

    /**
     * @throws org.springframework.dao.DataIntegrityViolationException if the monitor was
     *         deleted while the check was running
     */
    @Transactional
    public void record(ClaimedMonitor monitor, CheckOutcome outcome) {
        CheckResult result = outcome.success()
                ? CheckResult.success(monitor.id(), outcome.checkedAt(), outcome.statusCode(),
                        outcome.latencyMs())
                : CheckResult.failure(monitor.id(), outcome.checkedAt(), outcome.statusCode(),
                        outcome.latencyMs(), outcome.errorType(), outcome.errorMessage());
        checkResults.save(result);

        Timestamp checkedAt = Timestamp.from(outcome.checkedAt());
        jdbc.update("""
                UPDATE monitors SET last_checked_at = ?
                 WHERE id = ? AND (last_checked_at IS NULL OR last_checked_at < ?)
                """, checkedAt, monitor.id(), checkedAt);
    }
}
