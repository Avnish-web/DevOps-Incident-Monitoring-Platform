package dev.monitoring.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A destination for incident notifications. The target and signing secret are stored only
 * in encrypted form (see {@link dev.monitoring.common.crypto.SecretCipher}).
 */
@Entity
@Table(name = "alert_channels")
public class AlertChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "owner_id", updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private AlertChannelType type;

    @Column(name = "target_encrypted", nullable = false)
    private String targetEncrypted;

    @Column(name = "signing_secret_encrypted")
    private String signingSecretEncrypted;

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AlertChannel() {
        // for JPA
    }

    public AlertChannel(String name, AlertChannelType type, String targetEncrypted) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.targetEncrypted = Objects.requireNonNull(targetEncrypted, "targetEncrypted");
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public AlertChannelType getType() {
        return type;
    }

    public String getTargetEncrypted() {
        return targetEncrypted;
    }

    public void setTargetEncrypted(String targetEncrypted) {
        this.targetEncrypted = Objects.requireNonNull(targetEncrypted, "targetEncrypted");
    }

    public String getSigningSecretEncrypted() {
        return signingSecretEncrypted;
    }

    public void setSigningSecretEncrypted(String signingSecretEncrypted) {
        this.signingSecretEncrypted = signingSecretEncrypted;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    /** The user who owns this resource; set once at creation. */
    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        if (this.ownerId != null && !this.ownerId.equals(ownerId)) {
            throw new IllegalStateException("Owner cannot be changed");
        }
        this.ownerId = ownerId;
    }
}
