package com.example.atx24softwarearchitectuurkwaliteit.dao.jpa;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.ProcessedEventEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, Long> {

    boolean existsByEventId(String eventId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ProcessedEventEntity e WHERE e.processedAt < :cutoff")
    void deleteByProcessedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
