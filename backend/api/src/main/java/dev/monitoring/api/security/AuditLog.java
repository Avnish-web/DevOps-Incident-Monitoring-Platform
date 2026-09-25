package dev.monitoring.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Security audit trail on the dedicated {@code AUDIT} logger. Events are structured
 * key-value pairs (fields in the JSON log), so they can be filtered, retained and alerted on
 * separately from application logs. Never logs passwords, tokens or raw e-mail addresses of
 * failed logins (only a short hash, enough to correlate attempts on one account).
 */
public final class AuditLog {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private AuditLog() {
    }

    public static void loginSucceeded(UUID userId, String ip) {
        event("auth.login.success").addKeyValue("userId", userId).addKeyValue("clientIp", ip).log();
    }

    public static void loginFailed(String email, String ip) {
        warn("auth.login.failure").addKeyValue("accountHash", hash(email)).addKeyValue("clientIp", ip).log();
    }

    public static void loginBlocked(String email, String ip) {
        warn("auth.login.blocked").addKeyValue("accountHash", hash(email)).addKeyValue("clientIp", ip).log();
    }

    public static void logout(String email) {
        event("auth.logout").addKeyValue("accountHash", hash(email)).log();
    }

    public static void passwordChanged(UUID userId) {
        event("auth.password.changed").addKeyValue("userId", userId).log();
    }

    public static void passwordChangeRejected(UUID userId, String reason) {
        warn("auth.password.change_rejected").addKeyValue("userId", userId).addKeyValue("reason", reason).log();
    }

    public static void userCreated(UUID actorId, UUID userId, String role) {
        event("admin.user.created").addKeyValue("actorId", actorId).addKeyValue("userId", userId)
                .addKeyValue("role", role).log();
    }

    public static void userDeleted(UUID actorId, UUID userId, int sessionsRevoked) {
        event("admin.user.deleted").addKeyValue("actorId", actorId).addKeyValue("userId", userId)
                .addKeyValue("sessionsRevoked", sessionsRevoked).log();
    }

    private static LoggingEventBuilder event(String action) {
        return AUDIT.atInfo().setMessage(action).addKeyValue("event.action", action);
    }

    private static LoggingEventBuilder warn(String action) {
        return AUDIT.atWarn().setMessage(action).addKeyValue("event.action", action);
    }

    /** First 16 hex chars of SHA-256 of the normalized address: correlatable, not reversible in practice. */
    static String hash(String email) {
        if (email == null) {
            return "none";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(email.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
