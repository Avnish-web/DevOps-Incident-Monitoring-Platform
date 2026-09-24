package dev.monitoring.common.net;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * Decides whether an IP address is off-limits for outbound checks (SSRF protection).
 *
 * <p>Blocks loopback, private, link-local (including the {@code 169.254.169.254} cloud
 * metadata endpoint), carrier-grade NAT, multicast, documentation and reserved ranges, for
 * both IPv4 and IPv6. IPv6 forms that embed an IPv4 address (IPv4-mapped, IPv4-compatible,
 * NAT64, 6to4) are unwrapped and the embedded address is checked too.
 */
public final class BlockedAddresses {

    private static final List<Cidr> BLOCKED_RANGES = List.of(
            // IPv4
            Cidr.parse("0.0.0.0/8"),          // "this network"
            Cidr.parse("10.0.0.0/8"),         // private
            Cidr.parse("100.64.0.0/10"),      // carrier-grade NAT
            Cidr.parse("127.0.0.0/8"),        // loopback
            Cidr.parse("169.254.0.0/16"),     // link-local, cloud metadata
            Cidr.parse("172.16.0.0/12"),      // private
            Cidr.parse("192.0.0.0/24"),       // IETF protocol assignments
            Cidr.parse("192.0.2.0/24"),       // documentation
            Cidr.parse("192.88.99.0/24"),     // 6to4 relay anycast
            Cidr.parse("192.168.0.0/16"),     // private
            Cidr.parse("198.18.0.0/15"),      // benchmarking
            Cidr.parse("198.51.100.0/24"),    // documentation
            Cidr.parse("203.0.113.0/24"),     // documentation
            Cidr.parse("224.0.0.0/4"),        // multicast
            Cidr.parse("240.0.0.0/4"),        // reserved, broadcast
            // IPv6
            Cidr.parse("::/128"),             // unspecified
            Cidr.parse("::1/128"),            // loopback
            Cidr.parse("100::/64"),           // discard-only
            Cidr.parse("2001::/32"),          // Teredo
            Cidr.parse("2001:db8::/32"),      // documentation
            Cidr.parse("fc00::/7"),           // unique local
            Cidr.parse("fe80::/10"),          // link-local
            Cidr.parse("fec0::/10"),          // site-local (deprecated)
            Cidr.parse("ff00::/8"));          // multicast

    private BlockedAddresses() {
    }

    public static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        for (Cidr range : BLOCKED_RANGES) {
            if (range.contains(bytes)) {
                return true;
            }
        }
        if (address instanceof Inet6Address) {
            InetAddress embedded = embeddedIpv4(bytes);
            return embedded != null && isBlocked(embedded);
        }
        return false;
    }

    /** Returns the IPv4 address embedded in well-known IPv6 transition formats, or null. */
    static InetAddress embeddedIpv4(byte[] v6) {
        byte[] v4 = null;
        if (allZero(v6, 0, 10) && (v6[10] & 0xff) == 0xff && (v6[11] & 0xff) == 0xff) {
            v4 = Arrays.copyOfRange(v6, 12, 16);                    // ::ffff:a.b.c.d (mapped)
        } else if (allZero(v6, 0, 12)) {
            v4 = Arrays.copyOfRange(v6, 12, 16);                    // ::a.b.c.d (compatible)
        } else if (v6[0] == 0x00 && v6[1] == 0x64 && (v6[2] & 0xff) == 0xff
                && (v6[3] & 0xff) == 0x9b && allZero(v6, 4, 12)) {
            v4 = Arrays.copyOfRange(v6, 12, 16);                    // 64:ff9b::/96 (NAT64)
        } else if (v6[0] == 0x20 && v6[1] == 0x02) {
            v4 = Arrays.copyOfRange(v6, 2, 6);                      // 2002::/16 (6to4)
        }
        if (v4 == null) {
            return null;
        }
        try {
            return InetAddress.getByAddress(v4);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e); // cannot happen for a 4-byte array
        }
    }

    private static boolean allZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /** An address block in CIDR notation. */
    record Cidr(byte[] network, int prefixLength) {

        static Cidr parse(String cidr) {
            String[] parts = cidr.split("/");
            try {
                // Only IP literals are passed here, so no DNS lookup happens.
                byte[] network = InetAddress.getByName(parts[0]).getAddress();
                return new Cidr(network, Integer.parseInt(parts[1]));
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid CIDR: " + cidr, e);
            }
        }

        boolean contains(byte[] address) {
            if (address.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xff << (8 - remainingBits)) & 0xff;
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
