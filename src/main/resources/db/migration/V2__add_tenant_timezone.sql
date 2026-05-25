-- ─────────────────────────────────────────────────────────────────────────────
-- V2 — Add timezone column to tenants table
-- NFR13: notifications must respect the local timezone of each OpenMRS organisation.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE tenants
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';
