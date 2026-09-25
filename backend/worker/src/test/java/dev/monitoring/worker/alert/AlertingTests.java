package dev.monitoring.worker.alert;

import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.common.crypto.SecretCipher;
import dev.monitoring.common.domain.AlertChannel;
import dev.monitoring.common.domain.AlertChannelType;
import dev.monitoring.common.domain.CheckErrorType;
import dev.monitoring.common.domain.HttpCheckMethod;
import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.domain.MonitorType;
import dev.monitoring.common.domain.User;
import dev.monitoring.common.repository.AlertChannelRepository;
import dev.monitoring.common.repository.MonitorRepository;
import dev.monitoring.common.repository.UserRepository;
import dev.monitoring.worker.WorkerIntegrationTest;
import dev.monitoring.worker.check.CheckOutcome;
import dev.monitoring.worker.check.CheckResultRecorder;
import dev.monitoring.worker.check.TestHttpServer;
import dev.monitoring.worker.scheduling.ClaimedMonitor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Outbox writes, dispatch, signing, retries and cancellation against real PostgreSQL. */
@WorkerIntegrationTest
class AlertingTests {

    private static final Instant T0 = Instant.parse("2030-01-01T00:00:00Z");
    private static final String SECRET = "test-signing-secret";

    static TestHttpServer receiver;

    @Autowired
    CheckResultRecorder recorder;

    @Autowired
    AlertDispatcher dispatcher;

    @Autowired
    MonitorRepository monitors;

    @Autowired
    AlertChannelRepository channels;

    @Autowired
    SecretCipher cipher;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    UserRepository users;

    ClaimedMonitor monitor;
    User owner;

    @BeforeAll
    static void startReceiver() throws Exception {
        receiver = new TestHttpServer();
    }

    @AfterAll
    static void stopReceiver() {
        receiver.close();
    }

    @BeforeEach
    void setUp() {
        monitors.deleteAll();
        channels.deleteAll();
        users.deleteAll();
        receiver.received.clear();
        owner = users.save(new User("owner@example.com", "{noop}unused", User.Role.USER));
        Monitor m = new Monitor("Shop <!channel>", MonitorType.HTTP, "https://shop.example.com", 30, 5000);
        m.setOwnerId(owner.getId());
        m = monitors.save(m);
        jdbc.update("UPDATE monitors SET failure_threshold = 1, recovery_threshold = 1 WHERE id = ?", m.getId());
        monitor = new ClaimedMonitor(m.getId(), m.getUrl(), HttpCheckMethod.GET, 5000, null, 30);
    }

    private AlertChannel webhook(String path, boolean enabled) {
        return webhook(path, enabled, owner);
    }

    private AlertChannel webhook(String path, boolean enabled, User channelOwner) {
        AlertChannel c = new AlertChannel("hook", AlertChannelType.WEBHOOK,
                cipher.encrypt("http://127.0.0.1:" + receiver.port() + path));
        c.setOwnerId(channelOwner.getId());
        c.setSigningSecretEncrypted(cipher.encrypt(SECRET));
        c.setEnabled(enabled);
        return channels.save(c);
    }

    private void fail(int second) {
        recorder.record(monitor, CheckOutcome.failure(T0.plusSeconds(second), null, 5000,
                CheckErrorType.TIMEOUT, "No complete response within 5000 ms"));
    }

    private void ok(int second) {
        recorder.record(monitor, CheckOutcome.success(T0.plusSeconds(second), 200, 50));
    }

    private List<Map<String, Object>> deliveries() {
        return jdbc.queryForList("SELECT * FROM alert_deliveries ORDER BY id");
    }

    @Test
    void incidentChangesEnqueueOneDeliveryPerEnabledChannel() {
        AlertChannel active = webhook("/hook", true);
        webhook("/hook", false);

        fail(0);   // opens (threshold 1)
        fail(30);  // still down: no duplicate
        ok(60);    // resolves

        List<Map<String, Object>> rows = deliveries();
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> assertThat(r.get("channel_id")).isEqualTo(active.getId()));
        assertThat(rows).extracting(r -> r.get("event_type"))
                .containsExactly("INCIDENT_OPENED", "INCIDENT_RESOLVED");

        JsonNode resolved = json.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM alert_deliveries WHERE event_type = 'INCIDENT_RESOLVED'", String.class));
        assertThat(resolved.path("monitor").path("name").asString()).isEqualTo("Shop <!channel>");
        assertThat(resolved.path("incident").path("durationSeconds").asLong()).isEqualTo(60);
        assertThat(resolved.path("incident").path("cause").asString()).startsWith("TIMEOUT");
    }

    @Test
    void otherUsersChannelsAreNeverNotified() {
        User stranger = users.save(new User("stranger@example.com", "{noop}unused", User.Role.USER));
        webhook("/hook", true, stranger);

        fail(0);

        assertThat(deliveries()).isEmpty();
    }

    @Test
    void webhookIsDeliveredWithVerifiableSignature() throws Exception {
        webhook("/hook", true);
        fail(0);

        assertThat(dispatcher.dispatchDue(5)).isEqualTo(1);

        assertThat(receiver.received).singleElement().satisfies(r -> {
            assertThat(r.method()).isEqualTo("POST");
            assertThat(r.headers()).containsEntry("x-monitoring-event", "INCIDENT_OPENED")
                    .containsEntry("content-type", "application/json; charset=UTF-8");
            String expected = "sha256=" + WebhookSender.sign(SECRET,
                    r.headers().get("x-monitoring-timestamp") + "." + r.body());
            assertThat(r.headers().get("x-monitoring-signature")).isEqualTo(expected);
            assertThat(json.readTree(r.body()).path("event").asString()).isEqualTo("INCIDENT_OPENED");
        });
        assertThat(deliveries()).singleElement().satisfies(d -> {
            assertThat(d.get("status")).isEqualTo("SENT");
            assertThat(d.get("sent_at")).isNotNull();
        });
    }

    @Test
    void failedDeliveryIsRetriedWithBackoffThenGivesUp() {
        webhook("/hook-fail", true);
        fail(0);

        dispatcher.dispatchDue(5);

        Map<String, Object> d = deliveries().get(0);
        assertThat(d.get("status")).isEqualTo("PENDING");
        assertThat(d.get("attempts")).isEqualTo(1);
        assertThat((String) d.get("last_error")).contains("HTTP 500");
        Boolean scheduledLater = jdbc.queryForObject(
                "SELECT next_attempt_at > now() + interval '20 seconds' FROM alert_deliveries", Boolean.class);
        assertThat(scheduledLater).isTrue();

        // Exhaust the remaining attempts.
        for (int i = 0; i < 10; i++) {
            jdbc.update("UPDATE alert_deliveries SET next_attempt_at = now() WHERE status = 'PENDING'");
            dispatcher.dispatchDue(5);
        }
        Map<String, Object> last = deliveries().get(0);
        assertThat(last.get("status")).isEqualTo("FAILED");
        assertThat(last.get("attempts")).isEqualTo(8);
    }

    @Test
    void redirectsAreNotFollowed() {
        webhook("/hook-redirect", true);
        fail(0);

        dispatcher.dispatchDue(5);

        assertThat(receiver.received).extracting(TestHttpServer.Received::path).containsExactly("/hook-redirect");
        assertThat((String) deliveries().get(0).get("last_error")).contains("HTTP 302");
    }

    @Test
    void deliveryForDisabledChannelIsCancelled() {
        AlertChannel channel = webhook("/hook", true);
        fail(0);
        jdbc.update("UPDATE alert_channels SET enabled = false WHERE id = ?", channel.getId());

        dispatcher.dispatchDue(5);

        assertThat(receiver.received).isEmpty();
        assertThat(deliveries().get(0).get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void testDeliveryFromApiIsSent() {
        AlertChannel channel = webhook("/hook", true);
        jdbc.update("""
                INSERT INTO alert_deliveries (channel_id, event_type, payload)
                VALUES (?, 'TEST', CAST('{"event":"TEST","message":"hello"}' AS jsonb))""", channel.getId());

        dispatcher.dispatchDue(5);

        assertThat(receiver.received).singleElement()
                .satisfies(r -> assertThat(r.headers()).containsEntry("x-monitoring-event", "TEST"));
    }
}
