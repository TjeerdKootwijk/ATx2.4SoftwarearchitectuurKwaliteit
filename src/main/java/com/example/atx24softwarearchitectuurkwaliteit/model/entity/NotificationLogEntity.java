package com.example.atx24softwarearchitectuurkwaliteit.model.entity;

import com.example.atx24softwarearchitectuurkwaliteit.dao.listener.AuditEntityListener;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores meta-information about sent notifications for auditing and invoice verification.
 *
 * NFR11: Contains no directly identifiable patient data or appointment details.
 *        Only stores enough information to verify invoices from messaging providers.
 * NFR10: Patient-related data is automatically deleted within 14 days.
 *        This entity holds only meta-info, retained for up to 1 year.
 */
@Entity
@EntityListeners(AuditEntityListener.class)
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_notification_logs_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_notification_logs_sent_at", columnList = "sent_at")
})
public class NotificationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Internal notification UUID, not linked to any patient record. */
    @Column(name = "notification_id", nullable = false, length = 64)
    private String notificationId;

    /** The organisation (tenant) on whose behalf the notification was sent. */
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    /** Messaging provider used (SWIFTSEND, LEGACYLINK, ASYNCFLOW, SECUREPOST). */
    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    /** Delivery outcome: SUCCESS or FAILED. */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    /** ID returned by the external provider — used for invoice reconciliation. */
    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    /** Number of delivery attempts before this outcome. */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /** Error description when status is FAILED, null on success. */
    @Column(name = "error_message", length = 512)
    private String errorMessage;

    public Long getId() { return id; }

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
