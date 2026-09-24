package dev.monitoring.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

@WorkerIntegrationTest
class MonitoringWorkerApplicationTests {

    @LocalServerPort
    int port;

    RestTestClient management;

    @BeforeEach
    void setUp() {
        management = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void healthProbesAreUp() {
        for (String path : new String[] {"/actuator/health", "/actuator/health/liveness",
                "/actuator/health/readiness"}) {
            management.get().uri(path).exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.status").isEqualTo("UP");
        }
    }

    @Test
    void exposesNoApplicationEndpoints() {
        management.get().uri("/api/v1/monitors").exchange()
                .expectStatus().isNotFound();
    }
}
