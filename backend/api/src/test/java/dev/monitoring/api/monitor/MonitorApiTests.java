package dev.monitoring.api.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.api.ApiIntegrationTest;
import dev.monitoring.api.TestSessions;
import dev.monitoring.common.domain.User;
import dev.monitoring.common.repository.MonitorRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@ApiIntegrationTest
class MonitorApiTests {

    private static final String MONITORS = "/api/v1/monitors";

    @LocalServerPort
    int port;

    @Autowired
    MonitorRepository monitorRepository;

    RestTestClient client;

    @Autowired
    TestSessions sessions;

    User owner;

    @BeforeEach
    void setUp() {
        monitorRepository.deleteAll();
        owner = sessions.user("monitors@example.com");
        client = sessions.login(port, owner);
    }

    private static String body(String name, String url) {
        return """
                {"name": "%s", "url": "%s"}
                """.formatted(name, url);
    }

    private MonitorResponse create(String name, String url) {
        return client.post().uri(MONITORS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(name, url))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(MonitorResponse.class)
                .returnResult().getResponseBody();
    }

    // --- create ---------------------------------------------------------------------------

    @Test
    void createAppliesDefaultsAndReturnsLocationAndEtag() {
        client.post().uri(MONITORS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body("  Example  ", "https://example.com/health"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, ".*/api/v1/monitors/[0-9a-f-]{36}$")
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"0\"")
                .expectBody()
                .jsonPath("$.name").isEqualTo("Example")
                .jsonPath("$.type").isEqualTo("HTTP")
                .jsonPath("$.httpMethod").isEqualTo("GET")
                .jsonPath("$.intervalSeconds").isEqualTo(60)
                .jsonPath("$.timeoutMs").isEqualTo(5000)
                .jsonPath("$.failureThreshold").isEqualTo(3)
                .jsonPath("$.recoveryThreshold").isEqualTo(2)
                .jsonPath("$.enabled").isEqualTo(true)
                .jsonPath("$.status").isEqualTo("UNKNOWN")
                .jsonPath("$.createdAt").isNotEmpty()
                .jsonPath("$.consecutiveFailures").doesNotExist();
    }

    @Test
    void createAcceptsAllOptionalFields() {
        client.post().uri(MONITORS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name": "Status page", "url": "https://status.example.org",
                         "httpMethod": "HEAD", "intervalSeconds": 300, "timeoutMs": 10000,
                         "expectedStatus": 204, "failureThreshold": 5, "recoveryThreshold": 1,
                         "enabled": false}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.httpMethod").isEqualTo("HEAD")
                .jsonPath("$.intervalSeconds").isEqualTo(300)
                .jsonPath("$.expectedStatus").isEqualTo(204)
                .jsonPath("$.enabled").isEqualTo(false);
    }

    @Test
    void createReportsAllFieldErrors() {
        client.post().uri(MONITORS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name": "", "url": "https://example.com", "intervalSeconds": 5,
                         "timeoutMs": 100, "expectedStatus": 700}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(4)
                .jsonPath("$.errors[0].field").isEqualTo("expectedStatus")
                .jsonPath("$.errors[1].field").isEqualTo("intervalSeconds")
                .jsonPath("$.errors[2].field").isEqualTo("name")
                .jsonPath("$.errors[3].field").isEqualTo("timeoutMs");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://169.254.169.254/latest/meta-data/", "http://localhost:8080/",
            "http://127.0.0.1/", "http://[::1]/", "http://intranet.example.com/",
            "http://10.0.0.1/"})
    void createRejectsInternalTargets(String url) {
        client.post().uri(MONITORS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body("ssrf", url))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("url")
                .jsonPath("$.errors[0].message").value(String.class,
                        m -> assertThat(m).contains("not allowed"));
        assertThat(monitorRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "ftp://example.com", "https://u:p@example.com"})
    void createRejectsUnsafeUrls(String url) {
        client.post().uri(MONITORS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body("bad", url))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("url");
    }

    @Test
    void createRejectsUnknownAndReadOnlyFields() {
        client.post().uri(MONITORS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name": "x", "url": "https://example.com", "status": "UP"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createRejectsUnsupportedHttpMethod() {
        client.post().uri(MONITORS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name": "x", "url": "https://example.com", "httpMethod": "DELETE"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createRequiresJson() {
        client.post().uri(MONITORS)
                .contentType(MediaType.TEXT_PLAIN)
                .body("name=x")
                .exchange()
                .expectStatus().isEqualTo(415);
    }

    // --- read -----------------------------------------------------------------------------

    @Test
    void getReturnsMonitorWithEtag() {
        MonitorResponse created = create("Example", "https://example.com");

        client.get().uri(MONITORS + "/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"0\"")
                .expectBody().jsonPath("$.id").isEqualTo(created.id().toString());
    }

    @Test
    void getUnknownIdReturns404() {
        client.get().uri(MONITORS + "/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.detail").isEqualTo("Monitor not found");
    }

    @Test
    void getMalformedIdReturns400() {
        client.get().uri(MONITORS + "/not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void listPaginatesAndSorts() {
        create("Charlie", "https://example.com/c");
        create("Alpha", "https://example.com/a");
        create("Bravo", "https://example.com/b");

        client.get().uri(MONITORS + "?page=0&size=2&sort=name,asc")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.items[0].name").isEqualTo("Alpha")
                .jsonPath("$.items[1].name").isEqualTo("Bravo")
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(2)
                .jsonPath("$.totalElements").isEqualTo(3)
                .jsonPath("$.totalPages").isEqualTo(2);

        client.get().uri(MONITORS + "?page=1&size=2&sort=name,asc")
                .exchange()
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.items[0].name").isEqualTo("Charlie");
    }

    @ParameterizedTest
    @ValueSource(strings = {"?size=0", "?size=101", "?page=-1", "?sort=url,asc",
            "?sort=name;drop", "?sort=name,sideways"})
    void listRejectsInvalidParameters(String query) {
        client.get().uri(MONITORS + query)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").exists();
    }

    // --- update ---------------------------------------------------------------------------

    @Test
    void updateReplacesConfigurationAndBumpsVersion() {
        MonitorResponse created = create("Example", "https://example.com");

        client.put().uri(MONITORS + "/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .body("""
                        {"name": "Renamed", "url": "https://status.example.org",
                         "intervalSeconds": 120}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"1\"")
                .expectBody()
                .jsonPath("$.name").isEqualTo("Renamed")
                .jsonPath("$.url").isEqualTo("https://status.example.org")
                .jsonPath("$.intervalSeconds").isEqualTo(120)
                .jsonPath("$.version").isEqualTo(1);
    }

    @Test
    void updateWithStaleIfMatchReturns412AndChangesNothing() {
        MonitorResponse created = create("Example", "https://example.com");
        client.put().uri(MONITORS + "/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body("First edit", "https://example.com"))
                .exchange()
                .expectStatus().isOk();

        client.put().uri(MONITORS + "/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .body(body("Stale edit", "https://example.com"))
                .exchange()
                .expectStatus().isEqualTo(412);

        assertThat(monitorRepository.findById(created.id()).orElseThrow().getName())
                .isEqualTo("First edit");
    }

    @Test
    void updateRejectsInternalTarget() {
        MonitorResponse created = create("Example", "https://example.com");

        client.put().uri(MONITORS + "/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body("Example", "http://169.254.169.254/"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("url");

        assertThat(monitorRepository.findById(created.id()).orElseThrow().getUrl())
                .isEqualTo("https://example.com");
    }

    @Test
    void updateUnknownIdReturns404() {
        client.put().uri(MONITORS + "/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body("x", "https://example.com"))
                .exchange()
                .expectStatus().isNotFound();
    }

    // --- delete ---------------------------------------------------------------------------

    @Test
    void deleteRemovesMonitor() {
        MonitorResponse created = create("Example", "https://example.com");

        client.delete().uri(MONITORS + "/{id}", created.id())
                .exchange()
                .expectStatus().isNoContent();
        client.get().uri(MONITORS + "/{id}", created.id())
                .exchange()
                .expectStatus().isNotFound();
        client.delete().uri(MONITORS + "/{id}", created.id())
                .exchange()
                .expectStatus().isNotFound();
    }
}
