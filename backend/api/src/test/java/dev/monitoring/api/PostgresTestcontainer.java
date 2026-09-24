package dev.monitoring.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real PostgreSQL for full-application tests; Flyway migrates it on startup. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }
}
