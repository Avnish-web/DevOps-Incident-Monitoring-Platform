package dev.monitoring.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.api.web.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Boots the full application on random ports and exercises it over real HTTP. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class MonitoringApiApplicationTests {

    @LocalServerPort
    int serverPort;

    @LocalManagementPort
    int managementPort;

    RestTestClient api;
    RestTestClient management;

    @BeforeEach
    void setUp() {
        api = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
        management = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + managementPort).build();
    }

    @Test
    void healthLivenessAndReadinessAreUpOnManagementPort() {
        for (String path : new String[] {"/actuator/health", "/actuator/health/liveness",
                "/actuator/health/readiness"}) {
            management.get().uri(path).exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.status").isEqualTo("UP");
        }
    }

    @Test
    void actuatorIsNotExposedOnPublicPort() {
        api.get().uri("/actuator/health").exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void unknownPathReturnsProblemDetailWithRequestId() {
        api.get().uri("/api/v1/does-not-exist")
                .header(RequestIdFilter.HEADER, "test-123")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals(RequestIdFilter.HEADER, "test-123")
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.requestId").isEqualTo("test-123");
    }

    @Test
    void unsafeRequestIdIsReplaced() {
        String returned = api.get().uri("/api/v1/does-not-exist")
                .header(RequestIdFilter.HEADER, "<script>forged</script>")
                .exchange()
                .returnResult(String.class)
                .getResponseHeaders()
                .getFirst(RequestIdFilter.HEADER);
        assertThat(returned).isNotNull().doesNotContain("forged").hasSize(36);
    }
}
