package dev.monitoring.worker.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Worker tuning, bound from {@code monitoring.worker.*}.
 *
 * @param schedulerEnabled start claiming checks on startup (false = idle worker, e.g. tests)
 * @param pollInterval     delay between claim attempts
 * @param batchSize        maximum monitors claimed per poll
 * @param concurrency      maximum checks running at the same time in this instance
 * @param shutdownGrace    how long shutdown waits for in-flight checks to finish
 */
@Validated
@ConfigurationProperties(prefix = "monitoring.worker")
public record WorkerProperties(
        @DefaultValue("true") boolean schedulerEnabled,
        @DefaultValue("1s") @NotNull Duration pollInterval,
        @DefaultValue("50") @Min(1) @Max(500) int batchSize,
        @DefaultValue("20") @Min(1) @Max(500) int concurrency,
        @DefaultValue("20s") @NotNull Duration shutdownGrace) {
}
