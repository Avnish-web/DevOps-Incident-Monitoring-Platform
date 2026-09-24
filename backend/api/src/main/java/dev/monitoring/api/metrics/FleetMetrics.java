package dev.monitoring.api.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fleet-state gauges refreshed from the database, exported at {@code /actuator/prometheus}.
 * <ul>
 *   <li>{@code monitoring_monitors{state="up|down|unknown|paused"}}</li>
 *   <li>{@code monitoring_open_incidents}</li>
 *   <li>{@code monitoring_monitor_up{monitor_id,monitor_name}}: 1 = up, 0 = down, one series
 *       per enabled monitor with a known state. Cardinality grows with the number of monitors,
 *       which the platform bounds.</li>
 * </ul>
 * Every API replica reports the same values: aggregate with {@code max}, not {@code sum}.
 */
@Component
public class FleetMetrics {

    private static final Logger log = LoggerFactory.getLogger(FleetMetrics.class);

    enum State { UP, DOWN, UNKNOWN, PAUSED }

    private record Row(String id, String name, String status, boolean enabled) {
    }

    private final JdbcTemplate jdbc;
    private final Map<State, AtomicLong> monitorsByState = new EnumMap<>(State.class);
    private final AtomicLong openIncidents = new AtomicLong();
    private final MultiGauge monitorUp;

    public FleetMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        for (State state : State.values()) {
            AtomicLong value = new AtomicLong();
            monitorsByState.put(state, value);
            Gauge.builder("monitoring.monitors", value, AtomicLong::get)
                    .description("Monitors by current state")
                    .tag("state", state.name().toLowerCase())
                    .register(registry);
        }
        Gauge.builder("monitoring.open.incidents", openIncidents, AtomicLong::get)
                .description("Incidents currently open")
                .register(registry);
        this.monitorUp = MultiGauge.builder("monitoring.monitor.up")
                .description("1 if the monitor is up, 0 if down (enabled monitors with a known state)")
                .register(registry);
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${monitoring.metrics.fleet-refresh:30s}")
    public void refresh() {
        try {
            List<Row> rows = jdbc.query("SELECT id, name, status, enabled FROM monitors",
                    (rs, n) -> new Row(rs.getString("id"), rs.getString("name"),
                            rs.getString("status"), rs.getBoolean("enabled")));
            Map<State, Long> counts = new EnumMap<>(State.class);
            List<MultiGauge.Row<?>> upRows = new ArrayList<>();
            for (Row row : rows) {
                State state = row.enabled() ? State.valueOf(row.status()) : State.PAUSED;
                counts.merge(state, 1L, Long::sum);
                if (state == State.UP || state == State.DOWN) {
                    upRows.add(MultiGauge.Row.of(
                            Tags.of("monitor_id", row.id(), "monitor_name", row.name()),
                            state == State.UP ? 1 : 0));
                }
            }
            monitorsByState.forEach((state, value) -> value.set(counts.getOrDefault(state, 0L)));
            monitorUp.register(upRows, true);
            openIncidents.set(jdbc.queryForObject(
                    "SELECT count(*) FROM incidents WHERE resolved_at IS NULL", Long.class));
        } catch (RuntimeException e) {
            log.warn("Could not refresh fleet metrics: {}", e.toString());
        }
    }
}
