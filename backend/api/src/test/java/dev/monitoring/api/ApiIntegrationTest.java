package dev.monitoring.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Full application on random ports with a real PostgreSQL and fake DNS. All classes using
 * this annotation share one cached Spring context and one database container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // The container supplies the real credentials; the password check only needs a value.
        properties = {"management.server.port=0", "POSTGRES_PASSWORD=provided-by-testcontainers"})
@Import({PostgresTestcontainer.class, FakeDnsConfig.class})
public @interface ApiIntegrationTest {
}
