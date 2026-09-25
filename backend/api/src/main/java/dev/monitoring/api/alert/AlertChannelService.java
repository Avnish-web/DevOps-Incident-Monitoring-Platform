package dev.monitoring.api.alert;

import dev.monitoring.api.security.CurrentUser;
import dev.monitoring.api.web.NotFoundException;
import dev.monitoring.api.web.PageResponse;
import dev.monitoring.api.web.QuotaExceededException;
import dev.monitoring.common.crypto.SecretCipher;
import dev.monitoring.common.domain.AlertChannel;
import dev.monitoring.common.domain.AlertChannelType;
import dev.monitoring.common.repository.AlertChannelRepository;
import dev.monitoring.common.repository.AlertDeliveryRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AlertChannelService {

    private static final Logger log = LoggerFactory.getLogger(AlertChannelService.class);

    private final AlertChannelRepository channels;
    private final AlertDeliveryRepository deliveries;
    private final AlertTargetValidator validator;
    private final SecretCipher cipher;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CurrentUser currentUser;
    private final long maxChannelsPerUser;
    private final SecureRandom random = new SecureRandom();

    public AlertChannelService(AlertChannelRepository channels, AlertDeliveryRepository deliveries,
                               AlertTargetValidator validator, SecretCipher cipher,
                               JdbcTemplate jdbc, Clock clock, CurrentUser currentUser,
                               @Value("${monitoring.limits.max-alert-channels-per-user:20}") long maxChannelsPerUser) {
        this.currentUser = currentUser;
        this.maxChannelsPerUser = maxChannelsPerUser;
        this.channels = channels;
        this.deliveries = deliveries;
        this.validator = validator;
        this.cipher = cipher;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public AlertChannelResponse create(AlertChannelRequest request) {
        if (channels.countByOwnerId(currentUser.id()) >= maxChannelsPerUser) {
            throw new QuotaExceededException("alert channels", maxChannelsPerUser);
        }
        String target = validator.validate(request.type(), request.target());
        AlertChannel channel = new AlertChannel(request.name().strip(), request.type(),
                cipher.encrypt(target));
        channel.setEnabled(request.enabledOrDefault());
        channel.setOwnerId(currentUser.id());
        String signingSecret = null;
        if (request.type() == AlertChannelType.WEBHOOK) {
            byte[] secret = new byte[32];
            random.nextBytes(secret);
            signingSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
            channel.setSigningSecretEncrypted(cipher.encrypt(signingSecret));
        }
        AlertChannel saved = channels.saveAndFlush(channel);
        log.info("Created alert channel {} ({})", saved.getId(), saved.getType());
        return toResponse(saved, target, signingSecret);
    }

    @Transactional(readOnly = true)
    public List<AlertChannelResponse> list() {
        return channels.findAllByOwnerId(currentUser.id(), Sort.by("name").and(Sort.by("id"))).stream()
                .map(c -> toResponse(c, cipher.decrypt(c.getTargetEncrypted()), null))
                .toList();
    }

    @Transactional(readOnly = true)
    public AlertChannelResponse get(UUID id) {
        AlertChannel c = find(id);
        return toResponse(c, cipher.decrypt(c.getTargetEncrypted()), null);
    }

    public AlertChannelResponse update(UUID id, AlertChannelRequest request) {
        AlertChannel channel = find(id);
        if (request.type() != channel.getType()) {
            throw new InvalidAlertTargetException("Channel type cannot be changed; create a new channel");
        }
        String target;
        if (request.target() == null || request.target().isBlank()) {
            target = cipher.decrypt(channel.getTargetEncrypted());
        } else {
            target = validator.validate(request.type(), request.target());
            channel.setTargetEncrypted(cipher.encrypt(target));
        }
        channel.setName(request.name().strip());
        channel.setEnabled(request.enabledOrDefault());
        AlertChannel saved = channels.saveAndFlush(channel);
        return toResponse(saved, target, null);
    }

    public void delete(UUID id) {
        channels.delete(find(id));
        log.info("Deleted alert channel {}", id);
    }

    /** Queues a test notification; the worker's dispatcher delivers it like a real alert. */
    public AlertDeliveryResponse sendTest(UUID id) {
        AlertChannel channel = find(id);
        String payload = """
                {"event":"TEST","message":"Test notification from the Monitoring Platform","timestamp":"%s"}"""
                .formatted(clock.instant());
        Long deliveryId = jdbc.queryForObject("""
                INSERT INTO alert_deliveries (channel_id, event_type, payload)
                VALUES (?, 'TEST', CAST(? AS jsonb)) RETURNING id
                """, Long.class, channel.getId(), payload);
        return AlertDeliveryResponse.from(deliveries.findById(deliveryId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertDeliveryResponse> deliveries(UUID channelId, Pageable pageable) {
        find(channelId);
        return PageResponse.of(deliveries.findByChannelId(channelId, pageable),
                AlertDeliveryResponse::from);
    }

    private AlertChannel find(UUID id) {
        return channels.findByIdAndOwnerId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Alert channel"));
    }

    private static AlertChannelResponse toResponse(AlertChannel c, String target, String signingSecret) {
        return new AlertChannelResponse(c.getId(), c.getName(), c.getType(),
                AlertTargetValidator.preview(c.getType(), target), c.isEnabled(), c.getCreatedAt(),
                c.getUpdatedAt(), c.getVersion(), signingSecret);
    }
}
