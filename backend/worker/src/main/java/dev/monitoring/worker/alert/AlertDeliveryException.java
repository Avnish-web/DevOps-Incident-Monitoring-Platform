package dev.monitoring.worker.alert;

/** A delivery attempt failed; the dispatcher retries it with backoff. */
public class AlertDeliveryException extends Exception {

    public AlertDeliveryException(String message) {
        super(message);
    }

    public AlertDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
