package dev.monitoring.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Pins "now" for time-window queries (statistics) so assertions are exact. */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfig {

    public static final Instant NOW = Instant.parse("2030-06-01T12:00:00Z");

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
