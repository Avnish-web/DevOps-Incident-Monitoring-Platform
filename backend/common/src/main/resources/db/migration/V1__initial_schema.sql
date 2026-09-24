-- Initial schema: monitored targets, their check history, and incidents.
-- Users/ownership (Phase 13) and alerting tables (Phase 12) are added by later migrations.

CREATE TABLE monitors (
    id                    uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  varchar(100) NOT NULL,
    type                  varchar(16)  NOT NULL,
    url                   varchar(2048) NOT NULL,
    http_method           varchar(8)   NOT NULL DEFAULT 'GET',
    interval_seconds      integer      NOT NULL,
    timeout_ms            integer      NOT NULL,
    -- NULL means "any 2xx or 3xx status counts as success"
    expected_status       integer,
    failure_threshold     integer      NOT NULL DEFAULT 3,
    recovery_threshold    integer      NOT NULL DEFAULT 2,
    enabled               boolean      NOT NULL DEFAULT true,
    status                varchar(16)  NOT NULL DEFAULT 'UNKNOWN',
    consecutive_failures  integer      NOT NULL DEFAULT 0,
    consecutive_successes integer      NOT NULL DEFAULT 0,
    last_checked_at       timestamptz,
    next_check_at         timestamptz  NOT NULL DEFAULT now(),
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    version               bigint       NOT NULL DEFAULT 0,

    CONSTRAINT ck_monitors_name_not_blank     CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_monitors_type               CHECK (type IN ('HTTP')),
    CONSTRAINT ck_monitors_url_scheme         CHECK (url ~* '^https?://'),
    CONSTRAINT ck_monitors_http_method        CHECK (http_method IN ('GET', 'HEAD')),
    CONSTRAINT ck_monitors_interval           CHECK (interval_seconds BETWEEN 30 AND 86400),
    CONSTRAINT ck_monitors_timeout            CHECK (timeout_ms BETWEEN 1000 AND 30000),
    CONSTRAINT ck_monitors_expected_status    CHECK (expected_status IS NULL OR expected_status BETWEEN 100 AND 599),
    CONSTRAINT ck_monitors_failure_threshold  CHECK (failure_threshold BETWEEN 1 AND 10),
    CONSTRAINT ck_monitors_recovery_threshold CHECK (recovery_threshold BETWEEN 1 AND 10),
    CONSTRAINT ck_monitors_status             CHECK (status IN ('UNKNOWN', 'UP', 'DOWN')),
    CONSTRAINT ck_monitors_counters           CHECK (consecutive_failures >= 0 AND consecutive_successes >= 0)
);

-- Scheduler query: enabled monitors ordered by next_check_at (see docs/architecture.md §5.1).
CREATE INDEX idx_monitors_due ON monitors (next_check_at) WHERE enabled;


-- Allocated in blocks of 50 so Hibernate can batch inserts (matches allocationSize in CheckResult).
CREATE SEQUENCE check_results_seq INCREMENT BY 50;

CREATE TABLE check_results (
    id            bigint       PRIMARY KEY DEFAULT nextval('check_results_seq'),
    monitor_id    uuid         NOT NULL REFERENCES monitors (id) ON DELETE CASCADE,
    checked_at    timestamptz  NOT NULL,
    success       boolean      NOT NULL,
    status_code   integer,
    latency_ms    integer,
    error_type    varchar(32),
    error_message varchar(512),

    CONSTRAINT ck_check_results_latency     CHECK (latency_ms IS NULL OR latency_ms >= 0),
    CONSTRAINT ck_check_results_status_code CHECK (status_code IS NULL OR status_code BETWEEN 100 AND 599),
    -- A failed check must say why; a successful one must not carry an error.
    CONSTRAINT ck_check_results_error       CHECK ((success AND error_type IS NULL) OR (NOT success AND error_type IS NOT NULL))
);

ALTER SEQUENCE check_results_seq OWNED BY check_results.id;

-- History queries: latest results for one monitor.
CREATE INDEX idx_check_results_monitor_time ON check_results (monitor_id, checked_at DESC);
-- Retention job: delete results older than N days.
CREATE INDEX idx_check_results_checked_at ON check_results (checked_at);


CREATE TABLE incidents (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    monitor_id  uuid         NOT NULL REFERENCES monitors (id) ON DELETE CASCADE,
    started_at  timestamptz  NOT NULL,
    resolved_at timestamptz,
    cause       varchar(512),

    CONSTRAINT ck_incidents_resolved_after_start CHECK (resolved_at IS NULL OR resolved_at >= started_at)
);

-- At most one open incident per monitor, enforced by the database rather than application code.
CREATE UNIQUE INDEX uq_incidents_open_per_monitor ON incidents (monitor_id) WHERE resolved_at IS NULL;
CREATE INDEX idx_incidents_monitor_started ON incidents (monitor_id, started_at DESC);
CREATE INDEX idx_incidents_started_at ON incidents (started_at DESC);
