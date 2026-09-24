package dev.monitoring.api.alert;

import dev.monitoring.common.domain.AlertDelivery;
import java.time.Instant;
import java.util.UUID;

public record AlertDeliveryResponse(
        long id,
        UUID incidentId,
        String eventType,
        String status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant createdAt,
        Instant sentAt) {

    static AlertDeliveryResponse from(AlertDelivery d) {
        return new AlertDeliveryResponse(d.getId(), d.getIncidentId(), d.getEventType(),
                d.getStatus(), d.getAttempts(), d.getNextAttemptAt(), d.getLastError(),
                d.getCreatedAt(), d.getSentAt());
    }
}
