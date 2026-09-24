package dev.monitoring.worker.alert;

import dev.monitoring.common.crypto.SecretCipher;
import dev.monitoring.common.domain.AlertChannelType;
import dev.monitoring.worker.metrics.WorkerMetrics;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Delivers queued alerts from the outbox.
 *
 * <p>Claiming mirrors check scheduling: {@code FOR UPDATE SKIP LOCKED} lets any number of
 * workers share the queue, and pushing {@code next_attempt_at} forward at claim time is a
 * lease, so a crash mid-send only delays the delivery. Failures retry with exponential
 * backoff (30 s, 1 min, 2 min, … capped at 1 h) up to {@code maxAttempts}, then the
 * delivery is marked FAILED. Delivery is at-least-once: a receiver may rarely see a
 * duplicate, identifiable by {@code X-Monitoring-Delivery}.
 */
@Component
public class AlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatcher.class);

    private static final String CLAIM = """
            UPDATE alert_deliveries d
               SET attempts = d.attempts + 1,
                   next_attempt_at = now() + interval '5 minutes'
              FROM (SELECT id FROM alert_deliveries
                     WHERE status = 'PENDING' AND next_attempt_at <= now()
                     ORDER BY next_attempt_at
                     LIMIT ?
                       FOR UPDATE SKIP LOCKED) due,
                   alert_channels c
             WHERE d.id = due.id AND c.id = d.channel_id
            RETURNING d.id, d.event_type, d.payload::text AS payload, d.attempts,
                      c.type, c.target_encrypted, c.signing_secret_encrypted, c.enabled
            """;

    record ClaimedDelivery(long id, String eventType, String payload, int attempts,
                           AlertChannelType channelType, String targetEncrypted,
                           String signingSecretEncrypted, boolean channelEnabled) {
    }

    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final JsonMapper json;
    private final WorkerMetrics metrics;
    private final Map<AlertChannelType, AlertSender> senders = new EnumMap<>(AlertChannelType.class);
    private final boolean enabled;
    private final int maxAttempts;

    public AlertDispatcher(JdbcTemplate jdbc, SecretCipher cipher, JsonMapper json,
                           WorkerMetrics metrics, List<AlertSender> senders,
                           @Value("${monitoring.alerting.dispatcher-enabled:true}") boolean enabled,
                           @Value("${monitoring.alerting.max-attempts:8}") int maxAttempts) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.json = json;
        this.metrics = metrics;
        senders.forEach(s -> this.senders.put(s.type(), s));
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(initialDelayString = "${monitoring.alerting.dispatch-initial-delay:10s}",
            fixedDelayString = "${monitoring.alerting.dispatch-interval:5s}")
    public void scheduledRun() {
        if (!enabled) {
            return;
        }
        try {
            dispatchDue(10);
        } catch (RuntimeException e) {
            log.warn("Alert dispatch failed, will retry: {}", e.toString());
        }
    }

    /** Claims and sends due deliveries in batches. @return number processed */
    public int dispatchDue(int maxBatches) {
        int processed = 0;
        for (int i = 0; i < maxBatches; i++) {
            List<ClaimedDelivery> batch = claim(20);
            if (batch.isEmpty()) {
                break;
            }
            batch.forEach(this::deliver);
            processed += batch.size();
        }
        return processed;
    }

    /** A single UPDATE ... RETURNING statement: atomic on its own, no transaction needed. */
    List<ClaimedDelivery> claim(int limit) {
        return jdbc.query(CLAIM, (rs, n) -> new ClaimedDelivery(
                rs.getLong("id"), rs.getString("event_type"), rs.getString("payload"),
                rs.getInt("attempts"), AlertChannelType.valueOf(rs.getString("type")),
                rs.getString("target_encrypted"), rs.getString("signing_secret_encrypted"),
                rs.getBoolean("enabled")), limit);
    }

    void deliver(ClaimedDelivery d) {
        MDC.put("alertDeliveryId", Long.toString(d.id()));
        try {
            if (!d.channelEnabled()) {
                finish(d, "CANCELLED", "Channel disabled");
                return;
            }
            AlertSender sender = senders.get(d.channelType());
            String target = cipher.decrypt(d.targetEncrypted());
            String secret = d.signingSecretEncrypted() == null ? null : cipher.decrypt(d.signingSecretEncrypted());
            AlertPayload payload = json.readValue(d.payload(), AlertPayload.class);
            sender.send(d.id(), target, secret, payload, d.payload());
            finish(d, "SENT", null);
            log.info("Alert delivered: channelType={} event={}", d.channelType(), d.eventType());
        } catch (AlertDeliveryException | RuntimeException e) {
            fail(d, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            MDC.remove("alertDeliveryId");
        }
    }

    private void finish(ClaimedDelivery d, String status, String reason) {
        jdbc.update("""
                UPDATE alert_deliveries
                   SET status = ?, last_error = ?, sent_at = CASE WHEN ? = 'SENT' THEN now() END
                 WHERE id = ?
                """, status, reason, status, d.id());
        metrics.recordAlertDelivery(d.channelType(), status.toLowerCase());
    }

    private void fail(ClaimedDelivery d, String error) {
        String message = error.length() > 512 ? error.substring(0, 512) : error;
        if (d.attempts() >= maxAttempts) {
            jdbc.update("UPDATE alert_deliveries SET status = 'FAILED', last_error = ? WHERE id = ?",
                    message, d.id());
            metrics.recordAlertDelivery(d.channelType(), "failed");
            log.warn("Alert delivery gave up after {} attempts: {}", d.attempts(), message);
            return;
        }
        Duration backoff = backoff(d.attempts());
        jdbc.update("""
                UPDATE alert_deliveries
                   SET last_error = ?, next_attempt_at = now() + make_interval(secs => ?)
                 WHERE id = ?
                """, message, backoff.toSeconds(), d.id());
        metrics.recordAlertDelivery(d.channelType(), "retry");
        log.info("Alert delivery attempt {} failed, retrying in {}s: {}", d.attempts(),
                backoff.toSeconds(), message);
    }

    /** 30 s × 2^(attempt-1), capped at 1 hour. */
    static Duration backoff(int attempt) {
        long seconds = 30L << Math.min(Math.max(attempt - 1, 0), 10);
        return Duration.ofSeconds(Math.min(seconds, 3600));
    }
}
