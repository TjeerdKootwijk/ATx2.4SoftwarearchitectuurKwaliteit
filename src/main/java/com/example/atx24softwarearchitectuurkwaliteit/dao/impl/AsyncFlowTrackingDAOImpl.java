package com.example.atx24softwarearchitectuurkwaliteit.dao.impl;

import com.example.atx24softwarearchitectuurkwaliteit.dao.AsyncFlowTrackingDAO;
import com.example.atx24softwarearchitectuurkwaliteit.dao.jpa.AsyncFlowTrackingJpaRepository;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.AsyncFlowTrackingEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA-backed implementatie van {@link AsyncFlowTrackingDAO}.
 */
@Repository
public class AsyncFlowTrackingDAOImpl implements AsyncFlowTrackingDAO {

    private final AsyncFlowTrackingJpaRepository jpaRepository;

    public AsyncFlowTrackingDAOImpl(AsyncFlowTrackingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AsyncFlowTrackingEntity save(AsyncFlowTrackingEntity entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public List<AsyncFlowTrackingEntity> findByStatus(String status, int limit) {
        return jpaRepository.findByStatusOrderBySubmittedAtAsc(status, PageRequest.of(0, limit));
    }

    @Override
    public void delete(AsyncFlowTrackingEntity entity) {
        jpaRepository.delete(entity);
    }
}
