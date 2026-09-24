package dev.monitoring.common;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Fails startup with a clear message when no database password is configured, before any
 * connection attempt. Without this, an unresolved {@code ${POSTGRES_PASSWORD}} placeholder
 * would be sent to the server as a literal password.
 *
 * <p>Register from a service's configuration as a {@code static @Bean} method.
 */
public final class DatabasePasswordCheck {

    private DatabasePasswordCheck() {
    }

    public static BeanFactoryPostProcessor requireDatabasePassword(Environment environment) {
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
