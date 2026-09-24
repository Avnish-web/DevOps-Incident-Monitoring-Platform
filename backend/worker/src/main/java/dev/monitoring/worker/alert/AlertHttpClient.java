package dev.monitoring.worker.alert;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

/**
 * Outbound HTTP for webhook and Slack alerts. Uses the SSRF-guarded DNS resolver and never
 * follows redirects, so a receiver cannot bounce the request to an internal address.
 */
public class AlertHttpClient implements Closeable {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(10);
    private static final int MAX_RESPONSE_BYTES = 16 * 1024;

    private final CloseableHttpClient client;
    private final String userAgent;

    public AlertHttpClient(DnsResolver dnsResolver, String userAgent) {
        this.userAgent = userAgent;
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dnsResolver)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(TIMEOUT).setSocketTimeout(TIMEOUT).build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setRedirectsEnabled(false)
                        .setResponseTimeout(TIMEOUT)
                        .setConnectionRequestTimeout(TIMEOUT)
                        .build())
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableAuthCaching()
                .build();
    }

    /** POSTs JSON; succeeds only on a 2xx response. */
    public void postJson(String url, String json, Map<String, String> headers)
            throws AlertDeliveryException {
        HttpPost post = new HttpPost(URI.create(url));
        post.setHeader("User-Agent", userAgent);
        headers.forEach(post::setHeader);
        post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)));
        try {
            int status = client.execute(post, response -> {
                if (response.getEntity() != null) {
                    try (InputStream in = response.getEntity().getContent()) {
                        in.readNBytes(MAX_RESPONSE_BYTES);
                    }
                }
                return response.getCode();
            });
            if (status < 200 || status >= 300) {
                throw new AlertDeliveryException("Receiver responded with HTTP " + status);
            }
        } catch (IOException e) {
            throw new AlertDeliveryException("Request failed: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()), e);
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
