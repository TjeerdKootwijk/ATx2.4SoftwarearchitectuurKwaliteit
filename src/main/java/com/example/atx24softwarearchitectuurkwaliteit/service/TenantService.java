package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;

@Service
public class TenantService {

    private static final Logger logger = LoggerFactory.getLogger(TenantService.class);
    
    // In-memory tenant store (in production, use database)
    private final Map<String, TenantConfiguration> tenants = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastPolledAt = new ConcurrentHashMap<>();

    /**
     * Register a new tenant
     */
    public void registerTenant(TenantConfiguration config) {
        tenants.put(config.getTenantId(), config);
        lastPolledAt.put(config.getTenantId(), LocalDateTime.now());
        logger.info("Tenant registered: {}", config.getTenantId());
    }

    /**
     * Get tenant configuration
     */
    public TenantConfiguration getTenantConfiguration(String tenantId) {
        return tenants.getOrDefault(tenantId, null);
    }

    /**
     * Get all active tenants
     */
    public Collection<TenantConfiguration> getAllActiveTenants() {
        return tenants.values().stream()
            .filter(TenantConfiguration::isActive)
            .toList();
    }

    /**
     * Update last polled timestamp for tenant
     */
    public void updateLastPolledAt(String tenantId, LocalDateTime timestamp) {
        lastPolledAt.put(tenantId, timestamp);
        logger.debug("Updated lastPolledAt for tenant {}: {}", tenantId, timestamp);
    }

    /**
     * Get last polled timestamp for tenant
     */
    public LocalDateTime getLastPolledAt(String tenantId) {
        return lastPolledAt.getOrDefault(tenantId, LocalDateTime.now().minusHours(24));
    }

    /**
     * Validate tenant exists and is active
     */
    public boolean isTenantValid(String tenantId) {
        TenantConfiguration config = getTenantConfiguration(tenantId);
        return config != null && config.isActive();
    }
}
