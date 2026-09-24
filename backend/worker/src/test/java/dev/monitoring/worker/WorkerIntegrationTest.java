package dev.monitoring.worker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Full worker context with a real PostgreSQL. The auto-started scheduler is disabled so tests
 * control claiming themselves; Flyway is enabled here only because no API creates the schema.
 * Private targets are allowed so the Spring-wired checker can reach local test servers; the
 * SSRF policy itself is covered by HttpCheckerTests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "monitoring.worker.scheduler-enabled=false",
                "monitoring.targets.allow-private-addresses=true",
                "spring.flyway.enabled=true",
                "POSTGRES_PASSWORD=provided-by-testcontainers"})
@Import(PostgresTestcontainer.class)
public @interface WorkerIntegrationTest {
}
