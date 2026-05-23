package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Manages tenant (OpenMRS organisation) lifecycle.
 * Delegates all persistence to {@link DataService} — no in-memory state held here.
 */
@Service
public class TenantService {

    private static final Logger logger = LoggerFactory.getLogger(TenantService.class);

    private final DataService dataService;

    public TenantService(DataService dataService) {
        this.dataService = dataService;
    }

    /** Persists a new tenant configuration. Called on application startup. */
    public void registerTenant(TenantConfiguration config) {
        dataService.saveTenant(config);
        logger.info("Tenant registered: {}", config.getTenantId());
    }

    /** Returns the tenant configuration, or null when the tenant is unknown. */
    public TenantConfiguration getTenantConfiguration(String tenantId) {
        return dataService.findTenant(tenantId).orElse(null);
    }

    /** Returns all tenants that are currently marked as active. */
    public Collection<TenantConfiguration> getAllActiveTenants() {
        return dataService.findAllActiveTenants();
    }

    /** Updates the timestamp of the most recent successful poll for a tenant. */
    public void updateLastPolledAt(String tenantId, LocalDateTime timestamp) {
        dataService.updateLastPolledAt(tenantId, timestamp);
        logger.debug("lastPolledAt updated for tenant {}: {}", tenantId, timestamp);
    }

    /**
     * Returns the timestamp of the last successful poll.
     * Falls back to 24 hours ago when no record exists, ensuring no appointments are missed.
     */
    public LocalDateTime getLastPolledAt(String tenantId) {
        return dataService.getLastPolledAt(tenantId);
    }

    /** Returns true when the tenant exists and is active. */
    public boolean isTenantValid(String tenantId) {
        return dataService.isTenantValid(tenantId);
    }
}
