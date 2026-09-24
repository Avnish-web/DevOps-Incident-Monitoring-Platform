package dev.monitoring.worker.alert;

import dev.monitoring.common.domain.AlertChannelType;

/** Delivers one alert to one kind of channel. */
public interface AlertSender {

    AlertChannelType type();

    /**
     * @param target        decrypted destination (URL or e-mail address)
     * @param signingSecret decrypted HMAC key for webhooks, otherwise null
     * @param rawPayload    the stored JSON document, sent verbatim to webhooks
     */
    void send(long deliveryId, String target, String signingSecret, AlertPayload payload,
              String rawPayload) throws AlertDeliveryException;
}
