package dev.monitoring.worker.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.common.domain.CheckErrorType;
import dev.monitoring.common.domain.CheckResult;
import dev.monitoring.common.domain.HttpCheckMethod;
import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.domain.MonitorType;
import dev.monitoring.common.repository.CheckResultRepository;
import dev.monitoring.common.repository.MonitorRepository;
import dev.monitoring.worker.WorkerIntegrationTest;
import dev.monitoring.worker.scheduling.ClaimedMonitor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** End-to-end: real HTTP to a local server, results persisted to PostgreSQL. */
@WorkerIntegrationTest
class HttpCheckRunnerTests {

    static TestHttpServer server;

    @Autowired
    HttpCheckRunner runner;

    @Autowired
    CheckResultRecorder recorder;

    @Autowired
    MonitorRepository monitors;

    @Autowired
    CheckResultRepository checkResults;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeAll
    static void startServer() throws Exception {
        server = new TestHttpServer();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @BeforeEach
    void clean() {
        monitors.deleteAll();
    }

    private ClaimedMonitor saveMonitor(String path) {
        Monitor m = monitors.save(new Monitor("local", MonitorType.HTTP,
                "http://127.0.0.1:" + server.port() + path, 60, 2000));
        return new ClaimedMonitor(m.getId(), m.getUrl(), HttpCheckMethod.GET, 2000, null, 60);
    }

    private List<CheckResult> resultsFor(ClaimedMonitor m) {
        return checkResults.findAll().stream()
                .filter(r -> r.getMonitorId().equals(m.id())).toList();
    }

    private Instant lastCheckedAt(ClaimedMonitor m) {
        return jdbc.queryForObject("SELECT last_checked_at FROM monitors WHERE id = ?",
                Instant.class, m.id());
    }

    @Test
    void successfulCheckIsPersisted() {
        ClaimedMonitor monitor = saveMonitor("/ok");

        runner.run(monitor);

        assertThat(resultsFor(monitor)).singleElement().satisfies(r -> {
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getStatusCode()).isEqualTo(200);
            assertThat(r.getLatencyMs()).isNotNull().isGreaterThanOrEqualTo(0);
            assertThat(r.getErrorType()).isNull();
        });
        assertThat(lastCheckedAt(monitor)).isNotNull();
        assertThat(monitors.findById(monitor.id()).orElseThrow().getVersion())
                .as("recording must not bump the user-facing version").isZero();
    }

    @Test
    void failedCheckIsPersistedWithErrorDetails() {
        ClaimedMonitor monitor = saveMonitor("/status/500");

        runner.run(monitor);

        assertThat(resultsFor(monitor)).singleElement().satisfies(r -> {
            assertThat(r.isSuccess()).isFalse();
            assertThat(r.getStatusCode()).isEqualTo(500);
            assertThat(r.getErrorType()).isEqualTo(CheckErrorType.UNEXPECTED_STATUS);
            assertThat(r.getErrorMessage()).isEqualTo("HTTP 500 (expected 2xx or 3xx)");
        });
    }

    @Test
    void monitorDeletedDuringCheckIsIgnored() {
        ClaimedMonitor monitor = saveMonitor("/ok");
        monitors.deleteById(monitor.id());

        runner.run(monitor); // must not throw

        assertThat(checkResults.count()).isZero();
    }

    @Test
    void lastCheckedAtOnlyMovesForward() {
        ClaimedMonitor monitor = saveMonitor("/ok");
        Instant t0 = Instant.parse("2030-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2030-01-01T00:00:30Z");
        Instant t2 = Instant.parse("2030-01-01T00:01:00Z");

        recorder.record(monitor, CheckOutcome.success(t0, 200, 10));
        assertThat(lastCheckedAt(monitor)).isEqualTo(t0);

        recorder.record(monitor, CheckOutcome.success(t2, 200, 10));
        assertThat(lastCheckedAt(monitor)).as("newer result advances it").isEqualTo(t2);

        recorder.record(monitor, CheckOutcome.success(t1, 200, 10)); // finished out of order
        assertThat(lastCheckedAt(monitor)).as("older result does not rewind it").isEqualTo(t2);
        assertThat(resultsFor(monitor)).hasSize(3);
    }
}
