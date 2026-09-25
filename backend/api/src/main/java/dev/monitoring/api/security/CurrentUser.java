package dev.monitoring.api.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Access to the authenticated user for ownership checks in services. */
@Component
public class CurrentUser {

    public UserPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            // Unreachable for /api/** (the filter chain requires authentication): fail closed.
            throw new IllegalStateException("No authenticated user");
        }
        return principal;
    }

    public UUID id() {
        return get().id();
    }
}
