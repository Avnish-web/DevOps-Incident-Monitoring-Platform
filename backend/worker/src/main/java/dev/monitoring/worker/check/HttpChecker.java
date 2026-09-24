package dev.monitoring.worker.check;

import dev.monitoring.common.domain.CheckErrorType;
import dev.monitoring.common.domain.HttpCheckMethod;
import dev.monitoring.common.net.HostResolver;
import dev.monitoring.common.net.InvalidTargetUrlException;
import dev.monitoring.common.net.TargetUrlValidator;
import dev.monitoring.worker.config.HttpCheckProperties;
import dev.monitoring.worker.scheduling.ClaimedMonitor;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLException;
import org.apache.hc.client5.http.CircularRedirectException;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/**
 * Performs a single HTTP(S) check and measures its response time.
 *
 * <p>Safety and reliability rules:
 * <ul>
 *   <li>Connections go only to addresses approved by the {@link DnsResolver} passed in
 *       (SSRF policy, re-applied on every redirect hop).</li>
 *   <li>A hard deadline equal to the monitor's timeout aborts the request, whatever phase it
 *       is in (connect, headers or a slow-drip body).</li>
 *   <li>At most {@code maxBodyBytes} of the body are read. No retries, cookies, auth, proxies
 *       or connection reuse, so every check measures a fresh DNS + TCP + TLS + request.</li>
 * </ul>
 * Latency is measured from just before the request to the end of reading the body.
 */
public class HttpChecker implements Closeable {

    /** Upper bound for the pool-level connect timeout; per-request values are lower. */
    private static final Timeout MAX_TIMEOUT = Timeout.ofSeconds(30);

    private final HttpCheckProperties properties;
    private final CloseableHttpClient client;
    private final ScheduledExecutorService deadlines;
    /** Syntax/scheme re-check only. Addresses are enforced by the DNS resolver at connect time. */
    private final TargetUrlValidator urlSyntax = new TargetUrlValidator(HostResolver.SYSTEM, true);

    public HttpChecker(HttpCheckProperties properties, DnsResolver dnsResolver, int maxConnections) {
        this.properties = properties;
        PoolingHttpClientConnectionManager connections =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dnsResolver)
                        .setMaxConnTotal(maxConnections)
                        .setMaxConnPerRoute(maxConnections)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(MAX_TIMEOUT)
                                .setSocketTimeout(MAX_TIMEOUT)
                                .build())
                        .build();
        this.client = HttpClients.custom()
                .setConnectionManager(connections)
                .setConnectionReuseStrategy((request, response, context) -> false)
                .setUserAgent(properties.userAgent())
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableAuthCaching()
                .disableContentCompression()
                .build();
        this.deadlines = Executors.newSingleThreadScheduledExecutor(daemonThreads());
    }

    public CheckOutcome check(ClaimedMonitor monitor) {
        Instant checkedAt = Instant.now();
        URI uri;
        try {
            uri = urlSyntax.validate(monitor.url());
        } catch (InvalidTargetUrlException e) {
            return CheckOutcome.failure(checkedAt, null, null, CheckErrorType.OTHER,
                    "Invalid URL: " + e.getMessage());
        }

        HttpUriRequestBase request = monitor.httpMethod() == HttpCheckMethod.HEAD
                ? new HttpHead(uri) : new HttpGet(uri);
        request.setConfig(requestConfig(monitor.timeoutMs()));

        AtomicBoolean deadlineReached = new AtomicBoolean();
        long start = System.nanoTime();
        ScheduledFuture<?> deadline = deadlines.schedule(() -> {
            deadlineReached.set(true);
            request.cancel();
        }, monitor.timeoutMs(), TimeUnit.MILLISECONDS);
        try {
            return client.execute(request, response -> {
                int status = response.getCode();
                drain(response.getEntity());
                int latency = elapsedMs(start);
                if (isExpected(status, monitor.expectedStatus())) {
                    return CheckOutcome.success(checkedAt, status, latency);
                }
                return CheckOutcome.failure(checkedAt, status, latency,
                        CheckErrorType.UNEXPECTED_STATUS,
                        unexpectedStatusMessage(status, monitor.expectedStatus()));
            });
        } catch (IOException | RuntimeException e) {
            int latency = elapsedMs(start);
            if (deadlineReached.get()) {
                return CheckOutcome.failure(checkedAt, null, latency, CheckErrorType.TIMEOUT,
                        "No complete response within " + monitor.timeoutMs() + " ms");
            }
            return classify(e, checkedAt, latency);
        } finally {
            deadline.cancel(false);
        }
    }

    @SuppressWarnings("deprecation") // per-request connect timeout is still honoured in 5.x
    private RequestConfig requestConfig(int timeoutMs) {
        Timeout timeout = Timeout.ofMilliseconds(timeoutMs);
        return RequestConfig.custom()
                .setConnectionRequestTimeout(timeout)
                .setConnectTimeout(timeout)
                .setResponseTimeout(timeout)
                .setRedirectsEnabled(properties.maxRedirects() > 0)
                .setMaxRedirects(properties.maxRedirects())
                .setCircularRedirectsAllowed(false)
                .build();
    }

    /** Reads (and discards) at most maxBodyBytes so the full response time is measured. */
    private void drain(HttpEntity entity) throws IOException {
        if (entity == null) {
            return;
        }
        long remaining = properties.maxBodyBytes().toBytes();
        byte[] buffer = new byte[8192];
        try (InputStream in = entity.getContent()) {
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) {
                    break;
                }
                remaining -= read;
            }
        }
    }

    static boolean isExpected(int status, Integer expectedStatus) {
        return expectedStatus == null ? status >= 200 && status < 400 : status == expectedStatus;
    }

    private static String unexpectedStatusMessage(int status, Integer expectedStatus) {
        return expectedStatus == null
                ? "HTTP " + status + " (expected 2xx or 3xx)"
                : "HTTP " + status + " (expected " + expectedStatus + ")";
    }

    /** Maps a transport failure to an error type. The most specific cause wins. */
    static CheckOutcome classify(Throwable error, Instant checkedAt, int latencyMs) {
        CheckErrorType type = CheckErrorType.OTHER;
        String message = null;
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof BlockedTargetException) {
                type = CheckErrorType.BLOCKED_TARGET;
                message = t.getMessage();
                break;
            } else if (t instanceof UnknownHostException) {
                type = CheckErrorType.DNS_FAILURE;
                message = "Could not resolve host";
                break;
            } else if (t instanceof SSLException) {
                type = CheckErrorType.TLS_ERROR;
                message = "TLS error: " + t.getMessage();
                break;
            } else if (t instanceof ConnectException) {
                type = CheckErrorType.CONNECTION_REFUSED;
                message = "Connection failed: " + t.getMessage();
                break;
            } else if (t instanceof InterruptedIOException) {
                // Covers socket read timeouts and ConnectTimeoutException.
                type = CheckErrorType.TIMEOUT;
                message = "Timed out: " + t.getMessage();
                break;
            } else if (t instanceof CircularRedirectException) {
                message = "Circular redirect";
                break;
            } else if (t instanceof RedirectException) {
                message = "Too many redirects";
                break;
            }
        }
        if (message == null) {
            message = error.getClass().getSimpleName()
                    + (error.getMessage() != null ? ": " + error.getMessage() : "");
        }
        return CheckOutcome.failure(checkedAt, null, latencyMs, type, message);
    }

    private static int elapsedMs(long startNanos) {
        return (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static CustomizableThreadFactory daemonThreads() {
        CustomizableThreadFactory factory = new CustomizableThreadFactory("check-deadline-");
        factory.setDaemon(true);
        return factory;
    }

    @Override
    public void close() throws IOException {
        deadlines.shutdownNow();
        client.close();
    }
}
