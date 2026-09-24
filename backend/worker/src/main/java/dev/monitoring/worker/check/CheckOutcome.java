package dev.monitoring.worker.check;

import dev.monitoring.common.domain.CheckErrorType;
import java.time.Instant;
import java.util.Objects;

/** Result of one check, before it is persisted. */
public record CheckOutcome(
        Instant checkedAt,
        boolean success,
        Integer statusCode,
        Integer latencyMs,
        CheckErrorType errorType,
        String errorMessage) {

    public static CheckOutcome success(Instant checkedAt, int statusCode, int latencyMs) {
        return new CheckOutcome(checkedAt, true, statusCode, latencyMs, null, null);
    }

    public static CheckOutcome failure(Instant checkedAt, Integer statusCode, Integer latencyMs,
                                       CheckErrorType errorType, String errorMessage) {
        return new CheckOutcome(checkedAt, false, statusCode, latencyMs,
                Objects.requireNonNull(errorType, "errorType"), errorMessage);
    }
}
