package dev.monitoring.api.history;

import static dev.monitoring.api.FixedClockConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.api.ApiIntegrationTest;
import dev.monitoring.common.domain.CheckErrorType;
import dev.monitoring.common.domain.CheckResult;
import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.domain.MonitorType;
import dev.monitoring.common.repository.CheckResultRepository;
import dev.monitoring.common.repository.MonitorRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

@ApiIntegrationTest
class CheckHistoryApiTests {

    @LocalServerPort
    int port;

    @Autowired
    MonitorRepository monitors;

    @Autowired
    CheckResultRepository checkResults;

    RestTestClient client;
    UUID monitorId;

    @BeforeEach
    void setUp() {
        monitors.deleteAll();
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        monitorId = monitors.save(new Monitor("m", MonitorType.HTTP, "https://example.com", 60, 5000))
                .getId();
    }

    private void ok(Instant at, int latencyMs) {
        checkResults.save(CheckResult.success(monitorId, at, 200, latencyMs));
    }

    private void fail(Instant at) {
        checkResults.save(CheckResult.failure(monitorId, at, null, 5000, CheckErrorType.TIMEOUT, "slow"));
    }

    private static Instant minutesAgo(int minutes) {
        return NOW.minus(Duration.ofMinutes(minutes));
    }

    @Test
    void checksAreNewestFirstAndPaged() {
        ok(minutesAgo(3), 30);
        fail(minutesAgo(2));
        ok(minutesAgo(1), 10);

        client.get().uri("/api/v1/monitors/{id}/checks?size=2", monitorId).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(3)
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.items[0].latencyMs").isEqualTo(10)
                .jsonPath("$.items[1].success").isEqualTo(false)
                .jsonPath("$.items[1].errorType").isEqualTo("TIMEOUT");
    }

    @Test
    void statsSummarizeTheWindow() {
        for (int i = 1; i <= 10; i++) {
            ok(minutesAgo(i * 5), i * 100); // latencies 100..1000
        }
        fail(minutesAgo(7));
        fail(minutesAgo(8));
        ok(NOW.minus(Duration.ofHours(2)), 99_999); // outside the 1h window

        client.get().uri("/api/v1/monitors/{id}/stats?range=1h", monitorId).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.range").isEqualTo("1h")
                .jsonPath("$.bucketSeconds").isEqualTo(60)
                .jsonPath("$.from").isEqualTo("2030-06-01T11:00:00Z")
                .jsonPath("$.summary.totalChecks").isEqualTo(12)
                .jsonPath("$.summary.failedChecks").isEqualTo(2)
                .jsonPath("$.summary.uptimePercent").isEqualTo(83.333)
                .jsonPath("$.summary.avgLatencyMs").isEqualTo(550)
                .jsonPath("$.summary.p50LatencyMs").isEqualTo(550)
                .jsonPath("$.summary.p95LatencyMs").isEqualTo(955)
                .jsonPath("$.summary.maxLatencyMs").isEqualTo(1000)
                .jsonPath("$.series.length()").isEqualTo(60);
    }

    @Test
    void seriesBucketsAreFilledAndAligned() {
        ok(minutesAgo(1).plusSeconds(10), 100);
        ok(minutesAgo(1).plusSeconds(20), 300);
        fail(minutesAgo(1).plusSeconds(30));

        MonitorStatsResponse stats = client.get()
                .uri("/api/v1/monitors/{id}/stats?range=1h", monitorId).exchange()
                .expectStatus().isOk()
                .expectBody(MonitorStatsResponse.class).returnResult().getResponseBody();

        assertThat(stats).isNotNull();
        MonitorStatsResponse.Bucket last = stats.series().get(59);
        assertThat(last.bucketStart()).isEqualTo(Instant.parse("2030-06-01T11:59:00Z"));
        assertThat(last.checks()).isEqualTo(3);
        assertThat(last.failures()).isEqualTo(1);
        assertThat(last.avgLatencyMs()).isEqualTo(200);
        MonitorStatsResponse.Bucket empty = stats.series().get(0);
        assertThat(empty.checks()).isZero();
        assertThat(empty.avgLatencyMs()).as("gap, not zero latency").isNull();
    }

    @Test
    void emptyWindowHasNoUptime() {
        client.get().uri("/api/v1/monitors/{id}/stats?range=24h", monitorId).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.summary.totalChecks").isEqualTo(0)
                .jsonPath("$.summary.uptimePercent").doesNotExist()
                .jsonPath("$.series.length()").isEqualTo(96);
    }

    @Test
    void rejectsUnknownRangeAndMonitor() {
        client.get().uri("/api/v1/monitors/{id}/stats?range=1y", monitorId).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("range");
        client.get().uri("/api/v1/monitors/{id}/stats", UUID.randomUUID()).exchange()
                .expectStatus().isNotFound();
        client.get().uri("/api/v1/monitors/{id}/checks", UUID.randomUUID()).exchange()
                .expectStatus().isNotFound();
    }
}
