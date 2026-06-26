package com.example.atx24softwarearchitectuurkwaliteit.provider.asyncflow;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.AsyncFlowTrackingEntity;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import com.example.atx24softwarearchitectuurkwaliteit.service.DataService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pollt periodiek de afleverstatus van asynchroon verstuurde AsyncFlow-berichten.
 *
 * AsyncFlow geeft bij versturen alleen 'accepted' terug; de werkelijke uitkomst
 * (Completed/Failed) komt pas later beschikbaar via {@code GET /asyncflow/{trackingId}}.
 * Synchroon wachten in de consumer zou de doorvoer blokkeren, daarom worden
 * geaccepteerde berichten als PENDING vastgelegd en hier ontkoppeld afgehandeld:
 *
 *   - Completed  → notification_logs SUCCESS  + metric success
 *   - Failed     → notification_logs FAILED   + metric failed
 *   - Queued/Processing → opnieuw proberen bij de volgende ronde
 *   - geen eindstatus binnen de tijdslimiet → FAILED (timeout)
 *
 * Activeren/deactiveren via {@code app.asyncflow.poll.enabled}.
 */
@Component
@ConditionalOnProperty(name = "app.asyncflow.poll.enabled", havingValue = "true", matchIfMissing = true)
public class AsyncFlowStatusPoller {

    private static final Logger log = LoggerFactory.getLogger(AsyncFlowStatusPoller.class);

    private static final Set<String> SUCCESS_STATUSES = Set.of("completed", "delivered", "sent");
    private static final Set<String> FAILED_STATUSES  = Set.of("failed", "rejected", "error", "cancelled");

    private final AsyncFlowService asyncFlowService;
    private final DataService dataService;
    private final MeterRegistry meterRegistry;
    private final int batchSize;
    private final long maxAgeMinutes;

    public AsyncFlowStatusPoller(AsyncFlowService asyncFlowService,
                                 DataService dataService,
                                 MeterRegistry meterRegistry,
                                 @Value("${app.asyncflow.poll.batch-size:200}") int batchSize,
                                 @Value("${app.asyncflow.poll.max-age-minutes:30}") long maxAgeMinutes) {
        this.asyncFlowService = asyncFlowService;
        this.dataService = dataService;
        this.meterRegistry = meterRegistry;
        this.batchSize = batchSize;
        this.maxAgeMinutes = maxAgeMinutes;
    }

    @Scheduled(fixedDelayString = "${app.asyncflow.poll.interval-ms:15000}")
    public void pollPendingStatuses() {
        List<AsyncFlowTrackingEntity> pending = dataService.findPendingAsyncFlow(batchSize);
        if (pending.isEmpty()) {
            return;
        }

        log.debug("AsyncFlow status-polling: {} openstaande bericht(en)", pending.size());
        for (AsyncFlowTrackingEntity record : pending) {
            try {
                pollOne(record);
            } catch (Exception e) {
                log.error("Fout bij pollen van AsyncFlow trackingId={}: {}",
                        record.getTrackingId(), e.getMessage(), e);
            }
        }
    }

    private void pollOne(AsyncFlowTrackingEntity record) {
        AsyncFlowStatusResponse response = asyncFlowService.getStatus(record.getTrackingId());

        record.setPollCount(record.getPollCount() + 1);
        record.setLastCheckedAt(LocalDateTime.now());

        if (response == null || response.getStatus() == null) {
            failIfExpired(record, "Afleverstatus kon niet worden opgehaald (timeout)");
            return;
        }

        String status = response.getStatus().toLowerCase(Locale.ROOT);

        if (SUCCESS_STATUSES.contains(status)) {
            finalizeSuccess(record);
        } else if (FAILED_STATUSES.contains(status)) {
            finalizeFailed(record, errorMessage(response));
        } else {
            // Nog Queued/Processing — bij de volgende ronde opnieuw proberen.
            failIfExpired(record, "Geen eindstatus binnen tijdslimiet (laatst gezien: " + response.getStatus() + ")");
        }
    }

    private void failIfExpired(AsyncFlowTrackingEntity record, String reason) {
        boolean expired = Duration.between(record.getSubmittedAt(), LocalDateTime.now())
                .toMinutes() >= maxAgeMinutes;
        if (expired) {
            log.warn("AsyncFlow trackingId={} verlopen na {} min — gemarkeerd als FAILED",
                    record.getTrackingId(), maxAgeMinutes);
            finalizeFailed(record, reason);
        } else {
            dataService.updateAsyncFlowTracking(record);
        }
    }

    private void finalizeSuccess(AsyncFlowTrackingEntity record) {
        log.info("AsyncFlow afgeleverd | trackingId={} | notificationId={}",
                record.getTrackingId(), record.getNotificationId());

        dataService.logNotificationSent(
                record.getNotificationId(), record.getTenantId(),
                ProviderType.ASYNCFLOW, record.getTrackingId(), record.getRetryCount());

        meterRegistry.counter("notifications_sent_total",
                "status", "success", "provider", ProviderType.ASYNCFLOW).increment();

        dataService.deleteAsyncFlowTracking(record);
    }

    private void finalizeFailed(AsyncFlowTrackingEntity record, String errorMessage) {
        log.warn("AsyncFlow definitief mislukt | trackingId={} | reden={}",
                record.getTrackingId(), errorMessage);

        dataService.logNotificationFailed(
                record.getNotificationId(), record.getTenantId(),
                ProviderType.ASYNCFLOW, errorMessage, record.getRetryCount());

        meterRegistry.counter("notifications_sent_total",
                "status", "failed", "provider", ProviderType.ASYNCFLOW).increment();

        dataService.deleteAsyncFlowTracking(record);
    }

    private String errorMessage(AsyncFlowStatusResponse response) {
        if (response.getErrorDetails() != null && !response.getErrorDetails().isBlank()) {
            return response.getErrorDetails();
        }
        return "AsyncFlow status: " + response.getStatus();
    }
}
