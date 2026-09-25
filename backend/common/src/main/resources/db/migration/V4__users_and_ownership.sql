-- Users and per-user ownership of monitors and alert channels (Phase 13).

CREATE TABLE users (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         varchar(254) NOT NULL,
    -- Delegating encoder format, e.g. "{bcrypt}$2a$12$..."; never the password itself.
    password_hash varchar(100) NOT NULL,
    role          varchar(16)  NOT NULL,
    enabled       boolean      NOT NULL DEFAULT true,
    last_login_at timestamptz,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    version       bigint       NOT NULL DEFAULT 0,

    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))
);

CREATE UNIQUE INDEX uq_users_email ON users (email);

-- Nullable only for rows created before authentication existed; the API assigns those to
-- the bootstrap administrator on first start. New rows always get an owner.
ALTER TABLE monitors ADD COLUMN owner_id uuid REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE alert_channels ADD COLUMN owner_id uuid REFERENCES users (id) ON DELETE CASCADE;

CREATE INDEX idx_monitors_owner ON monitors (owner_id);
CREATE INDEX idx_alert_channels_owner ON alert_channels (owner_id);
