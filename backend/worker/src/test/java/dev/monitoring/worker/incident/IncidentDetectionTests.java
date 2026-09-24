package dev.monitoring.worker.incident;

import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.common.domain.CheckErrorType;
import dev.monitoring.common.domain.HttpCheckMethod;
import dev.monitoring.common.domain.Incident;
import dev.monitoring.common.domain.IncidentResolution;
import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.domain.MonitorStatus;
import dev.monitoring.common.domain.MonitorType;
import dev.monitoring.common.repository.CheckResultRepository;
import dev.monitoring.common.repository.IncidentRepository;
import dev.monitoring.common.repository.MonitorRepository;
import dev.monitoring.worker.WorkerIntegrationTest;
import dev.monitoring.worker.check.CheckOutcome;
import dev.monitoring.worker.check.CheckResultRecorder;
import dev.monitoring.worker.incident.IncidentStateMachine.Event;
import dev.monitoring.worker.scheduling.ClaimedMonitor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Incident detection through the real recorder and PostgreSQL. */
@WorkerIntegrationTest
class IncidentDetectionTests {

    private static final Instant T0 = Instant.parse("2030-01-01T00:00:00Z");

    @Autowired
    CheckResultRecorder recorder;

    @Autowired
    MonitorRepository monitors;

    @Autowired
    IncidentRepository incidents;

    @Autowired
    CheckResultRepository checkResults;

    @Autowired
    JdbcTemplate jdbc;

    ClaimedMonitor monitor;

    @BeforeEach
    void setUp() {
        monitors.deleteAll();
        Monitor m = monitors.save(
                new Monitor("svc", MonitorType.HTTP, "https://example.com", 30, 5000));
        monitor = new ClaimedMonitor(m.getId(), m.getUrl(), HttpCheckMethod.GET, 5000, null, 30);
    }

    private Event ok(int second) {
        return recorder.record(monitor, CheckOutcome.success(T0.plusSeconds(second), 200, 50));
    }

    private Event fail(int second) {
        return recorder.record(monitor, CheckOutcome.failure(T0.plusSeconds(second), null, 5000,
                CheckErrorType.TIMEOUT, "No complete response within 5000 ms"));
    }

    private Monitor reload() {
        return monitors.findById(monitor.id()).orElseThrow();
    }

    private List<Incident> incidentsOfMonitor() {
        return incidents.findAll().stream()
                .filter(i -> i.getMonitorId().equals(monitor.id())).toList();
    }

    @Test
    void firstSuccessMarksMonitorUp() {
        assertThat(ok(0)).isEqualTo(Event.NONE);

        assertThat(reload().getStatus()).isEqualTo(MonitorStatus.UP);
        assertThat(incidentsOfMonitor()).isEmpty();
    }

    @Test
    void opensIncidentAfterThresholdWithDownSinceAtFirstFailure() {
        ok(0);
        assertThat(fail(30)).isEqualTo(Event.NONE);
        assertThat(fail(60)).isEqualTo(Event.NONE);
        assertThat(reload().getStatus()).as("below threshold").isEqualTo(MonitorStatus.UP);

        assertThat(fail(90)).isEqualTo(Event.INCIDENT_OPENED);

        Monitor m = reload();
        assertThat(m.getStatus()).isEqualTo(MonitorStatus.DOWN);
        assertThat(m.getConsecutiveFailures()).isEqualTo(3);
        assertThat(m.getVersion()).as("state changes never bump the user-facing version").isZero();
        assertThat(incidentsOfMonitor()).singleElement().satisfies(i -> {
            assertThat(i.isOpen()).isTrue();
            assertThat(i.getStartedAt()).as("down since first failure").isEqualTo(T0.plusSeconds(30));
            assertThat(i.getCause()).isEqualTo("TIMEOUT: No complete response within 5000 ms");
        });
    }

    @Test
    void continuedFailuresDoNotOpenMoreIncidents() {
        for (int i = 0; i < 10; i++) {
            fail(i * 30);
        }

        assertThat(incidentsOfMonitor()).hasSize(1);
        assertThat(reload().getConsecutiveFailures()).isEqualTo(10);
    }

    @Test
    void resolvesAfterRecoveryThresholdAtFirstSuccess() {
        fail(0);
        fail(30);
        fail(60);
        assertThat(ok(90)).isEqualTo(Event.NONE);
        assertThat(reload().getStatus()).as("one success is not enough").isEqualTo(MonitorStatus.DOWN);

        assertThat(ok(120)).isEqualTo(Event.INCIDENT_RESOLVED);

        assertThat(reload().getStatus()).isEqualTo(MonitorStatus.UP);
        assertThat(incidentsOfMonitor()).singleElement().satisfies(i -> {
            assertThat(i.isOpen()).isFalse();
            assertThat(i.getStartedAt()).isEqualTo(T0);
            assertThat(i.getResolvedAt()).as("up again since first success").isEqualTo(T0.plusSeconds(90));
            assertThat(i.getResolution()).isEqualTo(IncidentResolution.RECOVERED);
        });
    }

    @Test
    void secondOutageCreatesSecondIncident() {
        fail(0);
        fail(30);
        fail(60);
        ok(90);
        ok(120);
        fail(150);
        fail(180);
        assertThat(fail(210)).isEqualTo(Event.INCIDENT_OPENED);

        assertThat(incidentsOfMonitor()).hasSize(2)
                .filteredOn(Incident::isOpen).singleElement()
                .extracting(Incident::getStartedAt).isEqualTo(T0.plusSeconds(150));
    }

    @Test
    void lateOlderResultIsStoredButDoesNotChangeState() {
        ok(100);
        assertThat(fail(50)).isEqualTo(Event.NONE); // finished after a newer check

        Monitor m = reload();
        assertThat(m.getStatus()).isEqualTo(MonitorStatus.UP);
        assertThat(m.getConsecutiveFailures()).isZero();
        assertThat(checkResults.count()).isEqualTo(2);
    }

    @Test
    void pausedMonitorRecordsResultsWithoutStateChanges() {
        jdbc.update("UPDATE monitors SET enabled = false WHERE id = ?", monitor.id());

        fail(0);
        fail(30);
        fail(60);

        Monitor m = reload();
        assertThat(m.getStatus()).isEqualTo(MonitorStatus.UNKNOWN);
        assertThat(m.getLastCheckedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(incidentsOfMonitor()).isEmpty();
        assertThat(checkResults.count()).isEqualTo(3);
    }

    @Test
    void lowerThresholdOpensImmediately() {
        jdbc.update("UPDATE monitors SET failure_threshold = 1 WHERE id = ?", monitor.id());

        assertThat(fail(0)).isEqualTo(Event.INCIDENT_OPENED);
    }
}
