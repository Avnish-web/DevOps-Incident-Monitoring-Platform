package dev.monitoring.api.security;

import dev.monitoring.common.domain.User;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated user as stored in the session. The password hash is erased after login
 * ({@link CredentialsContainer}), so it never ends up in Redis.
 */
public final class UserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final User.Role role;
    private final boolean enabled;
    private String passwordHash;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.enabled = user.isEnabled();
        this.passwordHash = user.getPasswordHash();
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public User.Role role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
