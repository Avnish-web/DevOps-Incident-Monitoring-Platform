package dev.monitoring.api.history;

import dev.monitoring.api.monitor.MonitorNotFoundException;
import dev.monitoring.api.web.PageResponse;
import dev.monitoring.common.repository.CheckResultRepository;
import dev.monitoring.common.repository.MonitorRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Check history and aggregated statistics, computed in PostgreSQL. */
@Service
@Transactional(readOnly = true)
public class CheckHistoryService {

    /** Fixed origin so bucket boundaries are stable between requests. */
    private static final Instant BUCKET_ORIGIN = Instant.parse("2000-01-01T00:00:00Z");

    private static final String SUMMARY_SQL = """
            SELECT count(*)                                   AS checks,
                   count(*) FILTER (WHERE NOT success)        AS failures,
                   avg(latency_ms) FILTER (WHERE success)     AS avg_latency,
                   percentile_cont(0.5)  WITHIN GROUP (ORDER BY latency_ms) FILTER (WHERE success) AS p50,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) FILTER (WHERE success) AS p95,
                   max(latency_ms) FILTER (WHERE success)     AS max_latency
              FROM check_results
             WHERE monitor_id = :id AND checked_at >= :from AND checked_at < :to
            """;

    private static final String SERIES_SQL = """
            SELECT date_bin(CAST(:bucket AS interval), checked_at, :origin) AS bucket_start,
                   count(*)                                   AS checks,
                   count(*) FILTER (WHERE NOT success)        AS failures,
                   avg(latency_ms) FILTER (WHERE success)     AS avg_latency,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) FILTER (WHERE success) AS p95
              FROM check_results
             WHERE monitor_id = :id AND checked_at >= :from AND checked_at < :to
             GROUP BY 1
             ORDER BY 1
            """;

    private final MonitorRepository monitors;
    private final CheckResultRepository checkResults;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public CheckHistoryService(MonitorRepository monitors, CheckResultRepository checkResults,
                               NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.monitors = monitors;
        this.checkResults = checkResults;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public PageResponse<CheckResultResponse> checks(UUID monitorId, Pageable pageable) {
        requireMonitor(monitorId);
        return PageResponse.of(checkResults.findByMonitorId(monitorId, pageable),
                CheckResultResponse::from);
    }

    public MonitorStatsResponse stats(UUID monitorId, StatsRange range) {
        requireMonitor(monitorId);
        Instant to = clock.instant();
        Instant from = alignDown(to.minus(range.window()), range.bucket());

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", monitorId)
                .addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .addValue("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                .addValue("bucket", range.bucket().toSeconds() + " seconds")
                .addValue("origin", OffsetDateTime.ofInstant(BUCKET_ORIGIN, ZoneOffset.UTC));

        MonitorStatsResponse.Summary summary = jdbc.queryForObject(SUMMARY_SQL, params,
                (rs, n) -> {
                    long checks = rs.getLong("checks");
                    long failures = rs.getLong("failures");
                    Double uptime = checks == 0 ? null
                            : Math.round((checks - failures) * 100_000.0 / checks) / 1000.0;
                    return new MonitorStatsResponse.Summary(checks, failures, uptime,
                            roundedOrNull(rs, "avg_latency"), roundedOrNull(rs, "p50"),
                            roundedOrNull(rs, "p95"), roundedOrNull(rs, "max_latency"));
                });

        Map<Instant, MonitorStatsResponse.Bucket> filled = new HashMap<>();
        jdbc.query(SERIES_SQL, params, rs -> {
            Instant start = rs.getObject("bucket_start", OffsetDateTime.class).toInstant();
            filled.put(start, new MonitorStatsResponse.Bucket(start, rs.getLong("checks"),
                    rs.getLong("failures"), roundedOrNull(rs, "avg_latency"),
                    roundedOrNull(rs, "p95")));
        });

        List<MonitorStatsResponse.Bucket> series = new ArrayList<>();
        for (Instant t = from; t.isBefore(to); t = t.plus(range.bucket())) {
            series.add(filled.getOrDefault(t, new MonitorStatsResponse.Bucket(t, 0, 0, null, null)));
        }
        return new MonitorStatsResponse(range.code(), from, to, range.bucket().toSeconds(),
                summary, series);
    }

    static Instant alignDown(Instant instant, Duration bucket) {
        long bucketMs = bucket.toMillis();
        long offset = instant.toEpochMilli() - BUCKET_ORIGIN.toEpochMilli();
        return BUCKET_ORIGIN.plusMillis(Math.floorDiv(offset, bucketMs) * bucketMs);
    }

    private static Integer roundedOrNull(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : (int) Math.round(value);
    }

    private void requireMonitor(UUID id) {
        if (!monitors.existsById(id)) {
            throw new MonitorNotFoundException(id);
        }
    }
}
