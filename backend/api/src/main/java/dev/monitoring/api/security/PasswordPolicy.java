package dev.monitoring.api.security;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Password rules, following current guidance (length over composition rules). The maximum
 * keeps passwords under BCrypt's 72-byte input limit.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 64;

    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

    private PasswordPolicy() {
    }

    /** @return a user-facing reason, or null if the password is acceptable */
    public static String violation(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters";
        }
        if (password.length() > MAX_LENGTH) {
            return "Password must be at most " + MAX_LENGTH + " characters";
        }
        if (CONTROL.matcher(password).find()) {
            return "Password must not contain control characters";
        }
        if (email != null) {
            String localPart = email.toLowerCase(Locale.ROOT).split("@")[0];
            // Short local parts (e.g. "a@…") would reject almost every password.
            if (localPart.length() >= 4 && password.toLowerCase(Locale.ROOT).contains(localPart)) {
                return "Password must not contain your e-mail name";
            }
        }
        return null;
    }
}
