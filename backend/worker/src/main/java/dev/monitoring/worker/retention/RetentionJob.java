package dev.monitoring.worker.retention;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deletes check results older than the retention period.
 *
 * <p>Runs in small batches, each in its own short transaction, so it never holds long locks
 * or produces a huge WAL burst. A transaction-level advisory lock ensures only one worker
 * replica deletes at a time; the others skip the round.
 */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    /** Arbitrary constant identifying this job's advisory lock. */
    static final long ADVISORY_LOCK_KEY = 0x4d4f4e5f52455431L; // "MON_RET1"

    private static final String DELETE_BATCH = """
            DELETE FROM check_results
             WHERE id IN (SELECT id FROM check_results
                           WHERE checked_at < ?
                           LIMIT ?)
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final RetentionProperties properties;
    private final Clock clock;

    public RetentionJob(JdbcTemplate jdbc, TransactionTemplate tx, RetentionProperties properties,
                        Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${monitoring.retention.initial-delay:5m}",
            fixedDelayString = "${monitoring.retention.interval:1h}")
    public void scheduledRun() {
        if (!properties.enabled()) {
            return;
        }
        try {
            long deleted = purgeExpired();
            if (deleted > 0) {
                log.info("Retention: deleted {} check result(s) older than {} days",
                        deleted, properties.checkResultsDays());
            }
        } catch (RuntimeException e) {
            log.warn("Retention run failed, will retry next interval: {}", e.toString());
        }
    }

    /** @return number of deleted rows, or 0 if another replica holds the lock */
    public long purgeExpired() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(properties.checkResultsDays()));
        Timestamp cutoffTs = Timestamp.from(cutoff);
        long total = 0;
        while (true) {
            Integer deleted = tx.execute(status -> {
                Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)",
                        Boolean.class, ADVISORY_LOCK_KEY);
                if (!Boolean.TRUE.equals(locked)) {
                    return -1;
                }
                return jdbc.update(DELETE_BATCH, cutoffTs, properties.batchSize());
            });
            if (deleted == null || deleted < 0) {
                return total;
            }
            total += deleted;
            if (deleted < properties.batchSize()) {
                return total;
            }
        }
    }
}
