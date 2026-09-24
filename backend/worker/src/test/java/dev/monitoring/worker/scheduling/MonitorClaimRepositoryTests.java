package dev.monitoring.worker.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.common.repository.MonitorRepository;
import dev.monitoring.worker.TestMonitors;
import dev.monitoring.worker.WorkerIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@WorkerIntegrationTest
class MonitorClaimRepositoryTests {

    @Autowired
    MonitorClaimRepository claims;

    @Autowired
    MonitorRepository monitors;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        monitors.deleteAll();
    }

    @Test
    void claimsOnlyEnabledDueMonitors() {
        UUID due = TestMonitors.due(monitors, "due", 10);
        TestMonitors.notDue(monitors, "later");
        TestMonitors.disabledDue(monitors, "paused");

        List<ClaimedMonitor> claimed = claims.claimDue(10);

        assertThat(claimed).extracting(ClaimedMonitor::id).containsExactly(due);
        assertThat(claimed.get(0).url()).isEqualTo("https://example.com/due");
        assertThat(claimed.get(0).timeoutMs()).isEqualTo(5000);
    }

    @Test
    void claimPushesNextCheckForwardByInterval() {
        UUID id = TestMonitors.due(monitors, "due", 10);

        claims.claimDue(10);

        Instant next = jdbc.queryForObject("SELECT next_check_at FROM monitors WHERE id = ?",
                Instant.class, id);
        assertThat(next).isBetween(Instant.now().plusSeconds(50), Instant.now().plusSeconds(61));
        assertThat(claims.claimDue(10)).as("claimed monitor is not due again").isEmpty();
    }

    @Test
    void claimDoesNotBumpEntityVersion() {
        UUID id = TestMonitors.due(monitors, "due", 10);

        claims.claimDue(10);

        assertThat(monitors.findById(id).orElseThrow().getVersion()).isZero();
    }

    @Test
    void respectsLimitAndClaimsMostOverdueFirst() {
        UUID oldest = TestMonitors.due(monitors, "oldest", 300);
        UUID middle = TestMonitors.due(monitors, "middle", 200);
        TestMonitors.due(monitors, "newest", 100);

        List<ClaimedMonitor> claimed = claims.claimDue(2);

        assertThat(claimed).extracting(ClaimedMonitor::id).containsExactlyInAnyOrder(oldest, middle);
    }

    @Test
    void concurrentClaimersNeverClaimTheSameMonitor() throws Exception {
        int dueCount = 200;
        int workers = 8;
        List<UUID> dueIds = TestMonitors.due(monitors, dueCount);

        List<UUID> allClaimed = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int w = 0; w < workers; w++) {
                futures.add(pool.submit((Callable<Void>) () -> {
                    startTogether.await();
                    List<ClaimedMonitor> batch;
                    do {
                        batch = claims.claimDue(7);
                        batch.forEach(m -> allClaimed.add(m.id()));
                    } while (!batch.isEmpty());
                    return null;
                }));
            }
            startTogether.countDown();
            for (Future<?> f : futures) {
                f.get(Duration.ofSeconds(60).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Set<UUID> unique = new HashSet<>(allClaimed);
        assertThat(allClaimed).as("no duplicates").hasSize(unique.size());
        assertThat(unique).as("every due monitor claimed").containsExactlyInAnyOrderElementsOf(dueIds);
    }
}
