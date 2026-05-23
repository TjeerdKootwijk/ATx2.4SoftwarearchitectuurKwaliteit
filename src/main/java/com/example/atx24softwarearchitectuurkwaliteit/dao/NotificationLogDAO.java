package com.example.atx24softwarearchitectuurkwaliteit.dao;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access contract for notification audit logging.
 *
 * Stores meta-information only (no patient data) so that
 * invoices from messaging providers can be verified (FR2, NFR11).
 */
public interface NotificationLogDAO {

    /** Persists a notification log entry (success or failure). */
    NotificationLogEntity save(NotificationLogEntity log);

    /** Returns all log entries for the given organisation. */
    List<NotificationLogEntity> findByTenantId(String tenantId);

    /**
     * Deletes log entries older than the given cutoff.
     * Used by the nightly cleanup job to enforce the 1-year retention limit (NFR11).
     */
    void deleteSentAtBefore(LocalDateTime cutoff);
}
