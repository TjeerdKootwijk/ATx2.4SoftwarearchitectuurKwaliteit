package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.dao.AsyncFlowTrackingDAO;
import com.example.atx24softwarearchitectuurkwaliteit.dao.NotificationLogDAO;
import com.example.atx24softwarearchitectuurkwaliteit.dao.ProcessedEventDAO;
import com.example.atx24softwarearchitectuurkwaliteit.dao.TenantDAO;
import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.AsyncFlowTrackingEntity;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.ProcessedEventEntity;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.TenantEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Central persistence service that orchestrates TenantDAO, ProcessedEventDAO,
 * and NotificationLogDAO. Provides a single access point for all database
 * operations used by the rest of the application.
 *
 * SOLID compliance:
 *   S – single responsibility: persistence orchestration only
 *   O – open for extension via new DAO interfaces without modifying consumers
 *   D – depends on DAO interfaces, never on JPA implementations directly
 */
@Service
@Transactional
public class DataService {

    private static final Logger logger = LoggerFactory.getLogger(DataService.class);

    private final TenantDAO tenantDAO;
    private final ProcessedEventDAO processedEventDAO;
    private final NotificationLogDAO notificationLogDAO;
    private final AsyncFlowTrackingDAO asyncFlowTrackingDAO;

    public DataService(TenantDAO tenantDAO,
                       ProcessedEventDAO processedEventDAO,
                       NotificationLogDAO notificationLogDAO,
                       AsyncFlowTrackingDAO asyncFlowTrackingDAO) {
        this.tenantDAO = tenantDAO;
        this.processedEventDAO = processedEventDAO;
        this.notificationLogDAO = notificationLogDAO;
        this.asyncFlowTrackingDAO = asyncFlowTrackingDAO;
    }

    // ── Tenant operations ────────────────────────────────────────────────────

    /**
     * Persists a tenant configuration to the database.
     * Called on startup by {@link com.example.atx24softwarearchitectuurkwaliteit.config.TenantInitializer}.
     */
    public void saveTenant(TenantConfiguration config) {
        boolean exists = tenantDAO.findByTenantId(config.getTenantId()).isPresent();
        TenantEntity entity = toEntity(config);
        tenantDAO.save(entity);
        if (exists) {
            logger.info("Tenant updated in database: id='{}' | org='{}'",
                    config.getTenantId(), config.getOrganizationName());
        } else {
            logger.info("New tenant registered in database: id='{}' | org='{}' | provider='{}'",
                    config.getTenantId(), config.getOrganizationName(), config.getNotificationProvider());
        }
    }

    @Transactional(readOnly = true)
    public Optional<TenantConfiguration> findTenant(String tenantId) {
        return tenantDAO.findByTenantId(tenantId).map(this::toModel);
    }

    @Transactional(readOnly = true)
    public Collection<TenantConfiguration> findAllActiveTenants() {
        return tenantDAO.findAllActive().stream().map(this::toModel).toList();
    }

    public void updateLastPolledAt(String tenantId, LocalDateTime timestamp) {
        tenantDAO.updateLastPolledAt(tenantId, timestamp);
    }

    @Transactional(readOnly = true)
    public LocalDateTime getLastPolledAt(String tenantId) {
        return tenantDAO.findByTenantId(tenantId)
                .map(TenantEntity::getLastPolledAt)
                .orElse(LocalDateTime.now().minusHours(24));
    }

    @Transactional(readOnly = true)
    public boolean isTenantValid(String tenantId) {
        return tenantDAO.findByTenantId(tenantId)
                .map(TenantEntity::isActive)
                .orElse(false);
    }

    // ── Idempotency operations ────────────────────────────────────────────────

    /**
     * Returns true if the event was already processed, preventing duplicate notifications.
     * Backed by the {@code processed_events} table instead of an in-memory set,
     * so idempotency survives application restarts.
     */
    @Transactional(readOnly = true)
    public boolean isEventProcessed(String eventId) {
        return processedEventDAO.existsByEventId(eventId);
    }

    public void markEventAsProcessed(String eventId) {
        ProcessedEventEntity entity = new ProcessedEventEntity(eventId, LocalDateTime.now());
        processedEventDAO.save(entity);
        logger.debug("Idempotency record saved: event_id='{}' — future duplicates will be ignored", eventId);
    }

    // ── Notification logging operations ──────────────────────────────────────

    /**
     * Logs a successful notification delivery.
     * Stores only meta-information — no patient data (NFR11).
     */
    public void logNotificationSent(String notificationId, String tenantId,
                                    String provider, String providerMessageId, int retryCount) {
        NotificationLogEntity log = new NotificationLogEntity();
        log.setNotificationId(notificationId);
        log.setTenantId(tenantId);
        log.setProvider(provider);
        log.setStatus("SUCCESS");
        log.setSentAt(LocalDateTime.now());
        log.setProviderMessageId(providerMessageId);
        log.setRetryCount(retryCount);
        notificationLogDAO.save(log);
    }

    /**
     * Logs a failed notification delivery attempt.
     * Stores only meta-information — no patient data (NFR11).
     */
    public void logNotificationFailed(String notificationId, String tenantId,
                                      String provider, String errorMessage, int retryCount) {
        NotificationLogEntity log = new NotificationLogEntity();
        log.setNotificationId(notificationId);
        log.setTenantId(tenantId);
        log.setProvider(provider);
        log.setStatus("FAILED");
        log.setSentAt(LocalDateTime.now());
        log.setErrorMessage(errorMessage);
        log.setRetryCount(retryCount);
        notificationLogDAO.save(log);
    }

    // ── AsyncFlow afleverstatus-tracking ─────────────────────────────────────

    /**
     * Registreert een door AsyncFlow geaccepteerd (maar nog niet afgeleverd) bericht,
     * zodat de {@code AsyncFlowStatusPoller} de definitieve status later kan ophalen.
     * Slaat geen patiëntdata of berichtinhoud op (NFR11).
     */
    public void saveAsyncFlowPending(String trackingId, String notificationId,
                                     String tenantId, int retryCount) {
        AsyncFlowTrackingEntity entity = new AsyncFlowTrackingEntity(
                trackingId, notificationId, tenantId, retryCount, LocalDateTime.now());
        asyncFlowTrackingDAO.save(entity);
        logger.debug("AsyncFlow tracking opgeslagen: trackingId='{}' (PENDING)", trackingId);
    }

    @Transactional(readOnly = true)
    public List<AsyncFlowTrackingEntity> findPendingAsyncFlow(int limit) {
        return asyncFlowTrackingDAO.findByStatus("PENDING", limit);
    }

    /** Verwijdert een tracking-record nadat de definitieve uitkomst is verwerkt. */
    public void deleteAsyncFlowTracking(AsyncFlowTrackingEntity entity) {
        asyncFlowTrackingDAO.delete(entity);
    }

    /** Werkt een tracking-record bij (bijv. poll_count / last_checked_at) zonder het te verwijderen. */
    public void updateAsyncFlowTracking(AsyncFlowTrackingEntity entity) {
        asyncFlowTrackingDAO.save(entity);
    }

    // ── Scheduled cleanup ────────────────────────────────────────────────────

    /**
     * Runs nightly at 02:00 to purge expired records.
     *
     * NFR10: patient-related data deleted within 14 days after handling.
     *        Processed events are purged after 30 days as a safe margin.
     * NFR11: notification meta-information retained for a maximum of 1 year.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredData() {
        logger.info("Scheduled cleanup started (runs nightly at 02:00)");

        // NFR10: patient-related data must be deleted within 14 days after handling
        LocalDateTime eventCutoff = LocalDateTime.now().minusDays(14);
        logger.info("Deleting processed_events older than 14 days (before {}) — NFR10", eventCutoff);
        processedEventDAO.deleteProcessedBefore(eventCutoff);

        LocalDateTime logCutoff = LocalDateTime.now().minusYears(1);
        logger.info("Deleting notification_logs older than 1 year (before {}) — NFR11 retention policy", logCutoff);
        notificationLogDAO.deleteSentAtBefore(logCutoff);

        logger.info("Scheduled cleanup complete");
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private TenantEntity toEntity(TenantConfiguration config) {
        TenantEntity entity = new TenantEntity();
        entity.setTenantId(config.getTenantId());
        entity.setOrganizationName(config.getOrganizationName());
        entity.setOpenMrsBaseUrl(config.getOpenMrsBaseUrl());
        entity.setOpenMrsUsername(config.getOpenMrsUsername());
        entity.setOpenMrsPassword(config.getOpenMrsPassword());
        entity.setActive(config.isActive());
        entity.setNotificationProvider(config.getNotificationProvider());
        entity.setProviderApiKey(config.getProviderApiKey());
        entity.setProviderSecret(config.getProviderSecret());
        entity.setTimezone(config.getTimezone());
        return entity;
    }

    private TenantConfiguration toModel(TenantEntity entity) {
        TenantConfiguration config = new TenantConfiguration(entity.getTenantId(), entity.getOrganizationName());
        config.setOpenMrsBaseUrl(entity.getOpenMrsBaseUrl());
        config.setOpenMrsUsername(entity.getOpenMrsUsername());
        config.setOpenMrsPassword(entity.getOpenMrsPassword());
        config.setActive(entity.isActive());
        config.setNotificationProvider(entity.getNotificationProvider());
        config.setProviderApiKey(entity.getProviderApiKey());
        config.setProviderSecret(entity.getProviderSecret());
        config.setTimezone(entity.getTimezone());
        return config;
    }
}
