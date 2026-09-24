package dev.monitoring.common;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/** Minimal boot configuration for testing the library module in isolation. */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(CommonPersistenceConfiguration.class)
class TestPersistenceApplication {
}
