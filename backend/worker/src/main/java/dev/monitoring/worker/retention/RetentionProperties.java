package dev.monitoring.worker.retention;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * History retention, bound from {@code monitoring.retention.*}.
 *
 * @param enabled          run the cleanup job
 * @param checkResultsDays keep raw check results this many days
 * @param batchSize        rows deleted per statement (keeps locks and WAL bursts small)
 */
@Validated
@ConfigurationProperties(prefix = "monitoring.retention")
public record RetentionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("30") @Min(1) @Max(3650) int checkResultsDays,
        @DefaultValue("5000") @Min(100) @Max(100_000) int batchSize) {
}
