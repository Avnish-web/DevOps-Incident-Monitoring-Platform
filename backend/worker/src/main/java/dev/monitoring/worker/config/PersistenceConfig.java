package dev.monitoring.worker.config;

import dev.monitoring.common.CommonPersistenceConfiguration;
import dev.monitoring.common.DatabasePasswordCheck;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/** Enables the shared JPA entities and repositories. The worker never migrates the schema. */
@Configuration(proxyBeanMethods = false)
@Import(CommonPersistenceConfiguration.class)
public class PersistenceConfig {

    @Bean
    static BeanFactoryPostProcessor requireDatabasePassword(Environment environment) {
        return DatabasePasswordCheck.requireDatabasePassword(environment);
    }
}
