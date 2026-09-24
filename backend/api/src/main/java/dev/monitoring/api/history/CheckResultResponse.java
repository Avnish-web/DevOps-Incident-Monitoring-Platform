package dev.monitoring.api.history;

import dev.monitoring.common.domain.CheckErrorType;
import dev.monitoring.common.domain.CheckResult;
import java.time.Instant;

public record CheckResultResponse(
        Instant checkedAt,
        boolean success,
        Integer statusCode,
        Integer latencyMs,
        CheckErrorType errorType,
        String errorMessage) {

    static CheckResultResponse from(CheckResult r) {
        return new CheckResultResponse(r.getCheckedAt(), r.isSuccess(), r.getStatusCode(),
                r.getLatencyMs(), r.getErrorType(), r.getErrorMessage());
    }
}
