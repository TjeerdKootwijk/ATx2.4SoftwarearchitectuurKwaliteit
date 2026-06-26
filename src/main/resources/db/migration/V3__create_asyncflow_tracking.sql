-- ─────────────────────────────────────────────────────────────────────────────
-- V3 — AsyncFlow afleverstatus-tracking
-- ─────────────────────────────────────────────────────────────────────────────
-- AsyncFlow is een asynchrone provider: bij versturen is een bericht alleen
-- 'accepted', niet afgeleverd. De werkelijke uitkomst (Completed/Failed) wordt
-- pas later beschikbaar via GET /asyncflow/{trackingId}.
--
-- Deze tabel houdt de nog niet afgeronde AsyncFlow-berichten bij, zodat de
-- AsyncFlowStatusPoller de status periodiek kan opvragen en de definitieve
-- uitkomst naar notification_logs kan wegschrijven.
--
-- NFR10/NFR11: bevat GEEN patiëntgegevens of berichtinhoud — alleen de trackingId
--              en interne meta-informatie. Rijen worden verwijderd zodra de
--              status definitief is.
CREATE TABLE asyncflow_tracking (
    id              BIGSERIAL    PRIMARY KEY,
    tracking_id     VARCHAR(128) NOT NULL UNIQUE,   -- ID van AsyncFlow (ASF-...)
    notification_id VARCHAR(64)  NOT NULL,           -- interne UUID, geen patiëntidentifier
    tenant_id       VARCHAR(128) NOT NULL,           -- organisatie namens wie verstuurd is
    status          VARCHAR(16)  NOT NULL,           -- PENDING zolang nog niet afgerond
    retry_count     INTEGER      NOT NULL DEFAULT 0, -- aantal transport-pogingen vóór acceptatie
    poll_count      INTEGER      NOT NULL DEFAULT 0, -- aantal statuscontroles
    submitted_at    TIMESTAMP    NOT NULL,
    last_checked_at TIMESTAMP
);

CREATE INDEX idx_asyncflow_tracking_status ON asyncflow_tracking (status);
