package dev.monitoring.worker.scheduling;

import dev.monitoring.worker.config.WorkerProperties;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Liveness signal for the polling loop. Reports DOWN only if the loop has stopped making
 * progress (e.g. hung on a call). A failing database makes polls fail quickly and is
 * reported by readiness instead, so an outage does not trigger restarts.
 *
 * <p>Exposed as health contributor {@code checkScheduler} (bean name minus "HealthIndicator").
 */
@Component
public class CheckSchedulerHealthIndicator implements HealthIndicator {

    private static final Duration MIN_STALL_THRESHOLD = Duration.ofSeconds(60);

    private final CheckScheduler scheduler;
    private final Duration stallThreshold;

    public CheckSchedulerHealthIndicator(CheckScheduler scheduler, WorkerProperties properties) {
        this.scheduler = scheduler;
        Duration tenPolls = properties.pollInterval().multipliedBy(10);
        this.stallThreshold = tenPolls.compareTo(MIN_STALL_THRESHOLD) > 0
                ? tenPolls : MIN_STALL_THRESHOLD;
    }

    @Override
    public Health health() {
        if (!scheduler.isRunning()) {
            return Health.up().withDetail("scheduler", "stopped").build();
        }
        Instant last = scheduler.lastPollCompletedAt();
        if (last != null && last.plus(stallThreshold).isBefore(Instant.now())) {
            return Health.down().withDetail("lastPollCompletedAt", last.toString()).build();
        }
        return Health.up()
                .withDetail("inFlight", scheduler.inFlight())
                .withDetail("lastPollSucceeded", scheduler.lastPollSucceeded())
                .build();
    }
}
