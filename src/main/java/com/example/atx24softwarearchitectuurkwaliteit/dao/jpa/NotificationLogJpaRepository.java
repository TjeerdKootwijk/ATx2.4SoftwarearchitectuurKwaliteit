package com.example.atx24softwarearchitectuurkwaliteit.dao.jpa;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationLogJpaRepository extends JpaRepository<NotificationLogEntity, Long> {

    List<NotificationLogEntity> findByTenantId(String tenantId);

    @Modifying
    @Transactional
    @Query("DELETE FROM NotificationLogEntity n WHERE n.sentAt < :cutoff")
    void deleteBySentAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
