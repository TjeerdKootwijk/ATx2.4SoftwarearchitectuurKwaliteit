-- ─────────────────────────────────────────────────────────────────────────────
-- V1 — Initial schema
-- ─────────────────────────────────────────────────────────────────────────────

-- Stores configuration per OpenMRS organisation (tenant).
-- NFR1:  supports multiple independent OpenMRS instances.
-- NFR5:  sensitive columns (password, api_key, secret) must be AES-256
--        encrypted at the application level before being written here.
CREATE TABLE tenants (
    tenant_id             VARCHAR(128)  PRIMARY KEY,
    organization_name     VARCHAR(255)  NOT NULL,
    openmrs_base_url      VARCHAR(512)  NOT NULL,
    openmrs_username      VARCHAR(128)  NOT NULL,
    openmrs_password      VARCHAR(512)  NOT NULL,   -- AES-256 encrypted (NFR5)
    active                BOOLEAN       NOT NULL DEFAULT TRUE,
    notification_provider VARCHAR(32)   NOT NULL,
    provider_api_key      VARCHAR(512),              -- AES-256 encrypted (NFR5)
    provider_secret       VARCHAR(512),              -- AES-256 encrypted (NFR5)
    last_polled_at        TIMESTAMP,
    created_at            TIMESTAMP     NOT NULL,
    updated_at            TIMESTAMP     NOT NULL
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Tracks which appointment events have already been processed.
-- Replaces the previous in-memory HashSet so idempotency survives restarts (ADR-4).
-- NFR10: records are purged after 30 days by the nightly cleanup job.
CREATE TABLE processed_events (
    id           BIGSERIAL    PRIMARY KEY,
    event_id     VARCHAR(64)  NOT NULL UNIQUE,
    processed_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_processed_events_event_id    ON processed_events (event_id);
CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);

-- ─────────────────────────────────────────────────────────────────────────────
-- Stores meta-information about every notification delivery attempt.
-- FR2:   enables generating overviews of which notifications were sent on
--        behalf of which organisation, via which messaging provider.
-- NFR11: contains NO directly identifiable patient data or appointment details;
--        retained for a maximum of 1 year, then purged by the cleanup job.
CREATE TABLE notification_logs (
    id                  BIGSERIAL    PRIMARY KEY,
    notification_id     VARCHAR(64)  NOT NULL,         -- internal UUID, not a patient identifier
    tenant_id           VARCHAR(128) NOT NULL,          -- organisation that triggered the notification
    provider            VARCHAR(32)  NOT NULL,          -- SWIFTSEND | LEGACYLINK | ASYNCFLOW | SECUREPOST
    status              VARCHAR(16)  NOT NULL,          -- SUCCESS | FAILED
    sent_at             TIMESTAMP    NOT NULL,
    provider_message_id VARCHAR(128),                   -- ID returned by the provider (invoice reconciliation)
    retry_count         INTEGER      NOT NULL DEFAULT 0,
    error_message       VARCHAR(512)                    -- populated only when status = FAILED
);

CREATE INDEX idx_notification_logs_tenant_id ON notification_logs (tenant_id);
CREATE INDEX idx_notification_logs_sent_at   ON notification_logs (sent_at);
