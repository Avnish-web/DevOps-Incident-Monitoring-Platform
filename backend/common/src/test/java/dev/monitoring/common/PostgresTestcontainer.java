package dev.monitoring.common;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real PostgreSQL (same major version as production) instead of an in-memory database. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    public static final DockerImageName IMAGE = DockerImageName.parse("postgres:17-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(IMAGE);
    }
}
