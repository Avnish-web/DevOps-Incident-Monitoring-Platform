package dev.monitoring.worker.alert;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes alert deliveries into the outbox. Must be called inside the transaction that
 * opens or resolves the incident: either both commit or neither does.
 */
@Component
public class AlertOutbox {

    private static final String ENQUEUE = """
            INSERT INTO alert_deliveries (channel_id, incident_id, event_type, payload)
            SELECT c.id, ?, ?, CAST(? AS jsonb) FROM alert_channels c WHERE c.enabled
            ON CONFLICT (incident_id, event_type, channel_id) WHERE incident_id IS NOT NULL
            DO NOTHING
            """;

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public AlertOutbox(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** @return number of deliveries queued (one per enabled channel) */
    public int enqueue(UUID incidentId, AlertPayload payload) {
        return jdbc.update(ENQUEUE, incidentId, payload.event(), json.writeValueAsString(payload));
    }
}
