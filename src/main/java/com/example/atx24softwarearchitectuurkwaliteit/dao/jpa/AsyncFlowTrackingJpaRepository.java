package com.example.atx24softwarearchitectuurkwaliteit.dao.jpa;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.AsyncFlowTrackingEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsyncFlowTrackingJpaRepository extends JpaRepository<AsyncFlowTrackingEntity, Long> {

    /** Haalt een batch tracking-records met de gegeven status op, oudste eerst. */
    List<AsyncFlowTrackingEntity> findByStatusOrderBySubmittedAtAsc(String status, Pageable pageable);
}
