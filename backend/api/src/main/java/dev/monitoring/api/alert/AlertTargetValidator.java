package dev.monitoring.api.alert;

import dev.monitoring.api.config.TargetPolicyConfig.TargetPolicyProperties;
import dev.monitoring.common.domain.AlertChannelType;
import dev.monitoring.common.net.InvalidTargetUrlException;
import dev.monitoring.common.net.TargetUrlValidator;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates channel destinations. Webhook URLs get the same SSRF checks as monitor URLs
 * (the worker re-checks at send time); Slack channels must point at Slack's webhook host.
 */
@Component
public class AlertTargetValidator {

    // Deliberately strict: one @, no spaces or control characters, a dotted domain.
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+'-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

    private final TargetUrlValidator urlValidator;
    private final boolean allowPrivateTargets;

    public AlertTargetValidator(TargetUrlValidator urlValidator, TargetPolicyProperties policy) {
        this.urlValidator = urlValidator;
        this.allowPrivateTargets = policy.allowPrivateAddresses();
    }

    /** @return the normalized target to store */
    public String validate(AlertChannelType type, String target) {
        if (target == null || target.isBlank()) {
            throw new InvalidAlertTargetException("Target is required");
        }
        String value = target.strip();
        return switch (type) {
            case WEBHOOK -> validateWebhook(value);
            case SLACK -> validateSlack(value);
            case EMAIL -> validateEmail(value);
        };
    }

    private String validateWebhook(String url) {
        URI uri = parseSafeUrl(url);
        // Payloads describe your infrastructure: require TLS unless internal targets are allowed.
        if (!allowPrivateTargets && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new InvalidAlertTargetException("Webhook URL must use https");
        }
        return url;
    }

    private String validateSlack(String url) {
        URI uri = parseSafeUrl(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"hooks.slack.com".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1
                || uri.getRawPath() == null || !uri.getRawPath().startsWith("/services/")
                || uri.getRawQuery() != null) {
            throw new InvalidAlertTargetException(
                    "Slack target must be an incoming webhook URL (https://hooks.slack.com/services/...)");
        }
        return url;
    }

    private static String validateEmail(String address) {
        if (address.length() > 254 || !EMAIL.matcher(address).matches()) {
            throw new InvalidAlertTargetException("Target must be a valid e-mail address");
        }
        return address.toLowerCase(Locale.ROOT);
    }

    private URI parseSafeUrl(String url) {
        try {
            return urlValidator.validate(url);
        } catch (InvalidTargetUrlException e) {
            throw new InvalidAlertTargetException(e.getMessage());
        }
    }

    /** Masked form safe to show in the UI: never reveals tokens embedded in the target. */
    public static String preview(AlertChannelType type, String target) {
        if (type == AlertChannelType.EMAIL) {
            int at = target.indexOf('@');
            return at <= 0 ? "***" : target.charAt(0) + "***" + target.substring(at);
        }
        try {
            URI uri = URI.create(target);
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort()) + "/…";
        } catch (IllegalArgumentException e) {
            return "***";
        }
    }
}
