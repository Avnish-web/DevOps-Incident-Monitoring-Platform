package dev.monitoring.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.api.ApiIntegrationTest;
import dev.monitoring.api.TestSessions;
import dev.monitoring.api.security.AdminBootstrap;
import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.domain.MonitorType;
import dev.monitoring.common.domain.User;
import dev.monitoring.common.repository.MonitorRepository;
import dev.monitoring.common.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

@ApiIntegrationTest
class AuthApiTests {

    @LocalServerPort
    int port;

    @Autowired
    TestSessions sessions;

    @Autowired
    UserRepository users;

    @Autowired
    MonitorRepository monitors;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private RestTestClient.ResponseSpec login(String email, String password) {
        RestTestClient anon = TestSessions.anonymous(port);
        String xsrf = TestSessions.cookie(anon.get().uri("/api/v1/auth/csrf").exchange()
                .returnResult(Void.class).getResponseHeaders(), "XSRF-TOKEN");
        return anon.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .cookie("XSRF-TOKEN", xsrf).header("X-XSRF-TOKEN", xsrf)
                .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
                .exchange();
    }

    // --- authentication ---------------------------------------------------------------------

    @Test
    void apiRequiresAuthentication() {
        TestSessions.anonymous(port).get().uri("/api/v1/monitors").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.detail").isEqualTo("Authentication is required")
                .jsonPath("$.requestId").isNotEmpty();
    }

    @Test
    void loginSetsHardenedSessionCookieAndReturnsUser() {
        User user = sessions.user(unique("login"));

        HttpHeaders headers = login(user.getEmail(), TestSessions.PASSWORD)
                .expectStatus().isOk()
                .expectBody().jsonPath("$.email").isEqualTo(user.getEmail())
                .jsonPath("$.role").isEqualTo("USER")
                .returnResult().getResponseHeaders();

        String sessionCookie = headers.getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("SESSION=")).findFirst().orElseThrow();
        assertThat(sessionCookie).contains("HttpOnly").contains("SameSite=Lax");
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameAnswer() {
        User user = sessions.user(unique("wrong"));

        login(user.getEmail(), "not-the-password-at-all").expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.detail").isEqualTo("Invalid e-mail or password");
        login(unique("nobody"), "not-the-password-at-all").expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.detail").isEqualTo("Invalid e-mail or password");
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() {
        TestSessions.anonymous(port).post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"a@example.com\",\"password\":\"whatever-password\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void stateChangingRequestWithoutCsrfHeaderIsRejected() {
        User user = sessions.user(unique("csrf"));
        TestSessions.Session session = TestSessions.loginSession(port, user.getEmail(), TestSessions.PASSWORD);

        // What a cross-site attacker can do: the browser attaches the session cookie, but the
        // attacker cannot read the XSRF-TOKEN cookie to set the matching header.
        TestSessions.anonymous(port).post().uri("/api/v1/monitors")
                .cookie("SESSION", session.sessionId())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"csrf\",\"url\":\"https://example.com\"}")
                .exchange()
                .expectStatus().isForbidden();
        // A mismatched header is rejected too.
        TestSessions.anonymous(port).post().uri("/api/v1/monitors")
                .cookie("SESSION", session.sessionId())
                .cookie("XSRF-TOKEN", session.xsrfToken())
                .header("X-XSRF-TOKEN", "forged-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"csrf\",\"url\":\"https://example.com\"}")
                .exchange()
                .expectStatus().isForbidden();
        // Reads are not CSRF-protected (they change nothing).
        TestSessions.anonymous(port).get().uri("/api/v1/monitors")
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void bruteForceIsLockedOutEvenWithTheRightPassword() {
        User user = sessions.user(unique("brute"));
        for (int i = 0; i < 5; i++) {
            login(user.getEmail(), "wrong-password-" + i).expectStatus().isUnauthorized();
        }

        login(user.getEmail(), TestSessions.PASSWORD)
                .expectStatus().isEqualTo(429)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER)
                .expectBody().jsonPath("$.detail").isEqualTo("Too many failed login attempts. Try again later.");
    }

    @Test
    void logoutEndsTheSession() {
        RestTestClient client = sessions.login(port, sessions.user(unique("logout")));
        client.get().uri("/api/v1/auth/me").exchange().expectStatus().isOk();

        client.post().uri("/api/v1/auth/logout").exchange().expectStatus().isNoContent();

        client.get().uri("/api/v1/auth/me").exchange().expectStatus().isUnauthorized();
    }

    // --- isolation between users --------------------------------------------------------------

    @Test
    void usersCannotSeeOrTouchEachOthersMonitors() {
        User alice = sessions.user(unique("alice"));
        User bob = sessions.user(unique("bob"));
        Monitor alicesMonitor = new Monitor("alice-only", MonitorType.HTTP, "https://example.com", 60, 5000);
        alicesMonitor.setOwnerId(alice.getId());
        UUID id = monitors.save(alicesMonitor).getId();
        RestTestClient bobClient = sessions.login(port, bob);

        bobClient.get().uri("/api/v1/monitors").exchange()
                .expectBody().jsonPath("$.items[?(@.name == 'alice-only')]").doesNotExist();
        bobClient.get().uri("/api/v1/monitors/{id}", id).exchange().expectStatus().isNotFound();
        bobClient.get().uri("/api/v1/monitors/{id}/stats", id).exchange().expectStatus().isNotFound();
        bobClient.delete().uri("/api/v1/monitors/{id}", id).exchange().expectStatus().isNotFound();
        bobClient.put().uri("/api/v1/monitors/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"hijack\",\"url\":\"https://example.com\"}")
                .exchange().expectStatus().isNotFound();

        sessions.login(port, alice).get().uri("/api/v1/monitors/{id}", id).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.name").isEqualTo("alice-only");
    }

    // --- user administration -------------------------------------------------------------------

    @Test
    void onlyAdminsManageUsers() {
        sessions.login(port, sessions.user(unique("plain"))).get().uri("/api/v1/users").exchange()
                .expectStatus().isForbidden();

        RestTestClient admin = sessions.login(port, sessions.user(unique("admin"), User.Role.ADMIN));
        String body = admin.get().uri("/api/v1/users").exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseBody();
        assertThat(body).doesNotContain("bcrypt").doesNotContain("password");
    }

    @Test
    void adminCreatesUsersWithPasswordPolicy() {
        RestTestClient admin = sessions.login(port, sessions.user(unique("admin"), User.Role.ADMIN));
        String email = unique("new");

        admin.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"%s\",\"password\":\"short\",\"role\":\"USER\"}".formatted(email))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("password");

        admin.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"%s\",\"password\":\"a-long-enough-passphrase\",\"role\":\"USER\"}".formatted(email))
                .exchange().expectStatus().isCreated();
        admin.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"%s\",\"password\":\"a-long-enough-passphrase\",\"role\":\"USER\"}".formatted(email.toUpperCase()))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].message").isEqualTo("A user with this e-mail already exists");

        login(email, "a-long-enough-passphrase").expectStatus().isOk();
    }

    @Test
    void deletingAUserEndsTheirSessionsImmediately() {
        User victim = sessions.user(unique("victim"));
        RestTestClient victimClient = sessions.login(port, victim);
        victimClient.get().uri("/api/v1/auth/me").exchange().expectStatus().isOk();
        RestTestClient admin = sessions.login(port, sessions.user(unique("admin"), User.Role.ADMIN));

        admin.delete().uri("/api/v1/users/{id}", victim.getId()).exchange().expectStatus().isNoContent();

        victimClient.get().uri("/api/v1/auth/me").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void adminCannotDeleteThemselves() {
        User admin = sessions.user(unique("self"), User.Role.ADMIN);
        sessions.login(port, admin).delete().uri("/api/v1/users/{id}", admin.getId()).exchange()
                .expectStatus().isBadRequest();
    }

    // --- password change ----------------------------------------------------------------------

    @Test
    void passwordChangeRequiresCurrentPasswordAndPolicy() {
        User user = sessions.user(unique("pw"));
        RestTestClient client = sessions.login(port, user);

        client.post().uri("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
                .body("{\"currentPassword\":\"wrong-password-xyz\",\"newPassword\":\"brand-new-passphrase\"}")
                .exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
                .body("{\"currentPassword\":\"%s\",\"newPassword\":\"tiny\"}".formatted(TestSessions.PASSWORD))
                .exchange().expectStatus().isBadRequest();
        client.post().uri("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
                .body("{\"currentPassword\":\"%s\",\"newPassword\":\"brand-new-passphrase\"}".formatted(TestSessions.PASSWORD))
                .exchange().expectStatus().isNoContent();

        login(user.getEmail(), "brand-new-passphrase").expectStatus().isOk();
    }

    // --- bootstrap ------------------------------------------------------------------------------

    @Test
    void bootstrapCreatesAdminAndAdoptsOrphanedMonitorsOnlyWhenNoUsersExist() {
        tx.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM monitors");
            jdbc.update("DELETE FROM users");
        });
        UUID orphan = monitors.save(new Monitor("pre-auth", MonitorType.HTTP, "https://example.com", 60, 5000)).getId();

        new AdminBootstrap(users, passwordEncoder, jdbc, tx, "Root@Example.com", "bootstrap-passphrase-1")
                .run(new DefaultApplicationArguments());

        User admin = users.findByEmail("root@example.com").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(monitors.findById(orphan).orElseThrow().getOwnerId()).isEqualTo(admin.getId());

        new AdminBootstrap(users, passwordEncoder, jdbc, tx, "second@example.com", "another-passphrase-2")
                .run(new DefaultApplicationArguments());
        assertThat(users.findAll()).extracting(User::getEmail).containsExactly("root@example.com");
    }
}
