package dev.monitoring.api.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.api.ApiIntegrationTest;
import dev.monitoring.api.TestSessions;
import dev.monitoring.common.domain.Incident;
import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.domain.MonitorType;
import dev.monitoring.common.repository.IncidentRepository;
import dev.monitoring.common.repository.MonitorRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@ApiIntegrationTest
class ApiMetricsTests {

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    @Autowired
    MonitorRepository monitors;

    @Autowired
    IncidentRepository incidents;

    @Autowired
    FleetMetrics fleetMetrics;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestSessions sessions;

    @BeforeEach
    void clean() {
        monitors.deleteAll();
    }

    private String scrape() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build()
                .get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody();
    }

    @Test
    void exposesFleetGaugesPerStateAndMonitor() {
        Monitor up = monitors.save(new Monitor("shop", MonitorType.HTTP, "https://example.com/a", 60, 5000));
        Monitor down = monitors.save(new Monitor("api", MonitorType.HTTP, "https://example.com/b", 60, 5000));
        Monitor paused = monitors.save(new Monitor("old", MonitorType.HTTP, "https://example.com/c", 60, 5000));
        jdbc.update("UPDATE monitors SET status = 'UP' WHERE id = ?", up.getId());
        jdbc.update("UPDATE monitors SET status = 'DOWN' WHERE id = ?", down.getId());
        jdbc.update("UPDATE monitors SET enabled = false WHERE id = ?", paused.getId());
        incidents.save(new Incident(down.getId(), Instant.now(), "TIMEOUT: x"));

        fleetMetrics.refresh();
        String metrics = scrape();

        assertThat(metrics)
                .contains("monitoring_monitors{application=\"monitoring-api\",state=\"up\"} 1.0")
                .contains("monitoring_monitors{application=\"monitoring-api\",state=\"down\"} 1.0")
                .contains("monitoring_monitors{application=\"monitoring-api\",state=\"paused\"} 1.0")
                .contains("monitoring_open_incidents{application=\"monitoring-api\"} 1.0")
                .contains("monitor_id=\"" + up.getId() + "\",monitor_name=\"shop\"} 1.0")
                .contains("monitor_id=\"" + down.getId() + "\",monitor_name=\"api\"} 0.0")
                .doesNotContain("monitor_name=\"old\"");
    }

    @Test
    void exposesHttpServerMetricsWithSloBuckets() {
        sessions.login(port, sessions.user("metrics@example.com"))
                .get().uri("/api/v1/monitors").exchange().expectStatus().isOk();

        assertThat(scrape())
                .contains("http_server_requests_seconds_bucket")
                .contains("uri=\"/api/v1/monitors\"")
                .contains("le=\"0.25\"");
    }

    @Test
    void prometheusIsNotOnThePublicPort() {
        // Anonymous callers are rejected before any endpoint is reached.
        RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .get().uri("/actuator/prometheus").exchange().expectStatus().isUnauthorized();
        sessions.login(port, sessions.user("metrics@example.com"))
                .get().uri("/actuator/prometheus").exchange().expectStatus().isForbidden();
    }
}
