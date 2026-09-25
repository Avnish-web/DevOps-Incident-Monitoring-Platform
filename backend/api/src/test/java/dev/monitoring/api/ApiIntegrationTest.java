package dev.monitoring.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Full application on random ports with real PostgreSQL and Redis, fake DNS and a fixed clock.
 * All classes using this annotation share one cached Spring context and one set of containers.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // The container supplies the real credentials; the password check only needs a value.
        properties = {"management.server.port=0", "POSTGRES_PASSWORD=provided-by-testcontainers",
                // Test-only key (32 zero bytes); real deployments generate their own.
                "ALERT_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@Import({PostgresTestcontainer.class, RedisTestcontainer.class, FakeDnsConfig.class,
        FixedClockConfig.class, TestSessions.class})
public @interface ApiIntegrationTest {
}
