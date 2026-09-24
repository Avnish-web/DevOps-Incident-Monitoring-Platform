package dev.monitoring.worker.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.common.domain.HttpCheckMethod;
import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.domain.MonitorType;
import dev.monitoring.common.repository.MonitorRepository;
import dev.monitoring.worker.WorkerIntegrationTest;
import dev.monitoring.worker.check.HttpCheckRunner;
import dev.monitoring.worker.check.TestHttpServer;
import dev.monitoring.worker.scheduling.ClaimedMonitor;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

@WorkerIntegrationTest
class WorkerMetricsTests {

    static TestHttpServer server;

    @LocalServerPort
    int managementPort;

    @Autowired
    HttpCheckRunner runner;

    @Autowired
    WorkerMetrics metrics;

    @Autowired
    MonitorRepository monitors;

    @BeforeAll
    static void start() throws Exception {
        server = new TestHttpServer();
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    private String scrape() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build()
                .get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody();
    }

    private ClaimedMonitor monitor(String path) {
        Monitor m = monitors.save(new Monitor("m", MonitorType.HTTP,
                "http://127.0.0.1:" + server.port() + path, 60, 2000));
        return new ClaimedMonitor(m.getId(), m.getUrl(), HttpCheckMethod.GET, 2000, null, 60);
    }

    @Test
    void checksAreCountedByOutcomeAndErrorType() {
        runner.run(monitor("/ok"));
        runner.run(monitor("/status/500"));
        metrics.recordClaim(Duration.ofMillis(300));

        assertThat(scrape())
                .contains("monitoring_checks_total{application=\"monitoring-worker\",error_type=\"none\",outcome=\"success\"}")
                .contains("monitoring_checks_total{application=\"monitoring-worker\",error_type=\"unexpected_status\",outcome=\"failure\"}")
                .contains("monitoring_check_duration_seconds_bucket{application=\"monitoring-worker\",outcome=\"success\",le=\"0.25\"}")
                .contains("monitoring_scheduler_lag_seconds_bucket")
                .contains("monitoring_claims_total")
                .contains("monitoring_checks_in_flight{application=\"monitoring-worker\"} 0.0");
    }
}
