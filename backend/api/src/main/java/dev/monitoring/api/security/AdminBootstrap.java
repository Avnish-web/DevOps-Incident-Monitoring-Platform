package dev.monitoring.api.security;

import dev.monitoring.common.domain.User;
import dev.monitoring.common.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the first administrator from {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} when no
 * users exist, and assigns resources created before authentication existed to that account.
 * Does nothing once any user exists, so the variables can (and should) be removed afterwards.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, JdbcTemplate jdbc,
                          TransactionTemplate tx,
                          @Value("${monitoring.auth.bootstrap-admin.email:}") String email,
                          @Value("${monitoring.auth.bootstrap-admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.tx = tx;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn("No users exist. Set ADMIN_EMAIL and ADMIN_PASSWORD to create the first "
                    + "administrator; until then nobody can log in.");
            return;
        }
        String violation = PasswordPolicy.violation(password, email);
        if (violation != null) {
            throw new IllegalStateException("ADMIN_PASSWORD rejected: " + violation);
        }
        try {
            tx.executeWithoutResult(status -> {
                User admin = users.saveAndFlush(new User(email, passwordEncoder.encode(password),
                        User.Role.ADMIN));
                int monitors = jdbc.update("UPDATE monitors SET owner_id = ? WHERE owner_id IS NULL", admin.getId());
                int channels = jdbc.update("UPDATE alert_channels SET owner_id = ? WHERE owner_id IS NULL", admin.getId());
                log.info("Created bootstrap administrator; adopted {} monitor(s) and {} alert channel(s)",
                        monitors, channels);
            });
        } catch (DataIntegrityViolationException e) {
            log.info("Bootstrap administrator was created concurrently by another instance");
        }
    }
}
