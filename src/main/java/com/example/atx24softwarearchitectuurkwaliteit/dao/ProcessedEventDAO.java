package com.example.atx24softwarearchitectuurkwaliteit.dao;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.ProcessedEventEntity;

import java.time.LocalDateTime;

/**
 * Data access contract for idempotency tracking.
 *
 * Replaces the previous in-memory {@code HashSet<String>} in {@code IdempotencyService},
 * making deduplication durable across application restarts.
 */
public interface ProcessedEventDAO {

    /** Returns true when the given event ID has already been recorded. */
    boolean existsByEventId(String eventId);

    /** Persists a new processed event record. */
    void save(ProcessedEventEntity entity);

    /**
     * Deletes all records processed before the given cutoff timestamp.
     * Used by the nightly cleanup job (NFR10).
     */
    void deleteProcessedBefore(LocalDateTime cutoff);
}
