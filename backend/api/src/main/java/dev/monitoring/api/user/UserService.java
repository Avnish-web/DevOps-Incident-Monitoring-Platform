package dev.monitoring.api.user;

import dev.monitoring.api.auth.AuthController.WeakPasswordException;
import dev.monitoring.api.security.CurrentUser;
import dev.monitoring.api.security.PasswordPolicy;
import dev.monitoring.api.web.NotFoundException;
import dev.monitoring.common.domain.User;
import dev.monitoring.common.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final Pattern EMAIL = Pattern.compile(
            "^[a-z0-9._%+'-]{1,64}@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, CurrentUser currentUser,
                       FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public List<User> list() {
        return users.findAll(Sort.by("email"));
    }

    public User create(String rawEmail, String password, User.Role role) {
        String email = User.normalizeEmail(rawEmail);
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new InvalidUserException("email", "Must be a valid e-mail address");
        }
        String violation = PasswordPolicy.violation(password, email);
        if (violation != null) {
            throw new WeakPasswordException(violation);
        }
        if (users.existsByEmail(email)) {
            throw new InvalidUserException("email", "A user with this e-mail already exists");
        }
        User user = users.saveAndFlush(new User(email, passwordEncoder.encode(password), role));
        log.info("User {} created with role {}", user.getId(), role);
        return user;
    }

    /** Deletes the user, their monitors and channels (cascade), and ends their sessions. */
    public void delete(UUID id) {
        if (id.equals(currentUser.id())) {
            throw new InvalidUserException("id", "You cannot delete your own account");
        }
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("User"));
        users.delete(user);
        sessions.findByPrincipalName(user.getEmail()).keySet().forEach(sessions::deleteById);
        log.info("User {} deleted and signed out", id);
    }

    /** 400 with a field error. */
    public static class InvalidUserException extends RuntimeException {
        private final String field;

        public InvalidUserException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }
}
