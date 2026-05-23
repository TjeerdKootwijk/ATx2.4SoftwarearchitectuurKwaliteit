package com.example.atx24softwarearchitectuurkwaliteit.dao;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.TenantEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Data access contract for tenant (OpenMRS organisation) persistence.
 *
 * Consumers depend on this interface, not on Spring Data JPA directly,
 * keeping the persistence technology swappable and the code testable.
 */
public interface TenantDAO {

    /** Inserts or updates a tenant record. */
    TenantEntity save(TenantEntity tenant);

    /** Returns the tenant with the given ID, or empty when not found. */
    Optional<TenantEntity> findByTenantId(String tenantId);

    /** Returns all tenants whose {@code active} flag is true. */
    List<TenantEntity> findAllActive();

    /** Updates only the {@code last_polled_at} column for the given tenant. */
    void updateLastPolledAt(String tenantId, LocalDateTime timestamp);
}
