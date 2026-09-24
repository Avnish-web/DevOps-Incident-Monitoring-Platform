package dev.monitoring.api.alert;

import dev.monitoring.common.domain.AlertChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for creating (POST) or updating (PUT) an alert channel.
 *
 * @param target webhook URL, Slack incoming-webhook URL or e-mail address. Required on create;
 *               on update, omit it to keep the stored (encrypted) value.
 */
public record AlertChannelRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "must not contain control characters")
        String name,

        @NotNull
        AlertChannelType type,

        @Size(max = 2048)
        String target,

        Boolean enabled) {

    boolean enabledOrDefault() {
        return enabled == null || enabled;
    }
}
