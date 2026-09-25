package dev.monitoring.worker.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.common.domain.CheckErrorType;
import dev.monitoring.common.domain.HttpCheckMethod;
import dev.monitoring.common.net.BlockedAddresses;
import dev.monitoring.common.net.HostResolver;
import dev.monitoring.worker.config.HttpCheckProperties;
import dev.monitoring.worker.scheduling.ClaimedMonitor;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Exercises real HTTP against a local server. DNS is faked: {@code target.test} points at the
 * local server, and the address policy allows exactly 127.0.0.1, so everything else internal
 * is blocked just as in production.
 */
class HttpCheckerTests {

    private static final InetAddress LOCAL_SERVER = InetAddress.getLoopbackAddress();

    private static final Map<String, String> DNS = Map.of(
            "target.test", "127.0.0.1",
            "internal.test", "10.0.0.1",
            "rebind.test", "127.0.0.2");

    private static final HostResolver FAKE_DNS = host -> {
        String ip = DNS.get(host);
        if (ip != null) {
            return new InetAddress[] {InetAddress.getByName(ip)};
        }
        if (Character.isDigit(host.charAt(0)) || host.contains(":")) {
            return InetAddress.getAllByName(host); // IP literal, no lookup
        }
        throw new UnknownHostException(host);
    };

    private static final Predicate<InetAddress> POLICY =
            address -> !address.equals(LOCAL_SERVER) && BlockedAddresses.isBlocked(address);

    static TestHttpServer server;
    static HttpChecker checker;

    @BeforeAll
    static void start() throws Exception {
        server = new TestHttpServer();
        HttpCheckProperties props = new HttpCheckProperties(5, DataSize.ofKilobytes(256),
                "monitoring-platform-worker/test");
        checker = new HttpChecker(props, new GuardedDnsResolver(FAKE_DNS, POLICY), 10);
    }

    @AfterAll
    static void stop() throws Exception {
        checker.close();
        server.close();
    }

    private String url(String path) {
        return "http://target.test:" + server.port() + path;
    }

    private static ClaimedMonitor monitor(String url) {
        return monitor(url, HttpCheckMethod.GET, 2000, null);
    }

    private static ClaimedMonitor monitor(String url, HttpCheckMethod method, int timeoutMs,
                                          Integer expectedStatus) {
        return new ClaimedMonitor(UUID.randomUUID(), url, method, timeoutMs, expectedStatus, 60);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // --- success paths --------------------------------------------------------------------

    @Test
    void successfulGetRecordsStatusAndLatency() {
        CheckOutcome outcome = checker.check(monitor(url("/ok")));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.statusCode()).isEqualTo(200);
        assertThat(outcome.latencyMs()).isBetween(0, 2000);
        assertThat(outcome.errorType()).isNull();
        assertThat(outcome.checkedAt()).isNotNull();
    }

    @Test
    void headMethodAndUserAgentAreSent() {
        server.received.clear();
        CheckOutcome outcome = checker.check(
                monitor(url("/ok"), HttpCheckMethod.HEAD, 2000, null));

        assertThat(outcome.success()).isTrue();
        assertThat(server.received).singleElement().satisfies(r -> {
            assertThat(r.method()).isEqualTo("HEAD");
            assertThat(r.userAgent()).isEqualTo("monitoring-platform-worker/test");
        });
    }

    @Test
    void followsRedirects() {
        CheckOutcome outcome = checker.check(monitor(url("/chain/3")));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.statusCode()).isEqualTo(200);
    }

    @Test
    void expectedStatusIsMatchedExactly() {
        assertThat(checker.check(monitor(url("/status/204"), HttpCheckMethod.GET, 2000, 204))
                .success()).isTrue();

        CheckOutcome mismatch = checker.check(monitor(url("/ok"), HttpCheckMethod.GET, 2000, 204));
        assertThat(mismatch.success()).isFalse();
        assertThat(mismatch.errorType()).isEqualTo(CheckErrorType.UNEXPECTED_STATUS);
        assertThat(mismatch.errorMessage()).isEqualTo("HTTP 200 (expected 204)");
    }

    @Test
    void largeBodyIsCappedAndStillSucceeds() {
        CheckOutcome outcome = checker.check(monitor(url("/big")));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.latencyMs()).isLessThan(2000);
    }

    // --- HTTP-level failures --------------------------------------------------------------

    @Test
    void serverErrorIsUnexpectedStatus() {
        CheckOutcome outcome = checker.check(monitor(url("/status/503")));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.statusCode()).isEqualTo(503);
        assertThat(outcome.errorType()).isEqualTo(CheckErrorType.UNEXPECTED_STATUS);
        assertThat(outcome.errorMessage()).isEqualTo("HTTP 503 (expected 2xx or 3xx)");
        assertThat(outcome.latencyMs()).isNotNull();
    }

    @Test
    void notFoundIsUnexpectedStatus() {
        CheckOutcome outcome = checker.check(monitor(url("/missing")));

        assertThat(outcome.errorType()).isEqualTo(CheckErrorType.UNEXPECTED_STATUS);
        assertThat(outcome.statusCode()).isEqualTo(404);
    }

    @Test
    void tooManyRedirectsFails() {
        CheckOutcome outcome = checker.check(monitor(url("/chain/10")));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorType()).isEqualTo(CheckErrorType.OTHER);
        assertThat(outcome.errorMessage()).isEqualTo("Too many redirects");
    }

    @Test
    void redirectLoopFails() {
        CheckOutcome outcome = checker.check(monitor(url("/loop")));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorMessage()).isIn("Circular redirect", "Too many redirects");
    }

    // --- SSRF at connection time ----------------------------------------------------------

    @Test
    void redirectToCloudMetadataIsBlocked() {
        CheckOutcome outcome = checker.check(
                monitor(url("/redirect?to=" + encode("http://169.254.169.254/latest/meta-data/"))));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorType()).isEqualTo(CheckErrorType.BLOCKED_TARGET);
    }

    @Test
    void redirectToHostResolvingToPrivateAddressIsBlocked() {
        CheckOutcome outcome = checker.check(
                monitor(url("/redirect?to=" + encode("http://internal.test/admin"))));

        assertThat(outcome.errorType()).isEqualTo(CheckErrorType.BLOCKED_TARGET);
    }

    @Test
    void hostThatNowResolvesToLoopbackIsBlockedBeforeConnecting() {
        // Simulates DNS rebinding: the name was public at save time, loopback now.
        server.received.clear();
        CheckOutcome outcome = checker.check(monitor("http://rebind.test:" + server.port() + "/ok"));

        assertThat(outcome.errorType()).isEqualTo(CheckErrorType.BLOCKED_TARGET);
        assertThat(server.received).isEmpty();
    }

    @Test
    void ipLiteralTargetIsBlocked() {
        CheckOutcome outcome = checker.check(monitor("http://127.0.0.2:" + server.port() + "/ok"));

        assertThat(outcome.errorType()).isEqualTo(CheckErrorType.BLOCKED_TARGET);
    }

    // --- transport failures ---------------------------------------------------------------

    @Test
    void unresolvableHostIsDnsFailure() {
        CheckOutcome outcome = checker.check(monitor("http://nxdomain.test/"));

        assertThat(outcome.errorType()).isEqualTo(CheckErrorType.DNS_FAILURE);
        assertThat(outcome.errorMessage()).isEqualTo("Could not resolve host");
    }

    @Test
    void closedPortIsConnectionRefused() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, LOCAL_SERVER)) {
            closedPort = socket.getLocalPort();
        }
        CheckOutcome outcome = checker.check(monitor("http://target.test:" + closedPort + "/"));

        assertThat(outcome.errorType()).isEqualTo(CheckErrorType.CONNECTION_REFUSED);
    }

    @Test
    void slowHeadersTimeOut() {
        CheckOutcome outcome = checker.check(
                monitor(url("/slow-headers"), HttpCheckMethod.GET, 1000, null));

        assertThat(outcome.errorType()).isEqualTo(CheckErrorType.TIMEOUT);
        assertThat(outcome.latencyMs()).isBetween(900, 2500);
    }

    @Test
    void slowDripBodyIsCutOffAtTheDeadline() {
        // Each byte arrives well within the socket timeout; only the hard deadline stops it.
        CheckOutcome outcome = checker.check(monitor(url("/drip"), HttpCheckMethod.GET, 1000, null));

        assertThat(outcome.errorType()).isEqualTo(CheckErrorType.TIMEOUT);
        assertThat(outcome.latencyMs()).isBetween(900, 2500);
    }

    @Test
    void tlsAgainstPlainHttpServerIsTlsError() throws Exception {
        // A server that answers the TLS ClientHello with plaintext HTTP (a common misconfiguration).
        try (ServerSocket plain = new ServerSocket(0, 1, LOCAL_SERVER)) {
            Thread responder = new Thread(() -> {
                try (var socket = plain.accept()) {
                    socket.getOutputStream().write(
                            "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n".getBytes());
                    socket.getOutputStream().flush();
                    Thread.sleep(500);
                } catch (Exception ignored) {
                    // client closed the connection
                }
            });
            responder.start();

            // Generous deadline: this may be the JVM's first TLS handshake (SSL context
            // initialization), which is slow on a busy CI machine.
            CheckOutcome outcome = checker.check(monitor(
                    "https://target.test:" + plain.getLocalPort() + "/ok", HttpCheckMethod.GET, 5000, null));

            assertThat(outcome.errorType()).isEqualTo(CheckErrorType.TLS_ERROR);
            assertThat(outcome.errorMessage()).startsWith("TLS error");
            responder.join(2000);
        }
    }

    @Test
    void silentServerDuringTlsHandshakeTimesOut() {
        // The JDK test server never answers a ClientHello: the check must still end on time.
        CheckOutcome outcome = checker.check(
                monitor("https://target.test:" + server.port() + "/ok", HttpCheckMethod.GET,
                        1000, null));

        assertThat(outcome.errorType()).isEqualTo(CheckErrorType.TIMEOUT);
        assertThat(outcome.latencyMs()).isBetween(900, 2500);
    }

    @Test
    void invalidStoredUrlFailsWithoutRequest() {
        CheckOutcome outcome = checker.check(monitor("ftp://target.test/file"));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorType()).isEqualTo(CheckErrorType.OTHER);
        assertThat(outcome.errorMessage()).startsWith("Invalid URL");
    }
}
