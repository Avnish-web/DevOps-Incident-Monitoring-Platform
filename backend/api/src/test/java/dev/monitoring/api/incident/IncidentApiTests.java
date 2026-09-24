package dev.monitoring.api.incident;

import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.api.ApiIntegrationTest;
import dev.monitoring.common.domain.Incident;
import dev.monitoring.common.domain.IncidentResolution;
import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.domain.MonitorStatus;
import dev.monitoring.common.domain.MonitorType;
import dev.monitoring.common.repository.IncidentRepository;
import dev.monitoring.common.repository.MonitorRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@ApiIntegrationTest
class IncidentApiTests {

    private static final String INCIDENTS = "/api/v1/incidents";
    private static final Instant T0 = Instant.parse("2030-01-01T00:00:00Z");

    @LocalServerPort
    int port;

    @Autowired
    MonitorRepository monitors;

    @Autowired
    IncidentRepository incidents;

    @Autowired
    JdbcTemplate jdbc;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        monitors.deleteAll();
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private Monitor monitor(String name) {
        return monitors.save(new Monitor(name, MonitorType.HTTP, "https://example.com/" + name,
                60, 5000));
    }

    private Incident resolved(Monitor m, int startSecond, int endSecond) {
        Incident i = new Incident(m.getId(), T0.plusSeconds(startSecond), "TIMEOUT: slow");
        i.resolve(T0.plusSeconds(endSecond), IncidentResolution.RECOVERED);
        return incidents.save(i);
    }

    /** Simulates what the worker does when a monitor goes down. */
    private Incident markDown(Monitor m, int startSecond) {
        jdbc.update("UPDATE monitors SET status = 'DOWN', consecutive_failures = 3 WHERE id = ?",
                m.getId());
        return incidents.save(new Incident(m.getId(), T0.plusSeconds(startSecond), "DNS_FAILURE: x"));
    }

    // --- listing --------------------------------------------------------------------------

    @Test
    void listsNewestFirstWithMonitorNameAndDuration() {
        Monitor api = monitor("api");
        resolved(api, 0, 90);
        markDown(api, 500);

        client.get().uri(INCIDENTS).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.items[0].status").isEqualTo("OPEN")
                .jsonPath("$.items[0].monitorName").isEqualTo("api")
                .jsonPath("$.items[0].resolvedAt").doesNotExist()
                .jsonPath("$.items[1].status").isEqualTo("RESOLVED")
                .jsonPath("$.items[1].resolution").isEqualTo("RECOVERED")
                .jsonPath("$.items[1].durationSeconds").isEqualTo(90)
                .jsonPath("$.items[1].cause").isEqualTo("TIMEOUT: slow");
    }

    @Test
    void filtersByStatusAndMonitor() {
        Monitor a = monitor("a");
        Monitor b = monitor("b");
        resolved(a, 0, 10);
        markDown(a, 100);
        resolved(b, 0, 10);

        client.get().uri(INCIDENTS + "?status=OPEN").exchange()
                .expectBody().jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.items[0].monitorId").isEqualTo(a.getId().toString());
        client.get().uri(INCIDENTS + "?status=RESOLVED").exchange()
                .expectBody().jsonPath("$.totalElements").isEqualTo(2);
        client.get().uri(INCIDENTS + "?monitorId=" + b.getId()).exchange()
                .expectBody().jsonPath("$.totalElements").isEqualTo(1);
        client.get().uri(INCIDENTS + "?monitorId=" + a.getId() + "&status=RESOLVED").exchange()
                .expectBody().jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void rejectsInvalidFilters() {
        client.get().uri(INCIDENTS + "?status=BROKEN").exchange().expectStatus().isBadRequest();
        client.get().uri(INCIDENTS + "?monitorId=nope").exchange().expectStatus().isBadRequest();
        client.get().uri(INCIDENTS + "?size=1000").exchange().expectStatus().isBadRequest();
    }

    @Test
    void getsSingleIncident() {
        Incident incident = resolved(monitor("x"), 0, 60);

        client.get().uri(INCIDENTS + "/{id}", incident.getId()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(incident.getId().toString())
                .jsonPath("$.durationSeconds").isEqualTo(60);
        client.get().uri(INCIDENTS + "/{id}", UUID.randomUUID()).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.detail").isEqualTo("Incident not found");
    }

    @Test
    void incidentsAreReadOnly() {
        client.post().uri(INCIDENTS).contentType(MediaType.APPLICATION_JSON).body("{}")
                .exchange().expectStatus().isEqualTo(405);
    }

    // --- monitor changes while DOWN -------------------------------------------------------

    private void put(Monitor m, String json) {
        client.put().uri("/api/v1/monitors/{id}", m.getId())
                .contentType(MediaType.APPLICATION_JSON).body(json)
                .exchange().expectStatus().isOk();
    }

    @Test
    void pausingDownMonitorClosesIncidentAsPaused() {
        Monitor m = monitor("svc");
        Incident open = markDown(m, 0);

        put(m, """
                {"name": "svc", "url": "https://example.com/svc", "enabled": false}
                """);

        Incident closed = incidents.findById(open.getId()).orElseThrow();
        assertThat(closed.isOpen()).isFalse();
        assertThat(closed.getResolution()).isEqualTo(IncidentResolution.MONITOR_PAUSED);
        Monitor after = monitors.findById(m.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(MonitorStatus.UNKNOWN);
        assertThat(after.getConsecutiveFailures()).isZero();
    }

    @Test
    void changingTargetOfDownMonitorClosesIncidentAsChanged() {
        Monitor m = monitor("svc");
        Incident open = markDown(m, 0);

        put(m, """
                {"name": "svc", "url": "https://example.com/new-path"}
                """);

        assertThat(incidents.findById(open.getId()).orElseThrow().getResolution())
                .isEqualTo(IncidentResolution.MONITOR_CHANGED);
        assertThat(monitors.findById(m.getId()).orElseThrow().getStatus())
                .isEqualTo(MonitorStatus.UNKNOWN);
    }

    @Test
    void renamingDownMonitorKeepsIncidentOpen() {
        Monitor m = monitor("svc");
        Incident open = markDown(m, 0);

        put(m, """
                {"name": "svc renamed", "url": "https://example.com/svc", "failureThreshold": 5}
                """);

        assertThat(incidents.findById(open.getId()).orElseThrow().isOpen()).isTrue();
        assertThat(monitors.findById(m.getId()).orElseThrow().getStatus())
                .isEqualTo(MonitorStatus.DOWN);
    }
}
