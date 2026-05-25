-- ─────────────────────────────────────────────────────────────────────────────
-- V2 — Add per-tenant timezone column (NFR13)
-- ─────────────────────────────────────────────────────────────────────────────

-- NFR13: each tenant has its own local timezone so that notification windows
--        (24h / 1h before appointment) are calculated in the tenant's local time
--        rather than the server's system timezone.
ALTER TABLE tenants
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';
