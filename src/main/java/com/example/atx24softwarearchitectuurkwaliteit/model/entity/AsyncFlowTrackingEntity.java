package com.example.atx24softwarearchitectuurkwaliteit.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Houdt nog niet afgeronde AsyncFlow-berichten bij.
 *
 * AsyncFlow is asynchroon: bij versturen is een bericht alleen 'accepted'. De
 * definitieve afleverstatus (Completed/Failed) wordt door de
 * {@code AsyncFlowStatusPoller} later opgehaald via de trackingId, waarna de
 * uitkomst naar {@code notification_logs} wordt geschreven en deze rij verdwijnt.
 *
 * NFR11: bevat geen patiëntgegevens of berichtinhoud — alleen meta-informatie.
 */
@Entity
@Table(name = "asyncflow_tracking", indexes = {
        @Index(name = "idx_asyncflow_tracking_status", columnList = "status")
})
public class AsyncFlowTrackingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID dat AsyncFlow teruggaf (ASF-...), gebruikt om de status op te vragen. */
    @Column(name = "tracking_id", nullable = false, length = 128, unique = true)
    private String trackingId;

    /** Interne notificatie-UUID, niet gekoppeld aan een patiëntdossier. */
    @Column(name = "notification_id", nullable = false, length = 64)
    private String notificationId;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    /** Interne pollingstatus: PENDING zolang nog niet afgerond. */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** Aantal transport-pogingen (RabbitMQ) vóór acceptatie door AsyncFlow. */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /** Aantal keren dat de afleverstatus is opgevraagd. */
    @Column(name = "poll_count", nullable = false)
    private int pollCount;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    public AsyncFlowTrackingEntity() {
    }

    public AsyncFlowTrackingEntity(String trackingId, String notificationId, String tenantId,
                                   int retryCount, LocalDateTime submittedAt) {
        this.trackingId = trackingId;
        this.notificationId = notificationId;
        this.tenantId = tenantId;
        this.status = "PENDING";
        this.retryCount = retryCount;
        this.pollCount = 0;
        this.submittedAt = submittedAt;
    }

    public Long getId() { return id; }

    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public int getPollCount() { return pollCount; }
    public void setPollCount(int pollCount) { this.pollCount = pollCount; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(LocalDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
}
