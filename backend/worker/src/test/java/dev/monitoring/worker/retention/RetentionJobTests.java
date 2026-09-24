package dev.monitoring.worker.retention;

import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.common.domain.CheckResult;
import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.domain.MonitorType;
import dev.monitoring.common.repository.CheckResultRepository;
import dev.monitoring.common.repository.MonitorRepository;
import dev.monitoring.worker.WorkerIntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@WorkerIntegrationTest
class RetentionJobTests {

    private static final Instant NOW = Instant.parse("2030-06-01T00:00:00Z");

    @Autowired
    MonitorRepository monitors;

    @Autowired
    CheckResultRepository checkResults;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    UUID monitorId;

    @BeforeEach
    void setUp() {
        monitors.deleteAll();
        monitorId = monitors.save(new Monitor("m", MonitorType.HTTP, "https://example.com", 60, 5000))
                .getId();
    }

    private RetentionJob job(int days, int batchSize) {
        return new RetentionJob(jdbc, tx, new RetentionProperties(true, days, batchSize),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void insertResultsAgedDays(int count, int ageDays) {
        List<CheckResult> batch = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            batch.add(CheckResult.success(monitorId,
                    NOW.minus(Duration.ofDays(ageDays)).plusSeconds(i), 200, 10));
        }
        checkResults.saveAll(batch);
    }

    @Test
    void deletesOnlyExpiredResultsAcrossBatches() {
        insertResultsAgedDays(250, 40); // expired
        insertResultsAgedDays(30, 5);   // kept

        long deleted = job(30, 100).purgeExpired();

        assertThat(deleted).isEqualTo(250);
        assertThat(checkResults.count()).isEqualTo(30);
    }

    @Test
    void nothingToDeleteIsANoop() {
        insertResultsAgedDays(10, 1);

        assertThat(job(30, 100).purgeExpired()).isZero();
        assertThat(checkResults.count()).isEqualTo(10);
    }

    @Test
    void skipsWhileAnotherReplicaHoldsTheLock() throws Exception {
        insertResultsAgedDays(20, 40);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread otherReplica = new Thread(() -> tx.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class,
                    RetentionJob.ADVISORY_LOCK_KEY);
            locked.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        otherReplica.start();
        locked.await();

        long deleted = job(30, 100).purgeExpired();
        release.countDown();
        otherReplica.join();

        assertThat(deleted).isZero();
        assertThat(checkResults.count()).isEqualTo(20);
        assertThat(job(30, 100).purgeExpired()).as("runs once the lock is free").isEqualTo(20);
    }
}
