package dev.monitoring.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** Real Redis for session storage and login rate limiting. */
@TestConfiguration(proxyBeanMethods = false)
public class RedisTestcontainer {

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis:8.10.2-alpine")).withExposedPorts(6379);
    }
}
