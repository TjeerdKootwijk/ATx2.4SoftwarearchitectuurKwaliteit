package com.example.atx24softwarearchitectuurkwaliteit.model;

public class TenantConfiguration {
    private String tenantId;
    private String organizationName;
    private String openMrsBaseUrl;
    private String openMrsUsername;
    private String openMrsPassword;
    private boolean active;
    private String notificationProvider; // SMS, EMAIL, PUSH, etc.
    private String providerApiKey;
    private String providerSecret;

    public TenantConfiguration() {}

    public TenantConfiguration(String tenantId, String organizationName) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.active = true;
    }

    // Getters and Setters
    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getOpenMrsBaseUrl() {
        return openMrsBaseUrl;
    }

    public void setOpenMrsBaseUrl(String openMrsBaseUrl) {
        this.openMrsBaseUrl = openMrsBaseUrl;
    }

    public String getOpenMrsUsername() {
        return openMrsUsername;
    }

    public void setOpenMrsUsername(String openMrsUsername) {
        this.openMrsUsername = openMrsUsername;
    }

    public String getOpenMrsPassword() {
        return openMrsPassword;
    }

    public void setOpenMrsPassword(String openMrsPassword) {
        this.openMrsPassword = openMrsPassword;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getNotificationProvider() {
        return notificationProvider;
    }

    public void setNotificationProvider(String notificationProvider) {
        this.notificationProvider = notificationProvider;
    }

    public String getProviderApiKey() {
        return providerApiKey;
    }

    public void setProviderApiKey(String providerApiKey) {
        this.providerApiKey = providerApiKey;
    }

    public String getProviderSecret() {
        return providerSecret;
    }

    public void setProviderSecret(String providerSecret) {
        this.providerSecret = providerSecret;
    }
}
