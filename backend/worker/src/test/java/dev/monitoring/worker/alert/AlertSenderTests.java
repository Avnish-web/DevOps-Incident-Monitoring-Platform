package dev.monitoring.worker.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.monitoring.common.net.BlockedAddresses;
import dev.monitoring.worker.check.GuardedDnsResolver;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class AlertSenderTests {

    private static final AlertPayload OPENED = AlertPayload.opened(Instant.parse("2030-01-01T00:00:00Z"),
            UUID.randomUUID(), "Shop <!channel> & co\r\nBcc: x@evil.test", "https://shop.example.com",
            UUID.randomUUID(), Instant.parse("2030-01-01T00:00:00Z"), "TIMEOUT: slow");

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    @Test
    void slackEscapesMentionsAndLinks() {
        String text = SlackSender.format(OPENED);

        assertThat(text).startsWith(":red_circle: *Shop &lt;!channel&gt; &amp; co");
        assertThat(text).doesNotContain("<!channel>");
    }

    @Test
    void emailSubjectCannotInjectHeaders() {
        assertThat(EmailSender.subject(OPENED)).doesNotContain("\r").doesNotContain("\n")
                .startsWith("[DOWN] Shop");
    }

    @Test
    void emailIsSentThroughConfiguredSmtp() throws Exception {
        JavaMailSender mail = mock(JavaMailSender.class);
        new EmailSender(provider(mail), "alerts@example.com").send(1, "ops@example.com", null, OPENED, "{}");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("ops@example.com");
        assertThat(sent.getValue().getFrom()).isEqualTo("alerts@example.com");
        assertThat(sent.getValue().getText()).contains("URL: https://shop.example.com").contains("Cause: TIMEOUT");
    }

    @Test
    void emailFailsClearlyWhenSmtpIsMissingOrDown() {
        assertThatThrownBy(() -> new EmailSender(provider(null), "a@example.com")
                .send(1, "ops@example.com", null, OPENED, "{}"))
                .isInstanceOf(AlertDeliveryException.class).hasMessageContaining("not configured");

        JavaMailSender broken = mock(JavaMailSender.class);
        doThrow(new MailSendException("connection refused")).when(broken).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> new EmailSender(provider(broken), "a@example.com")
                .send(1, "ops@example.com", null, OPENED, "{}"))
                .isInstanceOf(AlertDeliveryException.class).hasMessageContaining("SMTP delivery failed");
    }

    @Test
    void webhookToInternalAddressIsBlockedAtSendTime() throws Exception {
        GuardedDnsResolver guarded = new GuardedDnsResolver(InetAddress::getAllByName, BlockedAddresses::isBlocked);
        try (AlertHttpClient client = new AlertHttpClient(guarded, "test")) {
            assertThatThrownBy(() -> client.postJson("http://169.254.169.254/latest", "{}", Map.of()))
                    .isInstanceOf(AlertDeliveryException.class)
                    .hasMessageContaining("private, loopback or reserved");
        }
    }

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        assertThat(AlertDispatcher.backoff(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(AlertDispatcher.backoff(2)).isEqualTo(Duration.ofMinutes(1));
        assertThat(AlertDispatcher.backoff(4)).isEqualTo(Duration.ofMinutes(4));
        assertThat(AlertDispatcher.backoff(20)).isEqualTo(Duration.ofHours(1));
    }
}
