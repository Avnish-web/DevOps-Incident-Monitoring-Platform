package dev.monitoring.common.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;

/**
 * Validates URLs submitted as monitor targets.
 *
 * <p>This is the save-time check. It rejects malformed URLs, non-HTTP schemes, embedded
 * credentials and hosts that resolve to blocked addresses. A host that does not resolve
 * right now is accepted, because the target may simply be down. The worker must repeat the
 * address check at connection time and on every redirect, because DNS answers can change
 * after saving (DNS rebinding).
 */
public class TargetUrlValidator {

    public static final int MAX_LENGTH = 2048;

    static final String BLOCKED_MESSAGE =
            "URL points to a private, loopback or reserved address, which is not allowed";

    private final HostResolver resolver;
    private final boolean allowPrivateAddresses;

    /**
     * @param allowPrivateAddresses if true, skips the address check. Only for self-hosted
     *                              setups that intentionally monitor internal services.
     */
    public TargetUrlValidator(HostResolver resolver, boolean allowPrivateAddresses) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.allowPrivateAddresses = allowPrivateAddresses;
    }

    /** Returns the parsed URI, or throws {@link InvalidTargetUrlException} with the reason. */
    public URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidTargetUrlException("URL is required");
        }
        if (rawUrl.length() > MAX_LENGTH) {
            throw new InvalidTargetUrlException("URL must be at most " + MAX_LENGTH + " characters");
        }
        for (int i = 0; i < rawUrl.length(); i++) {
            char c = rawUrl.charAt(i);
            if (c <= 0x20 || c >= 0x7f) {
                throw new InvalidTargetUrlException("URL must not contain whitespace, control or "
                        + "non-ASCII characters (use punycode for international domain names)");
            }
        }

        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            throw new InvalidTargetUrlException("URL is not valid");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidTargetUrlException("Only http and https URLs are allowed");
        }
        if (uri.getRawUserInfo() != null) {
            throw new InvalidTargetUrlException("URL must not contain credentials");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new InvalidTargetUrlException("URL must contain a valid host name");
        }
        int port = uri.getPort();
        if (port != -1 && (port < 1 || port > 65535)) {
            throw new InvalidTargetUrlException("Port must be between 1 and 65535");
        }

        if (!allowPrivateAddresses) {
            checkHostIsPublic(normalizeHost(host));
        }
        return uri;
    }

    private void checkHostIsPublic(String host) {
        if (host.equals("localhost") || host.endsWith(".localhost")) {
            throw new InvalidTargetUrlException(BLOCKED_MESSAGE);
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            // Not resolvable right now; the worker re-checks at connection time.
            return;
        }
        for (InetAddress address : addresses) {
            if (BlockedAddresses.isBlocked(address)) {
                throw new InvalidTargetUrlException(BLOCKED_MESSAGE);
            }
        }
    }

    private static String normalizeHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);  // IPv6 literal
        }
        if (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);  // fully-qualified form "example.com."
        }
        return h;
    }
}
