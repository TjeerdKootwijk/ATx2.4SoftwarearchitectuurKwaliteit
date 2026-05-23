package com.example.atx24softwarearchitectuurkwaliteit.dao.impl;

import com.example.atx24softwarearchitectuurkwaliteit.dao.NotificationLogDAO;
import com.example.atx24softwarearchitectuurkwaliteit.dao.jpa.NotificationLogJpaRepository;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA-backed implementation of {@link NotificationLogDAO}.
 *
 * Acts as an adapter between the business-facing DAO interface and the
 * Spring Data JPA repository.
 */
@Repository
public class NotificationLogDAOImpl implements NotificationLogDAO {

    private final NotificationLogJpaRepository jpaRepository;

    public NotificationLogDAOImpl(NotificationLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public NotificationLogEntity save(NotificationLogEntity log) {
        return jpaRepository.save(log);
    }

    @Override
    public List<NotificationLogEntity> findByTenantId(String tenantId) {
        return jpaRepository.findByTenantId(tenantId);
    }

    @Override
    public void deleteSentAtBefore(LocalDateTime cutoff) {
        jpaRepository.deleteBySentAtBefore(cutoff);
    }
}
