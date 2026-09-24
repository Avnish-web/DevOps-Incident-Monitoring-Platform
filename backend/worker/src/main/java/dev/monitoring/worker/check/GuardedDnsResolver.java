package dev.monitoring.worker.check;

import dev.monitoring.common.net.HostResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.function.Predicate;
import org.apache.hc.client5.http.DnsResolver;

/**
 * DNS resolver for the HTTP client that enforces the SSRF address policy at connection time.
 *
 * <p>The client connects only to addresses returned here. The check therefore covers exactly
 * what is dialed, on every connection, including every redirect hop and IP-literal hosts.
 * That closes the gap between save-time validation and a later DNS answer (DNS rebinding).
 * If any resolved address is blocked, the whole host is rejected, so a mixed answer cannot
 * be used to reach an internal address.
 */
public class GuardedDnsResolver implements DnsResolver {

    private final HostResolver resolver;
    private final Predicate<InetAddress> isBlocked;

    public GuardedDnsResolver(HostResolver resolver, Predicate<InetAddress> isBlocked) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.isBlocked = Objects.requireNonNull(isBlocked, "isBlocked");
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = resolver.resolve(host);
        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException(host);
        }
        for (InetAddress address : addresses) {
            if (isBlocked.test(address)) {
                throw new BlockedTargetException();
            }
        }
        return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) {
        // No reverse lookups: they leak information and are not needed for checks.
        return host;
    }
}
