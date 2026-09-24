package dev.monitoring.worker.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.monitoring.common.repository.MonitorRepository;
import dev.monitoring.worker.TestMonitors;
import dev.monitoring.worker.WorkerIntegrationTest;
import dev.monitoring.worker.check.CheckRunner;
import dev.monitoring.worker.config.WorkerProperties;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs a dedicated scheduler instance against the real database with a controllable runner. */
@WorkerIntegrationTest
class CheckSchedulerTests {

    @Autowired
    MonitorClaimRepository claims;

    @Autowired
    MonitorRepository monitors;

    @Autowired
    JdbcTemplate jdbc;

    CheckScheduler scheduler;

    @BeforeEach
    void clean() {
        monitors.deleteAll();
    }

    @AfterEach
    void stopScheduler() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    private CheckScheduler newScheduler(CheckRunner runner, int concurrency, Duration grace) {
        WorkerProperties props = new WorkerProperties(true, Duration.ofMillis(50), 50,
                concurrency, grace);
        return new CheckScheduler(claims, runner, props);
    }

    private int dueCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM monitors WHERE enabled AND next_check_at <= now()",
                Integer.class);
    }

    @Test
    void dispatchesEveryDueMonitor() {
        List<UUID> due = TestMonitors.due(monitors, 5);
        Set<UUID> seen = ConcurrentHashMap.newKeySet();

        scheduler = newScheduler(m -> seen.add(m.id()), 4, Duration.ofSeconds(5));
        scheduler.start();

        await().atMost(Duration.ofSeconds(10)).until(() -> seen.size() == 5);
        assertThat(seen).containsExactlyInAnyOrderElementsOf(due);
    }

    @Test
    void claimsNoMoreThanItCanRun() throws Exception {
        TestMonitors.due(monitors, 5);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger();
        CheckRunner blocking = m -> {
            running.incrementAndGet();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        scheduler = newScheduler(blocking, 2, Duration.ofSeconds(5));
        scheduler.start();

        await().atMost(Duration.ofSeconds(10)).until(() -> running.get() == 2);
        Thread.sleep(300); // several more poll cycles while both slots are busy
        assertThat(running.get()).isEqualTo(2);
        assertThat(scheduler.inFlight()).isEqualTo(2);
        assertThat(dueCount()).as("unclaimed monitors left for other workers").isEqualTo(3);

        release.countDown();
        await().atMost(Duration.ofSeconds(10)).until(() -> dueCount() == 0);
    }

    @Test
    void keepsRunningAfterRunnerThrows() {
        TestMonitors.due(monitors, 4);
        AtomicInteger calls = new AtomicInteger();
        CheckRunner flaky = m -> {
            if (calls.incrementAndGet() % 2 == 0) {
                throw new IllegalStateException("boom");
            }
        };

        scheduler = newScheduler(flaky, 1, Duration.ofSeconds(5));
        scheduler.start();

        await().atMost(Duration.ofSeconds(10)).until(() -> calls.get() == 4);
        await().atMost(Duration.ofSeconds(5)).until(() -> scheduler.inFlight() == 0);
    }

    @Test
    void stopWaitsForInFlightChecks() {
        TestMonitors.due(monitors, 1);
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean finished = new AtomicBoolean();
        CheckRunner slow = m -> {
            started.set(true);
            try {
                Thread.sleep(700);
                finished.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        scheduler = newScheduler(slow, 1, Duration.ofSeconds(5));
        scheduler.start();
        await().atMost(Duration.ofSeconds(10)).untilTrue(started);

        scheduler.stop();

        assertThat(finished).as("check completed before stop() returned").isTrue();
        assertThat(scheduler.isRunning()).isFalse();
    }

    @Test
    void stopInterruptsChecksThatExceedGracePeriod() {
        TestMonitors.due(monitors, 1);
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean interrupted = new AtomicBoolean();
        CheckRunner stuck = m -> {
            started.set(true);
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        };

        scheduler = newScheduler(stuck, 1, Duration.ofMillis(200));
        scheduler.start();
        await().atMost(Duration.ofSeconds(10)).untilTrue(started);

        long startedAt = System.nanoTime();
        scheduler.stop();

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(5));
        await().atMost(Duration.ofSeconds(2)).untilTrue(interrupted);
    }
}
