package dev.monitoring.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.api.ApiIntegrationTest;
import dev.monitoring.api.TestSessions;
import dev.monitoring.common.crypto.SecretCipher;
import dev.monitoring.common.domain.AlertChannel;
import dev.monitoring.common.domain.AlertChannelType;
import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.domain.MonitorType;
import dev.monitoring.common.domain.User;
import dev.monitoring.common.repository.AlertChannelRepository;
import dev.monitoring.common.repository.MonitorRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;

/** Abuse limits and the security audit trail. */
@ApiIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class HardeningApiTests {

    @LocalServerPort
    int port;

    @Autowired
    TestSessions sessions;

    @Autowired
    MonitorRepository monitors;

    @Autowired
    AlertChannelRepository channels;

    @Autowired
    SecretCipher cipher;

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    @Test
    void monitorQuotaIsEnforced() {
        User user = sessions.user(unique("quota"));
        List<Monitor> existing = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Monitor m = new Monitor("m" + i, MonitorType.HTTP, "https://example.com/" + i, 60, 5000);
            m.setOwnerId(user.getId());
            existing.add(m);
        }
        monitors.saveAll(existing);

        sessions.login(port, user).post().uri("/api/v1/monitors").contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"one too many\",\"url\":\"https://example.com\"}")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.detail").isEqualTo("Limit reached: at most 100 monitors per user");
    }

    @Test
    void alertChannelQuotaIsEnforced() {
        User user = sessions.user(unique("channels"));
        for (int i = 0; i < 20; i++) {
            AlertChannel c = new AlertChannel("c" + i, AlertChannelType.EMAIL, cipher.encrypt("x@example.com"));
            c.setOwnerId(user.getId());
            channels.save(c);
        }

        sessions.login(port, user).post().uri("/api/v1/alert-channels").contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"extra\",\"type\":\"EMAIL\",\"target\":\"y@example.com\"}")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void securityEventsAreAuditedWithoutSecrets(CapturedOutput output) {
        User user = sessions.user(unique("audited"));

        var anonymous = TestSessions.anonymous(port);
        String xsrf = TestSessions.cookie(anonymous.get().uri("/api/v1/auth/csrf").exchange()
                .returnResult(Void.class).getResponseHeaders(), "XSRF-TOKEN");
        anonymous.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .cookie("XSRF-TOKEN", xsrf).header("X-XSRF-TOKEN", xsrf)
                .body("{\"email\":\"%s\",\"password\":\"definitely-the-wrong-password\"}".formatted(user.getEmail()))
                .exchange()
                .expectStatus().isUnauthorized();
        sessions.login(port, user).post().uri("/api/v1/auth/logout").exchange().expectStatus().isNoContent();

        String logs = output.getAll();
        assertThat(logs).contains("auth.login.failure").contains("auth.login.success").contains("auth.logout");
        assertThat(logs).contains(AuditLog.hash(user.getEmail()));
        assertThat(logs).doesNotContain("definitely-the-wrong-password").doesNotContain(TestSessions.PASSWORD);
        assertThat(logs).doesNotContain("\"accountHash\":\"" + user.getEmail());
    }

    @Test
    void accountHashIsStableAndShort() {
        assertThat(AuditLog.hash(" Alice@Example.com ")).isEqualTo(AuditLog.hash("alice@example.com")).hasSize(16);
        assertThat(AuditLog.hash("bob@example.com")).isNotEqualTo(AuditLog.hash("alice@example.com"));
    }
}
