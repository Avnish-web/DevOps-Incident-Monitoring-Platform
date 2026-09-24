package dev.monitoring.worker.alert;

import dev.monitoring.common.domain.AlertChannelType;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Slack incoming webhook: posts a short text message. */
@Component
public class SlackSender implements AlertSender {

    private final AlertHttpClient http;
    private final JsonMapper json;

    public SlackSender(AlertHttpClient http, JsonMapper json) {
        this.http = http;
        this.json = json;
    }

    @Override
    public AlertChannelType type() {
        return AlertChannelType.SLACK;
    }

    @Override
    public void send(long deliveryId, String target, String signingSecret, AlertPayload payload,
                     String rawPayload) throws AlertDeliveryException {
        http.postJson(target, json.writeValueAsString(Map.of("text", format(payload))), Map.of());
    }

    static String format(AlertPayload p) {
        if (p.isTest()) {
            return ":white_check_mark: " + escape(p.body());
        }
        String icon = "INCIDENT_OPENED".equals(p.event()) ? ":red_circle:" : ":large_green_circle:";
        return icon + " *" + escape(p.title()) + "*\n" + escape(p.body().substring(p.title().length() + 1));
    }

    /**
     * Slack treats &, < and > as control characters (links, @channel mentions). User content
     * such as monitor names is escaped so it can never trigger a mention or inject a link.
     */
    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
