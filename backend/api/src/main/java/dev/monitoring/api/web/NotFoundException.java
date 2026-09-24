package dev.monitoring.api.web;

/** A requested resource does not exist (HTTP 404). The message never includes the ID. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resourceName) {
        super(resourceName + " not found");
    }
}
