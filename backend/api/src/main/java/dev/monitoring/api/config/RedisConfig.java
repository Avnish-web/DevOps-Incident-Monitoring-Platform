package dev.monitoring.api.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.boot.data.redis.autoconfigure.ClientResourcesBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RedisConfig {

    /**
     * Resolve the Redis host with the JDK resolver (short JVM DNS cache) instead of Netty's
     * DNS resolver, which caches for the record TTL. Docker's embedded DNS uses 600 s, so a
     * recreated Redis container (or a Kubernetes/ElastiCache failover to a new IP) would stay
     * unreachable for up to ten minutes.
     */
    @Bean
    ClientResourcesBuilderCustomizer jdkDnsResolver() {
        return builder -> builder.addressResolverGroup(DefaultAddressResolverGroup.INSTANCE);
    }
}
