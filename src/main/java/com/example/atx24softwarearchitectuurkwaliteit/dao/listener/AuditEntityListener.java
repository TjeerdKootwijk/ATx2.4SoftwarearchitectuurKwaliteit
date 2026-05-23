package com.example.atx24softwarearchitectuurkwaliteit.dao.listener;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.ProcessedEventEntity;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.TenantEntity;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA entity listener that automatically logs every database write operation
 * (INSERT, UPDATE, DELETE) for all audited entities.
 *
 * Attach to an entity via {@code @EntityListeners(AuditEntityListener.class)}.
 *
 * JPA restriction: only ONE method per callback annotation (@PostPersist,
 * @PostUpdate, @PostRemove) is allowed per listener class.
 * Each method uses instanceof to handle all entity types.
 *
 * Log levels:
 *   INFO  — normal writes (insert, update)
 *   WARN  — deletes (data loss, worth flagging)
 */
public class AuditEntityListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEntityListener.class);

    // ── INSERT ────────────────────────────────────────────────────────────────

    @PostPersist
    public void afterInsert(Object entity) {
        if (entity instanceof TenantEntity t) {
            log.info("[DB INSERT] tenants | tenant_id='{}' | org='{}' | provider='{}' | active={}",
                    t.getTenantId(), t.getOrganizationName(), t.getNotificationProvider(), t.isActive());

        } else if (entity instanceof ProcessedEventEntity e) {
            log.info("[DB INSERT] processed_events | id={} | event_id='{}' | processed_at={}",
                    e.getId(), e.getEventId(), e.getProcessedAt());

        } else if (entity instanceof NotificationLogEntity n) {
            if ("SUCCESS".equals(n.getStatus())) {
                log.info("[DB INSERT] notification_logs | id={} | notification_id='{}' | tenant='{}' | provider='{}' | status={} | provider_msg_id='{}' | retries={}",
                        n.getId(), n.getNotificationId(), n.getTenantId(),
                        n.getProvider(), n.getStatus(), n.getProviderMessageId(), n.getRetryCount());
            } else {
                log.info("[DB INSERT] notification_logs | id={} | notification_id='{}' | tenant='{}' | provider='{}' | status={} | retries={} | error='{}'",
                        n.getId(), n.getNotificationId(), n.getTenantId(),
                        n.getProvider(), n.getStatus(), n.getRetryCount(), n.getErrorMessage());
            }
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @PostUpdate
    public void afterUpdate(Object entity) {
        if (entity instanceof TenantEntity t) {
            log.info("[DB UPDATE] tenants | tenant_id='{}' | org='{}' | provider='{}' | active={} | last_polled_at={}",
                    t.getTenantId(), t.getOrganizationName(),
                    t.getNotificationProvider(), t.isActive(), t.getLastPolledAt());
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @PostRemove
    public void afterDelete(Object entity) {
        if (entity instanceof TenantEntity t) {
            log.warn("[DB DELETE] tenants | tenant_id='{}' | org='{}'",
                    t.getTenantId(), t.getOrganizationName());

        } else if (entity instanceof ProcessedEventEntity e) {
            log.warn("[DB DELETE] processed_events | id={} | event_id='{}'",
                    e.getId(), e.getEventId());

        } else if (entity instanceof NotificationLogEntity n) {
            log.warn("[DB DELETE] notification_logs | id={} | tenant='{}' | sent_at={}",
                    n.getId(), n.getTenantId(), n.getSentAt());
        }
    }
}
