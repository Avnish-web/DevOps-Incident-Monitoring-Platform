package dev.monitoring.worker.alert;

import dev.monitoring.common.domain.AlertChannelType;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Generic webhook: POSTs the stored JSON payload and signs it so receivers can verify origin
 * and freshness:
 * <pre>
 * X-Monitoring-Timestamp: 1767225600
 * X-Monitoring-Signature: sha256=hex(HMAC-SHA256(secret, timestamp + "." + body))
 * </pre>
 * Receivers should reject timestamps older than a few minutes (replay protection).
 */
@Component
public class WebhookSender implements AlertSender {

    private final AlertHttpClient http;
    private final Clock clock;

    public WebhookSender(AlertHttpClient http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    @Override
    public AlertChannelType type() {
        return AlertChannelType.WEBHOOK;
    }

    @Override
    public void send(long deliveryId, String target, String signingSecret, AlertPayload payload,
                     String rawPayload) throws AlertDeliveryException {
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        http.postJson(target, rawPayload, Map.of(
                "X-Monitoring-Event", payload.event(),
                "X-Monitoring-Delivery", Long.toString(deliveryId),
                "X-Monitoring-Timestamp", timestamp,
                "X-Monitoring-Signature", "sha256=" + sign(signingSecret, timestamp + "." + rawPayload)));
    }

    static String sign(String secret, String data) throws AlertDeliveryException {
        if (secret == null || secret.isEmpty()) {
            throw new AlertDeliveryException("Webhook channel has no signing secret");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new AlertDeliveryException("Could not sign payload", e);
        }
    }
}
