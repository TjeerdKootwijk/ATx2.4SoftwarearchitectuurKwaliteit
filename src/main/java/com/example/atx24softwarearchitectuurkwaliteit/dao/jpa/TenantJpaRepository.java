package com.example.atx24softwarearchitectuurkwaliteit.dao.jpa;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.TenantEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TenantJpaRepository extends JpaRepository<TenantEntity, String> {

    List<TenantEntity> findByActiveTrue();

    @Modifying
    @Transactional
    @Query("UPDATE TenantEntity t SET t.lastPolledAt = :timestamp, t.updatedAt = :timestamp WHERE t.tenantId = :tenantId")
    void updateLastPolledAt(@Param("tenantId") String tenantId, @Param("timestamp") LocalDateTime timestamp);
}
