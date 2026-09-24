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

    @Bean(destroyMethod = "close")
    HttpChecker httpChecker(HttpCheckProperties http, TargetPolicyProperties policy,
                            WorkerProperties worker, HostResolver hostResolver) {
        Predicate<InetAddress> isBlocked = BlockedAddresses::isBlocked;
        if (policy.allowPrivateAddresses()) {
            log.warn("monitoring.targets.allow-private-addresses=true: checks may connect to "
                    + "private and loopback addresses (SSRF protection DISABLED)");
            isBlocked = address -> false;
        }
        return new HttpChecker(http, new GuardedDnsResolver(hostResolver, isBlocked),
                worker.concurrency());
    }
}
