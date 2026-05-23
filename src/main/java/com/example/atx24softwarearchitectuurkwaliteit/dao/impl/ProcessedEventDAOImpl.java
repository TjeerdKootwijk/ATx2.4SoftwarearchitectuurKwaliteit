package com.example.atx24softwarearchitectuurkwaliteit.dao.impl;

import com.example.atx24softwarearchitectuurkwaliteit.dao.ProcessedEventDAO;
import com.example.atx24softwarearchitectuurkwaliteit.dao.jpa.ProcessedEventJpaRepository;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.ProcessedEventEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * JPA-backed implementation of {@link ProcessedEventDAO}.
 *
 * Acts as an adapter between the business-facing DAO interface and the
 * Spring Data JPA repository.
 */
@Repository
public class ProcessedEventDAOImpl implements ProcessedEventDAO {

    private final ProcessedEventJpaRepository jpaRepository;

    public ProcessedEventDAOImpl(ProcessedEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return jpaRepository.existsByEventId(eventId);
    }

    @Override
    public void save(ProcessedEventEntity entity) {
        jpaRepository.save(entity);
    }

    @Override
    public void deleteProcessedBefore(LocalDateTime cutoff) {
        jpaRepository.deleteByProcessedAtBefore(cutoff);
    }
}
