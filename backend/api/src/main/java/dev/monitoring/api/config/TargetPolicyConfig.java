package dev.monitoring.api.config;

import dev.monitoring.common.net.HostResolver;
import dev.monitoring.common.net.TargetUrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires SSRF protection for monitor URLs (see docs/architecture.md §8). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TargetPolicyConfig.TargetPolicyProperties.class)
public class TargetPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(TargetPolicyConfig.class);

    /**
     * @param allowPrivateAddresses allow monitors that point at loopback/private/link-local
     *                              addresses. Off by default; enable only for self-hosted
     *                              installations that intentionally monitor internal services.
     */
    @ConfigurationProperties(prefix = "monitoring.targets")
    public record TargetPolicyProperties(boolean allowPrivateAddresses) {
    }

    @Bean
    HostResolver hostResolver() {
        return HostResolver.SYSTEM;
    }

    @Bean
    TargetUrlValidator targetUrlValidator(HostResolver hostResolver,
                                          TargetPolicyProperties properties) {
        if (properties.allowPrivateAddresses()) {
            log.warn("monitoring.targets.allow-private-addresses=true: SSRF protection for "
                    + "private and loopback addresses is DISABLED");
        }
        return new TargetUrlValidator(hostResolver, properties.allowPrivateAddresses());
    }
}
