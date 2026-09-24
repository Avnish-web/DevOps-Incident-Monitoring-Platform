-- Alert channels and the delivery outbox (docs/architecture.md §5.4).

CREATE TABLE alert_channels (
    id                       uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                     varchar(100) NOT NULL,
    type                     varchar(16)  NOT NULL,
    -- Destination (webhook URL, Slack webhook URL or e-mail address), AES-GCM encrypted:
    -- webhook URLs often embed credentials, so they are treated as secrets.
    target_encrypted         text         NOT NULL,
    -- HMAC key for signing generic webhook payloads (encrypted); NULL for other types.
    signing_secret_encrypted text,
    enabled                  boolean      NOT NULL DEFAULT true,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    version                  bigint       NOT NULL DEFAULT 0,

    CONSTRAINT ck_alert_channels_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_alert_channels_type           CHECK (type IN ('WEBHOOK', 'SLACK', 'EMAIL'))
);

-- Outbox: one row per (event, channel), written in the same transaction as the incident
-- change, so no alert can be lost between the database commit and a message queue.
CREATE TABLE alert_deliveries (
    id              bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    channel_id      uuid         NOT NULL REFERENCES alert_channels (id) ON DELETE CASCADE,
    -- NULL for test deliveries triggered from the API.
    incident_id     uuid         REFERENCES incidents (id) ON DELETE CASCADE,
    event_type      varchar(24)  NOT NULL,
    payload         jsonb        NOT NULL,
    status          varchar(16)  NOT NULL DEFAULT 'PENDING',
    attempts        integer      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    last_error      varchar(512),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    sent_at         timestamptz,

    CONSTRAINT ck_alert_deliveries_event  CHECK (event_type IN ('INCIDENT_OPENED', 'INCIDENT_RESOLVED', 'TEST')),
    CONSTRAINT ck_alert_deliveries_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_alert_deliveries_attempts CHECK (attempts >= 0)
);

-- Idempotency: an incident event is delivered to a channel at most once.
CREATE UNIQUE INDEX uq_alert_deliveries_event_channel
    ON alert_deliveries (incident_id, event_type, channel_id) WHERE incident_id IS NOT NULL;
-- Dispatcher query: pending deliveries that are due.
CREATE INDEX idx_alert_deliveries_due ON alert_deliveries (next_attempt_at) WHERE status = 'PENDING';
-- Delivery history per channel.
CREATE INDEX idx_alert_deliveries_channel ON alert_deliveries (channel_id, created_at DESC);
