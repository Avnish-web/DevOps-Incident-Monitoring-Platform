package dev.monitoring.api.monitor;

import java.util.UUID;

public class MonitorNotFoundException extends RuntimeException {

    public MonitorNotFoundException(UUID id) {
        super("Monitor " + id + " not found");
    }
}
