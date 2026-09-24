package dev.monitoring.api.alert;

import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.api.ApiIntegrationTest;
import dev.monitoring.common.crypto.SecretCipher;
import dev.monitoring.common.domain.AlertChannel;
import dev.monitoring.common.repository.AlertChannelRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@ApiIntegrationTest
class AlertChannelApiTests {

    private static final String CHANNELS = "/api/v1/alert-channels";
    private static final String SLACK_URL = "https://hooks.slack.com/services/T0000/B0000/SECRETTOKEN";

    @LocalServerPort
    int port;

    @Autowired
    AlertChannelRepository channels;

    @Autowired
    SecretCipher cipher;

    @Autowired
    JdbcTemplate jdbc;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        channels.deleteAll();
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private RestTestClient.ResponseSpec post(String json) {
        return client.post().uri(CHANNELS).contentType(MediaType.APPLICATION_JSON).body(json).exchange();
    }

    private AlertChannelResponse create(String json) {
        return post(json).expectStatus().isCreated()
                .expectBody(AlertChannelResponse.class).returnResult().getResponseBody();
    }

    @Test
    void slackChannelTargetIsEncryptedAndMasked() {
        AlertChannelResponse created = create("""
                {"name": "Ops Slack", "type": "SLACK", "target": "%s"}""".formatted(SLACK_URL));

        assertThat(created.targetPreview()).isEqualTo("https://hooks.slack.com/…");
        assertThat(created.signingSecret()).isNull();
        AlertChannel stored = channels.findById(created.id()).orElseThrow();
        assertThat(stored.getTargetEncrypted()).startsWith("v1:").doesNotContain("SECRETTOKEN");
        assertThat(cipher.decrypt(stored.getTargetEncrypted())).isEqualTo(SLACK_URL);

        String body = client.get().uri(CHANNELS).exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseBody();
        assertThat(body).contains("Ops Slack").doesNotContain("SECRETTOKEN").doesNotContain("signingSecret");
    }

    @Test
    void webhookChannelReturnsSigningSecretOnlyOnCreate() {
        AlertChannelResponse created = create("""
                {"name": "Pager", "type": "WEBHOOK", "target": "https://example.com/hooks/abc"}""");

        assertThat(created.signingSecret()).isNotBlank().hasSizeGreaterThanOrEqualTo(40);
        assertThat(channels.findById(created.id()).orElseThrow().getSigningSecretEncrypted()).startsWith("v1:");
        client.get().uri(CHANNELS + "/{id}", created.id()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.signingSecret").doesNotExist()
                .jsonPath("$.targetPreview").isEqualTo("https://example.com/…");
    }

    @Test
    void emailChannelIsNormalizedAndMasked() {
        AlertChannelResponse created = create("""
                {"name": "On-call", "type": "EMAIL", "target": " OnCall@Example.com "}""");

        assertThat(created.targetPreview()).isEqualTo("o***@example.com");
        assertThat(cipher.decrypt(channels.findById(created.id()).orElseThrow().getTargetEncrypted()))
                .isEqualTo("oncall@example.com");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "SLACK   | https://evil.example.com/services/x      | Slack target must be an incoming webhook URL",
            "SLACK   | http://hooks.slack.com/services/x         | Slack target must be an incoming webhook URL",
            "SLACK   | https://hooks.slack.com/api/other         | Slack target must be an incoming webhook URL",
            "WEBHOOK | http://example.com/hook                   | Webhook URL must use https",
            "WEBHOOK | https://169.254.169.254/latest/meta-data/ | not allowed",
            "WEBHOOK | https://intranet.example.com/hook         | not allowed",
            "WEBHOOK | ftp://example.com/hook                    | Only http and https URLs are allowed",
            "EMAIL   | not-an-email                              | valid e-mail address",
            "EMAIL   | a@b                                       | valid e-mail address",
            "EMAIL   | x@example.com\\nBcc: y@example.com         | valid e-mail address"})
    void rejectsUnsafeOrInvalidTargets(String type, String target, String message) {
        post("""
                {"name": "x", "type": "%s", "target": "%s"}""".formatted(type, target))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("target")
                .jsonPath("$.errors[0].message").value(String.class, m -> assertThat(m).contains(message));
        assertThat(channels.count()).isZero();
    }

    @Test
    void createRequiresTargetAndType() {
        post("""
                {"name": "x", "type": "EMAIL"}""").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("target");
        post("""
                {"name": "x", "target": "a@example.com"}""").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("type");
    }

    @Test
    void updateKeepsTargetWhenOmittedAndRejectsTypeChange() {
        AlertChannelResponse created = create("""
                {"name": "Ops", "type": "SLACK", "target": "%s"}""".formatted(SLACK_URL));

        client.put().uri(CHANNELS + "/{id}", created.id()).contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name": "Ops renamed", "type": "SLACK", "enabled": false}""")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("Ops renamed")
                .jsonPath("$.enabled").isEqualTo(false);
        assertThat(cipher.decrypt(channels.findById(created.id()).orElseThrow().getTargetEncrypted()))
                .isEqualTo(SLACK_URL);

        client.put().uri(CHANNELS + "/{id}", created.id()).contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name": "Ops", "type": "EMAIL", "target": "a@example.com"}""")
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void testEndpointQueuesDeliveryVisibleInHistory() {
        AlertChannelResponse created = create("""
                {"name": "Ops", "type": "EMAIL", "target": "ops@example.com"}""");

        client.post().uri(CHANNELS + "/{id}/test", created.id()).exchange()
                .expectStatus().isAccepted()
                .expectBody().jsonPath("$.eventType").isEqualTo("TEST")
                .jsonPath("$.status").isEqualTo("PENDING");

        client.get().uri(CHANNELS + "/{id}/deliveries", created.id()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.items[0].eventType").isEqualTo("TEST");
        String payload = jdbc.queryForObject("SELECT payload->>'event' FROM alert_deliveries", String.class);
        assertThat(payload).isEqualTo("TEST");
    }

    @Test
    void deleteRemovesChannelAndItsDeliveries() {
        AlertChannelResponse created = create("""
                {"name": "Ops", "type": "EMAIL", "target": "ops@example.com"}""");
        client.post().uri(CHANNELS + "/{id}/test", created.id()).exchange().expectStatus().isAccepted();

        client.delete().uri(CHANNELS + "/{id}", created.id()).exchange().expectStatus().isNoContent();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert_deliveries", Integer.class)).isZero();
        client.get().uri(CHANNELS + "/{id}", created.id()).exchange().expectStatus().isNotFound();
        client.get().uri(CHANNELS + "/{id}", UUID.randomUUID()).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.detail").isEqualTo("Alert channel not found");
    }
}
