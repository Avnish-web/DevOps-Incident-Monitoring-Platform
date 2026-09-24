package dev.monitoring.api.monitor;

import dev.monitoring.api.web.NotFoundException;
import java.util.UUID;

public class MonitorNotFoundException extends NotFoundException {

    public MonitorNotFoundException(UUID id) {
        super("Monitor");
    }
}
