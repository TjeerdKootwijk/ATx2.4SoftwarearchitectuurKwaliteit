package com.example.atx24softwarearchitectuurkwaliteit.dao.impl;

import com.example.atx24softwarearchitectuurkwaliteit.dao.TenantDAO;
import com.example.atx24softwarearchitectuurkwaliteit.dao.jpa.TenantJpaRepository;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.TenantEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link TenantDAO}.
 *
 * Acts as an adapter between the business-facing DAO interface and the
 * Spring Data JPA repository, so the rest of the application never
 * imports Spring Data types directly.
 */
@Repository
public class TenantDAOImpl implements TenantDAO {

    private final TenantJpaRepository jpaRepository;

    public TenantDAOImpl(TenantJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public TenantEntity save(TenantEntity tenant) {
        return jpaRepository.save(tenant);
    }

    @Override
    public Optional<TenantEntity> findByTenantId(String tenantId) {
        return jpaRepository.findById(tenantId);
    }

    @Override
    public List<TenantEntity> findAllActive() {
        return jpaRepository.findByActiveTrue();
    }

    @Override
    public void updateLastPolledAt(String tenantId, LocalDateTime timestamp) {
        jpaRepository.updateLastPolledAt(tenantId, timestamp);
    }
}
