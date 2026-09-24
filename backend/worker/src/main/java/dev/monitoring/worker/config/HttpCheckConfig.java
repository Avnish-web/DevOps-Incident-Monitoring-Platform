package dev.monitoring.worker.config;

import dev.monitoring.common.net.BlockedAddresses;
import dev.monitoring.common.net.HostResolver;
import dev.monitoring.worker.check.GuardedDnsResolver;
import dev.monitoring.worker.check.HttpChecker;
import java.net.InetAddress;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class HttpCheckConfig {

    private static final Logger log = LoggerFactory.getLogger(HttpCheckConfig.class);

    @Bean
    HostResolver hostResolver() {
        return HostResolver.SYSTEM;
    }

    /**
     * SSRF-enforcing resolver shared by every outbound HTTP client in the worker (checks and
     * alert webhooks), so both follow exactly the same address policy.
     */
    @Bean
    GuardedDnsResolver guardedDnsResolver(TargetPolicyProperties policy, HostResolver hostResolver) {
        Predicate<InetAddress> isBlocked = BlockedAddresses::isBlocked;
        if (policy.allowPrivateAddresses()) {
            log.warn("monitoring.targets.allow-private-addresses=true: checks and webhooks may "
                    + "connect to private and loopback addresses (SSRF protection DISABLED)");
            isBlocked = address -> false;
        }
        return new GuardedDnsResolver(hostResolver, isBlocked);
    }

    @Bean(destroyMethod = "close")
    HttpChecker httpChecker(HttpCheckProperties http, WorkerProperties worker,
                            GuardedDnsResolver guardedDnsResolver) {
        return new HttpChecker(http, guardedDnsResolver, worker.concurrency());
    }
}
