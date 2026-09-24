package dev.monitoring.worker.metrics;

import dev.monitoring.worker.check.CheckOutcome;
import dev.monitoring.worker.incident.IncidentStateMachine.Event;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Worker metrics, exported at {@code /actuator/prometheus} (docs/architecture.md §10).
 *
 * <p>All labels have bounded cardinality: outcome (2 values), error type (enum), incident event
 * (2 values). Per-monitor series are deliberately not produced here; per-monitor state comes
 * from the API's fleet gauges.
 * <ul>
 *   <li>{@code monitoring_checks_total{outcome,error_type}}</li>
 *   <li>{@code monitoring_check_duration_seconds{outcome}}: histogram, SLO buckets</li>
 *   <li>{@code monitoring_scheduler_lag_seconds}: how late a check started vs. its due time</li>
 *   <li>{@code monitoring_claims_total}, {@code monitoring_incidents_total{event}}</li>
 *   <li>{@code monitoring_checks_in_flight}: registered by the scheduler</li>
 * </ul>
 */
@Component
public class WorkerMetrics {

    private static final Duration[] CHECK_BUCKETS = {
            Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(250),
            Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2),
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30)};

    private static final Duration[] LAG_BUCKETS = {
            Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofSeconds(1),
            Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10),
            Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofMinutes(5)};

    private final MeterRegistry registry;
    private final Timer schedulerLag;
    private final Counter claims;

    public WorkerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.schedulerLag = Timer.builder("monitoring.scheduler.lag")
                .description("Delay between a monitor becoming due and its check being claimed")
                .serviceLevelObjectives(LAG_BUCKETS)
                .register(registry);
        this.claims = Counter.builder("monitoring.claims")
                .description("Monitors claimed for checking")
                .register(registry);
    }

    public MeterRegistry registry() {
        return registry;
    }

    public void recordCheck(CheckOutcome outcome) {
        String result = outcome.success() ? "success" : "failure";
        String errorType = outcome.errorType() == null
                ? "none" : outcome.errorType().name().toLowerCase(Locale.ROOT);
        Counter.builder("monitoring.checks")
                .description("Completed checks by outcome")
                .tag("outcome", result)
                .tag("error_type", errorType)
                .register(registry)
                .increment();
        if (outcome.latencyMs() != null) {
            Timer.builder("monitoring.check.duration")
                    .description("Check duration: DNS, connect, TLS, request and body read")
                    .tag("outcome", result)
                    .serviceLevelObjectives(CHECK_BUCKETS)
                    .register(registry)
                    .record(Duration.ofMillis(outcome.latencyMs()));
        }
    }

    public void recordClaim(Duration lag) {
        claims.increment();
        schedulerLag.record(lag.isNegative() ? Duration.ZERO : lag);
    }

    public void recordIncident(Event event) {
        if (event == Event.NONE) {
            return;
        }
        Counter.builder("monitoring.incidents")
                .description("Incident state changes")
                .tag("event", event == Event.INCIDENT_OPENED ? "opened" : "resolved")
                .register(registry)
                .increment();
    }
}
