package dev.monitoring.api.alert;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.monitoring.common.domain.AlertChannelType;
import java.time.Instant;
import java.util.UUID;

/**
 * An alert channel as seen by clients. The full target is never returned, only a masked
 * preview. {@code signingSecret} is present exactly once: in the response that creates a
 * webhook channel.
 */
public record AlertChannelResponse(
        UUID id,
        String name,
        AlertChannelType type,
        String targetPreview,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        long version,
        @JsonInclude(JsonInclude.Include.NON_NULL) String signingSecret) {
}
