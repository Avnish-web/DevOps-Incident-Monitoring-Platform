package dev.monitoring.common;

import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.repository.MonitorRepository;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Registers the shared entities and repositories. Services import this explicitly, because
 * their own base packages ({@code dev.monitoring.api}, {@code dev.monitoring.worker}) do not
 * cover {@code dev.monitoring.common}.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = Monitor.class)
@EnableJpaRepositories(basePackageClasses = MonitorRepository.class)
public class CommonPersistenceConfiguration {
}
