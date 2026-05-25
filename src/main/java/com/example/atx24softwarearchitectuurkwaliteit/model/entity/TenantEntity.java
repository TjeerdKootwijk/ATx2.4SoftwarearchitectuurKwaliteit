package com.example.atx24softwarearchitectuurkwaliteit.model.entity;

import com.example.atx24softwarearchitectuurkwaliteit.config.AesEncryptionConverter;
import com.example.atx24softwarearchitectuurkwaliteit.dao.listener.AuditEntityListener;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenants")
@EntityListeners(AuditEntityListener.class)
public class TenantEntity {

    @Id
    @Column(name = "tenant_id", length = 128)
    private String tenantId;

    @Column(name = "organization_name", nullable = false, length = 255)
    private String organizationName;

    @Column(name = "openmrs_base_url", nullable = false, length = 512)
    private String openMrsBaseUrl;

    @Column(name = "openmrs_username", nullable = false, length = 128)
    private String openMrsUsername;

    // NFR5: encrypted with AES-256-GCM before storage, decrypted transparently on read
    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "openmrs_password", nullable = false, length = 512)
    private String openMrsPassword;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "notification_provider", nullable = false, length = 32)
    private String notificationProvider;

    // NFR5: encrypted with AES-256-GCM before storage, decrypted transparently on read
    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "provider_api_key", length = 512)
    private String providerApiKey;

    // NFR5: encrypted with AES-256-GCM before storage, decrypted transparently on read
    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "provider_secret", length = 512)
    private String providerSecret;

    @Column(name = "last_polled_at")
    private LocalDateTime lastPolledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (lastPolledAt == null) {
            lastPolledAt = LocalDateTime.now().minusHours(24);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

    public String getOpenMrsBaseUrl() { return openMrsBaseUrl; }
    public void setOpenMrsBaseUrl(String openMrsBaseUrl) { this.openMrsBaseUrl = openMrsBaseUrl; }

    public String getOpenMrsUsername() { return openMrsUsername; }
    public void setOpenMrsUsername(String openMrsUsername) { this.openMrsUsername = openMrsUsername; }

    public String getOpenMrsPassword() { return openMrsPassword; }
    public void setOpenMrsPassword(String openMrsPassword) { this.openMrsPassword = openMrsPassword; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getNotificationProvider() { return notificationProvider; }
    public void setNotificationProvider(String notificationProvider) { this.notificationProvider = notificationProvider; }

    public String getProviderApiKey() { return providerApiKey; }
    public void setProviderApiKey(String providerApiKey) { this.providerApiKey = providerApiKey; }

    public String getProviderSecret() { return providerSecret; }
    public void setProviderSecret(String providerSecret) { this.providerSecret = providerSecret; }

    public LocalDateTime getLastPolledAt() { return lastPolledAt; }
    public void setLastPolledAt(LocalDateTime lastPolledAt) { this.lastPolledAt = lastPolledAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
