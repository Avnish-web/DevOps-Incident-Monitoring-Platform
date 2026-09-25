package dev.monitoring.api.auth;

import dev.monitoring.api.security.CurrentUser;
import dev.monitoring.api.security.LoginRateLimiter;
import dev.monitoring.api.security.PasswordPolicy;
import dev.monitoring.api.security.UserPrincipal;
import dev.monitoring.common.domain.User;
import dev.monitoring.common.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, current user and password change. Logout is handled by the security filter chain
 * ({@code POST /api/v1/auth/logout}).
 */
@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /**
     * Same cookie settings as {@code csrf.spa()} (XSRF-TOKEN, readable by the SPA). Used only
     * to clear the token after login so a pre-login token cannot be reused.
     */
    private static final CsrfTokenRepository CSRF_COOKIES = CookieCsrfTokenRepository.withHttpOnlyFalse();

    public record LoginRequest(@NotBlank @Size(max = 254) String email,
                               @NotBlank @Size(max = 128) String password) {
    }

    public record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
                                        @NotBlank @Size(max = 128) String newPassword) {
    }

    public record MeResponse(UUID id, String email, User.Role role) {
        static MeResponse of(UserPrincipal p) {
            return new MeResponse(p.id(), p.email(), p.role());
        }
    }

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginRateLimiter rateLimiter;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          LoginRateLimiter rateLimiter, UserRepository users,
                          PasswordEncoder passwordEncoder, CurrentUser currentUser, Clock clock) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.rateLimiter = rateLimiter;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** Issues the XSRF-TOKEN cookie. The SPA calls this before its first state-changing request. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken(); // materialise the deferred token so the cookie is written
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                                   HttpServletResponse response) {
        String email = User.normalizeEmail(body.email());
        String ip = request.getRemoteAddr();
        Optional<Duration> blocked = rateLimiter.blockedFor(email, ip);
        if (blocked.isPresent()) {
            log.warn("Login blocked by rate limiter");
            throw new TooManyAttemptsException(blocked.get());
        }

        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, body.password()));
        } catch (AuthenticationException e) {
            rateLimiter.recordFailure(email, ip);
            log.info("Failed login attempt");
            // Same message for unknown user, wrong password and disabled account.
            throw new InvalidCredentialsException();
        }
        rateLimiter.reset(email);

        // Session fixation protection: never reuse a session id from before login.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        // Rotate the CSRF token on privilege change; the SPA fetches a fresh one afterwards.
        CSRF_COOKIES.saveToken(null, request, response);

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        users.findById(principal.id()).ifPresent(u -> u.setLastLoginAt(clock.instant()));
        log.info("User {} logged in", principal.id());
        return ResponseEntity.ok(MeResponse.of(principal));
    }

    @GetMapping("/me")
    public MeResponse me() {
        return MeResponse.of(currentUser.get());
    }

    @PostMapping(path = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        User user = users.findById(currentUser.id()).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(body.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String violation = PasswordPolicy.violation(body.newPassword(), user.getEmail());
        if (violation != null) {
            throw new WeakPasswordException(violation);
        }
        user.setPasswordHash(passwordEncoder.encode(body.newPassword()));
        log.info("User {} changed their password", user.getId());
        return ResponseEntity.noContent().build();
    }

    /** 429 with Retry-After. */
    public static class TooManyAttemptsException extends RuntimeException {
        private final Duration retryAfter;

        public TooManyAttemptsException(Duration retryAfter) {
            super("Too many failed login attempts. Try again later.");
            this.retryAfter = retryAfter;
        }

        public Duration retryAfter() {
            return retryAfter;
        }

        public HttpHeaders headers() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, retryAfter.toSeconds())));
            return headers;
        }
    }

    /** 401 with a message that does not reveal whether the account exists. */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid e-mail or password");
        }
    }

    /** 400 on the password field. */
    public static class WeakPasswordException extends RuntimeException {
        public WeakPasswordException(String message) {
            super(message);
        }
    }
}
