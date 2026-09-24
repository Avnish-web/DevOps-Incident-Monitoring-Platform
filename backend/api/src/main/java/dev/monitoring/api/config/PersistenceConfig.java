package dev.monitoring.api.config;

import dev.monitoring.common.CommonPersistenceConfiguration;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Enables the shared JPA entities and repositories. Kept off the main application class so
 * sliced tests (e.g. {@code @WebMvcTest}) do not try to start JPA.
 */
@Configuration(proxyBeanMethods = false)
@Import(CommonPersistenceConfiguration.class)
public class PersistenceConfig {

    /**
     * Fails startup with a clear message when no database password is configured, before any
     * connection attempt. Without this, an unresolved {@code ${POSTGRES_PASSWORD}} placeholder
     * would be sent to the server as a literal password.
     */
    @Bean
    static BeanFactoryPostProcessor requireDatabasePassword(Environment environment) {
        return beanFactory -> {
            String password;
            try {
                password = environment.getProperty("spring.datasource.password");
            } catch (IllegalArgumentException unresolvedPlaceholder) {
                password = null;
            }
            if (!StringUtils.hasText(password)) {
                throw new IllegalStateException("Database password is not configured. Set the "
                        + "POSTGRES_PASSWORD environment variable (or add it to .env and run "
                        + "with the 'local' profile).");
            }
        };
    }
}
