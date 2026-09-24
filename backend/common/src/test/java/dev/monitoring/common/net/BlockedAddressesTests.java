package dev.monitoring.common.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BlockedAddressesTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0", "127.0.0.1", "127.255.255.254", "10.1.2.3", "172.16.0.1", "172.31.255.255",
            "192.168.1.1", "169.254.169.254", "100.64.0.1", "100.127.255.255", "224.0.0.1",
            "255.255.255.255", "192.0.2.10", "198.18.0.1",
            "::", "::1", "fe80::1", "fc00::1", "fd12:3456::1", "ff02::1", "2001:db8::1",
            // IPv4 embedded in IPv6
            "::ffff:127.0.0.1", "::ffff:169.254.169.254", "::127.0.0.1", "64:ff9b::a00:1",
            "2002:7f00:0001::1", "2002:c0a8:0101::1"})
    void blocksNonPublicAddresses(String literal) throws Exception {
        assertThat(BlockedAddresses.isBlocked(InetAddress.getByName(literal)))
                .as(literal).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "8.8.8.8", "1.1.1.1", "93.184.215.14", "172.15.255.255", "172.32.0.1",
            "100.63.255.255", "100.128.0.1", "169.253.255.255", "2606:4700:4700::1111",
            "2a00:1450:4001:80b::200e", "64:ff9b::808:808", "2002:0808:0808::1"})
    void allowsPublicAddresses(String literal) throws Exception {
        assertThat(BlockedAddresses.isBlocked(InetAddress.getByName(literal)))
                .as(literal).isFalse();
    }
}
