package dev.monitoring.api;

import dev.monitoring.common.net.HostResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Deterministic DNS for tests: no dependency on the network or real DNS answers. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeDnsConfig {

    static final Map<String, String> RECORDS = Map.of(
            "example.com", "93.184.215.14",
            "status.example.org", "93.184.215.15",
            "intranet.example.com", "10.20.30.40");

    @Bean
    @Primary
    HostResolver fakeHostResolver() {
        return host -> {
            String ip = RECORDS.get(host);
            if (ip != null) {
                return new InetAddress[] {InetAddress.getByName(ip)};
            }
            if (Character.isDigit(host.charAt(0)) || host.contains(":")) {
                return InetAddress.getAllByName(host); // IP literal, no lookup
            }
            throw new UnknownHostException(host);
        };
    }
}
