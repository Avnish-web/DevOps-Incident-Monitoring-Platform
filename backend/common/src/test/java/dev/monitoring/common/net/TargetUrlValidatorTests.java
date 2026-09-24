package dev.monitoring.common.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TargetUrlValidatorTests {

    /** Fake DNS: known names map to fixed addresses, IP literals parse locally, rest is NXDOMAIN. */
    private static final Map<String, String> DNS = Map.of(
            "example.com", "93.184.215.14",
            "internal.example", "10.0.0.5",
            "rebind.example", "127.0.0.1",
            "metadata.example", "169.254.169.254");

    private static final HostResolver FAKE_DNS = host -> {
        String ip = DNS.get(host);
        if (ip != null) {
            return new InetAddress[] {InetAddress.getByName(ip)};
        }
        if (Character.isDigit(host.charAt(0)) || host.contains(":")) {
            return InetAddress.getAllByName(host); // IP literal: no network lookup
        }
        throw new UnknownHostException(host);
    };

    private final TargetUrlValidator validator = new TargetUrlValidator(FAKE_DNS, false);

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com", "http://example.com/health?full=1", "HTTPS://EXAMPLE.COM/",
            "https://example.com:8443/status", "https://example.com./", "http://93.184.215.14/",
            "https://does-not-resolve.example/"})
    void acceptsPublicHttpUrls(String url) {
        assertThat(validator.validate(url)).isNotNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ftp://example.com/                 | Only http and https URLs are allowed",
            "file:///etc/passwd                 | Only http and https URLs are allowed",
            "javascript:alert(1)                | Only http and https URLs are allowed",
            "example.com                        | Only http and https URLs are allowed",
            "https://user:pass@example.com/     | URL must not contain credentials",
            "https://example.com:99999/         | Port must be between 1 and 65535",
            "https:///path                      | URL must contain a valid host name",
            "https://exa mple.com               | URL must not contain whitespace"})
    void rejectsMalformedUrls(String url, String expectedMessage) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidTargetUrlException.class)
                .hasMessageContaining(expectedMessage);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8080/", "http://LOCALHOST/", "http://api.localhost/",
            "http://127.0.0.1/", "http://2130706433/", "http://0.0.0.0/",
            "http://[::1]/", "http://[::ffff:127.0.0.1]/", "http://169.254.169.254/latest/meta-data/",
            "http://10.0.0.1/", "http://192.168.0.10/", "http://internal.example/",
            "http://rebind.example/", "http://metadata.example/"})
    void rejectsPrivateAndReservedTargets(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidTargetUrlException.class)
                .hasMessage(TargetUrlValidator.BLOCKED_MESSAGE);
    }

    /** Shorthand IPv4 forms are not valid URI hosts, so they are rejected before any lookup. */
    @ParameterizedTest
    @ValueSource(strings = {"http://127.1/", "http://10.1/", "http://0x7f.0.0.1/"})
    void rejectsShorthandIpForms(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidTargetUrlException.class);
    }

    @Test
    void errorMessageNeverEchoesInput() {
        String hostile = "ftp://${jndi:ldap://x}/{evil}";
        assertThatThrownBy(() -> validator.validate(hostile))
                .isInstanceOf(InvalidTargetUrlException.class)
                .message().doesNotContain("jndi").doesNotContain("evil");
    }

    @Test
    void rejectsOverlongUrl() {
        String url = "https://example.com/" + "a".repeat(TargetUrlValidator.MAX_LENGTH);
        assertThatThrownBy(() -> validator.validate(url))
                .hasMessageContaining("at most 2048");
    }

    @Test
    void allowPrivateModeAcceptsInternalTargets() {
        TargetUrlValidator permissive = new TargetUrlValidator(FAKE_DNS, true);
        assertThat(permissive.validate("http://10.0.0.1:9100/metrics")).isNotNull();
        assertThat(permissive.validate("http://localhost:8080/")).isNotNull();
    }
}
