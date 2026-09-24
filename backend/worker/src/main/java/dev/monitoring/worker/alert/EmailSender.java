package dev.monitoring.worker.alert;

import dev.monitoring.common.domain.AlertChannelType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Plain-text e-mail through the SMTP server configured with {@code SPRING_MAIL_*} variables.
 * Without an SMTP host no mail sender exists and deliveries fail with a clear error.
 */
@Component
public class EmailSender implements AlertSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public EmailSender(ObjectProvider<JavaMailSender> mailSender,
                       @Value("${monitoring.alerting.email.from:monitoring@localhost}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public AlertChannelType type() {
        return AlertChannelType.EMAIL;
    }

    @Override
    public void send(long deliveryId, String target, String signingSecret, AlertPayload payload,
                     String rawPayload) throws AlertDeliveryException {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new AlertDeliveryException("E-mail is not configured (set SPRING_MAIL_HOST)");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(target);
        message.setSubject(subject(payload));
        message.setText(payload.body());
        try {
            sender.send(message);
        } catch (MailException e) {
            throw new AlertDeliveryException("SMTP delivery failed: " + e.getMessage(), e);
        }
    }

    /** Subjects never contain line breaks (header injection). */
    static String subject(AlertPayload payload) {
        String prefix = payload.isTest() ? "[TEST] "
                : "INCIDENT_OPENED".equals(payload.event()) ? "[DOWN] " : "[RECOVERED] ";
        String subject = prefix + payload.title();
        return subject.replaceAll("[\\r\\n\\t]+", " ").strip();
    }
}
