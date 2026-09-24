package dev.monitoring.api.alert;

/** The channel target was rejected. The message is fixed text and never echoes the input. */
public class InvalidAlertTargetException extends RuntimeException {

    public InvalidAlertTargetException(String message) {
        super(message);
    }
}
