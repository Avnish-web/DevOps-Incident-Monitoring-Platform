package dev.monitoring.api;

import dev.monitoring.common.domain.User;
import dev.monitoring.common.repository.UserRepository;
import java.util.List;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Creates users and logs them in through the real HTTP flow (CSRF cookie, JSON login,
 * rotated CSRF token), returning a client that carries the session and CSRF header.
 */
@TestComponent
public class TestSessions {

    public static final String PASSWORD = "correct-horse-battery-staple";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public TestSessions(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public User user(String email, User.Role role) {
        return users.findByEmail(User.normalizeEmail(email))
                .orElseGet(() -> users.saveAndFlush(new User(email, passwordEncoder.encode(PASSWORD), role)));
    }

    public User user(String email) {
        return user(email, User.Role.USER);
    }

    public static RestTestClient anonymous(int port) {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    public RestTestClient login(int port, User user) {
        return login(port, user.getEmail(), PASSWORD);
    }

    /** Raw cookie values of a logged-in session. */
    public record Session(String sessionId, String xsrfToken) {
    }

    public static RestTestClient login(int port, String email, String password) {
        Session s = loginSession(port, email, password);
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .defaultCookie("SESSION", s.sessionId())
                .defaultCookie("XSRF-TOKEN", s.xsrfToken())
                .defaultHeader("X-XSRF-TOKEN", s.xsrfToken())
                .build();
    }

    public static Session loginSession(int port, String email, String password) {
        RestTestClient anonymous = anonymous(port);
        String xsrf = cookie(anonymous.get().uri("/api/v1/auth/csrf").exchange()
                .expectStatus().isNoContent()
                .returnResult(Void.class).getResponseHeaders(), "XSRF-TOKEN");

        HttpHeaders loginHeaders = anonymous.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie("XSRF-TOKEN", xsrf)
                .header("X-XSRF-TOKEN", xsrf)
                .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Void.class).getResponseHeaders();
        String session = cookie(loginHeaders, "SESSION");

        // The CSRF token is rotated at login: fetch the new one with the session.
        String freshXsrf = cookie(anonymous.get().uri("/api/v1/auth/csrf")
                .cookie("SESSION", session).exchange()
                .expectStatus().isNoContent()
                .returnResult(Void.class).getResponseHeaders(), "XSRF-TOKEN");

        return new Session(session, freshXsrf);
    }

    /** Value of a Set-Cookie header with a non-empty value. */
    public static String cookie(HttpHeaders headers, String name) {
        List<String> setCookies = headers.getOrEmpty(HttpHeaders.SET_COOKIE);
        return setCookies.stream()
                .filter(c -> c.startsWith(name + "="))
                .map(c -> c.substring(name.length() + 1, c.indexOf(';') > 0 ? c.indexOf(';') : c.length()))
                .filter(v -> !v.isEmpty())
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No " + name + " cookie in " + setCookies));
    }
}
