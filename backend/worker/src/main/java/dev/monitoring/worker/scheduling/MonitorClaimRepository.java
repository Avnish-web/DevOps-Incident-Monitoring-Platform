package dev.monitoring.worker.scheduling;

import dev.monitoring.common.domain.HttpCheckMethod;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims due monitors atomically (see docs/architecture.md §5.1).
 *
 * <p>One statement selects due rows with {@code FOR UPDATE SKIP LOCKED} and pushes their
 * {@code next_check_at} forward by the interval. Rows locked by another worker are skipped,
 * so concurrent workers never claim the same monitor. Moving {@code next_check_at} forward
 * also acts as a lease: if this worker crashes mid-check, the monitor is picked up again at
 * its next interval instead of getting stuck.
 *
 * <p>Plain SQL on purpose: it does not bump the JPA {@code version}, so scheduling never
 * conflicts with a user editing the monitor through the API.
 */
@Repository
public class MonitorClaimRepository {

    private static final String CLAIM_SQL = """
            UPDATE monitors m
               SET next_check_at = now() + make_interval(secs => m.interval_seconds)
              FROM (SELECT id, next_check_at AS due_at
                      FROM monitors
                     WHERE enabled
                       AND next_check_at <= now()
                     ORDER BY next_check_at
                     LIMIT :limit
                       FOR UPDATE SKIP LOCKED) due
             WHERE m.id = due.id
            RETURNING m.id, m.url, m.http_method, m.timeout_ms, m.expected_status,
                      m.interval_seconds, due.due_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public MonitorClaimRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public List<ClaimedMonitor> claimDue(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbc.query(CLAIM_SQL, new MapSqlParameterSource("limit", limit),
                (rs, rowNum) -> new ClaimedMonitor(
                        rs.getObject("id", UUID.class),
                        rs.getString("url"),
                        HttpCheckMethod.valueOf(rs.getString("http_method")),
                        rs.getInt("timeout_ms"),
                        rs.getObject("expected_status", Integer.class),
                        rs.getInt("interval_seconds"),
                        rs.getObject("due_at", OffsetDateTime.class).toInstant()));
    }
}
