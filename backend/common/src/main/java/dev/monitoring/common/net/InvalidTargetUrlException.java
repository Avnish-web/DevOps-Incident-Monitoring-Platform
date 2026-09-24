package dev.monitoring.common.net;

/**
 * A monitor URL was rejected. The message is a fixed, user-safe sentence that never echoes
 * the submitted value.
 */
public class InvalidTargetUrlException extends RuntimeException {

    public InvalidTargetUrlException(String message) {
        super(message);
    }
}
