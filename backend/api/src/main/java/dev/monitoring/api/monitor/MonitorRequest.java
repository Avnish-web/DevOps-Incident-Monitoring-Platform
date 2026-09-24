package dev.monitoring.api.monitor;

import dev.monitoring.common.domain.HttpCheckMethod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for creating (POST) or replacing (PUT) a monitor. Optional fields fall back to the
 * defaults below. URL safety (scheme, credentials, private addresses) is checked separately
 * by {@link dev.monitoring.common.net.TargetUrlValidator}.
 */
public record MonitorRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "must not contain control characters")
        String name,

        @NotBlank
        @Size(max = 2048)
        String url,

        HttpCheckMethod httpMethod,

        @Min(30) @Max(86400)
        Integer intervalSeconds,

        @Min(1000) @Max(30000)
        Integer timeoutMs,

        @Min(100) @Max(599)
        Integer expectedStatus,

        @Min(1) @Max(10)
        Integer failureThreshold,

        @Min(1) @Max(10)
        Integer recoveryThreshold,

        Boolean enabled) {

    static final HttpCheckMethod DEFAULT_METHOD = HttpCheckMethod.GET;
    static final int DEFAULT_INTERVAL_SECONDS = 60;
    static final int DEFAULT_TIMEOUT_MS = 5000;
    static final int DEFAULT_FAILURE_THRESHOLD = 3;
    static final int DEFAULT_RECOVERY_THRESHOLD = 2;

    HttpCheckMethod httpMethodOrDefault() {
        return httpMethod != null ? httpMethod : DEFAULT_METHOD;
    }

    int intervalSecondsOrDefault() {
        return intervalSeconds != null ? intervalSeconds : DEFAULT_INTERVAL_SECONDS;
    }

    int timeoutMsOrDefault() {
        return timeoutMs != null ? timeoutMs : DEFAULT_TIMEOUT_MS;
    }

    int failureThresholdOrDefault() {
        return failureThreshold != null ? failureThreshold : DEFAULT_FAILURE_THRESHOLD;
    }

    int recoveryThresholdOrDefault() {
        return recoveryThreshold != null ? recoveryThreshold : DEFAULT_RECOVERY_THRESHOLD;
    }

    boolean enabledOrDefault() {
        return enabled == null || enabled;
    }
}
