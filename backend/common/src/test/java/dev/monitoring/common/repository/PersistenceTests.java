package dev.monitoring.common.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.monitoring.common.PostgresTestcontainer;
import dev.monitoring.common.domain.CheckErrorType;
import dev.monitoring.common.domain.CheckResult;
import dev.monitoring.common.domain.HttpCheckMethod;
import dev.monitoring.common.domain.Incident;
import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.domain.MonitorStatus;
import dev.monitoring.common.domain.MonitorType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs Flyway against a real PostgreSQL, then validates the JPA mapping against the migrated
 * schema ({@code ddl-auto=validate}) and checks that the database enforces its constraints.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainer.class)
class PersistenceTests {

    @Autowired
    MonitorRepository monitors;

    @Autowired
    CheckResultRepository checkResults;

    @Autowired
    IncidentRepository incidents;

    @Autowired
    JdbcTemplate jdbc;

    private Monitor newMonitor() {
        return new Monitor("Example", MonitorType.HTTP, "https://example.com", 60, 5000);
    }

    @Test
    void flywayAppliedInitialMigration() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void savesMonitorWithDefaults() {
        Monitor saved = monitors.saveAndFlush(newMonitor());

        Monitor loaded = monitors.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getHttpMethod()).isEqualTo(HttpCheckMethod.GET);
        assertThat(loaded.getStatus()).isEqualTo(MonitorStatus.UNKNOWN);
        assertThat(loaded.getFailureThreshold()).isEqualTo(3);
        assertThat(loaded.getRecoveryThreshold()).isEqualTo(2);
        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getNextCheckAt()).isNotNull();
    }

    @Test
    void databaseRejectsIntervalBelowMinimum() {
        Monitor monitor = newMonitor();
        monitor.setIntervalSeconds(5);

        assertThatThrownBy(() -> monitors.saveAndFlush(monitor))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_monitors_interval");
    }

    @Test
    void databaseRejectsNonHttpUrl() {
        Monitor monitor = newMonitor();
        monitor.setUrl("file:///etc/passwd");

        assertThatThrownBy(() -> monitors.saveAndFlush(monitor))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_monitors_url_scheme");
    }

    @Test
    void savesCheckResults() {
        Monitor monitor = monitors.saveAndFlush(newMonitor());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        CheckResult ok = checkResults.saveAndFlush(
                CheckResult.success(monitor.getId(), now, 200, 123));
        CheckResult failed = checkResults.saveAndFlush(CheckResult.failure(
                monitor.getId(), now, null, null, CheckErrorType.TIMEOUT, "x".repeat(600)));

        assertThat(ok.getId()).isNotNull();
        assertThat(checkResults.findById(ok.getId()).orElseThrow().getLatencyMs()).isEqualTo(123);
        assertThat(checkResults.findById(failed.getId()).orElseThrow().getErrorMessage())
                .hasSize(512);
    }

    @Test
    void databaseRejectsFailedCheckWithoutErrorType() {
        Monitor monitor = monitors.saveAndFlush(newMonitor());

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO check_results (monitor_id, checked_at, success) VALUES (?, now(), false)",
                monitor.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_check_results_error");
    }

    @Test
    void onlyOneOpenIncidentPerMonitor() {
        Monitor monitor = monitors.saveAndFlush(newMonitor());
        incidents.saveAndFlush(new Incident(monitor.getId(), Instant.now(), "TIMEOUT"));

        assertThatThrownBy(() -> incidents.saveAndFlush(
                new Incident(monitor.getId(), Instant.now(), "TIMEOUT")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_incidents_open_per_monitor");
    }

    @Test
    void resolvedIncidentAllowsNewOpenIncident() {
        Monitor monitor = monitors.saveAndFlush(newMonitor());
        Instant start = Instant.now().minusSeconds(60);
        Incident first = new Incident(monitor.getId(), start, "TIMEOUT");
        first.resolve(start.plusSeconds(30));
        incidents.saveAndFlush(first);

        Incident second = incidents.saveAndFlush(
                new Incident(monitor.getId(), Instant.now(), "DNS_FAILURE"));

        assertThat(second.isOpen()).isTrue();
        assertThat(incidents.count()).isEqualTo(2);
    }

    @Test
    void deletingMonitorCascadesToHistory() {
        Monitor monitor = monitors.saveAndFlush(newMonitor());
        checkResults.saveAndFlush(CheckResult.success(monitor.getId(), Instant.now(), 200, 50));
        incidents.saveAndFlush(new Incident(monitor.getId(), Instant.now(), "TIMEOUT"));

        monitors.delete(monitor);
        monitors.flush();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM check_results", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM incidents", Integer.class)).isZero();
    }
}
