package dev.monitoring.api.web;

/** The {@code If-Match} header did not match the resource's current version (HTTP 412). */
public class PreconditionFailedException extends RuntimeException {

    public PreconditionFailedException(String message) {
        super(message);
    }
}
