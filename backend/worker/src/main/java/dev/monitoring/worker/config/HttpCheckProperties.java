package dev.monitoring.worker.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * HTTP check behaviour, bound from {@code monitoring.worker.http.*}.
 *
 * @param maxRedirects redirects followed per check (0 disables following)
 * @param maxBodyBytes body bytes read per check; the rest is discarded by closing the connection
 * @param userAgent    User-Agent header sent with every check
 */
@Validated
@ConfigurationProperties(prefix = "monitoring.worker.http")
public record HttpCheckProperties(
        @DefaultValue("5") @Min(0) @Max(10) int maxRedirects,
        @DefaultValue("1MB") @NotNull DataSize maxBodyBytes,
        @DefaultValue("monitoring-platform-worker/0.1") @NotBlank String userAgent) {
}
