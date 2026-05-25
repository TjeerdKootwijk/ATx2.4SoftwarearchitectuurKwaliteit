package com.example.atx24softwarearchitectuurkwaliteit.config;

import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.example.atx24softwarearchitectuurkwaliteit.service.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registreert bij opstart automatisch tenants op basis van env vars.
 *
 * Tenant 1 (verplicht):
 *   OPENMRS_TENANT_ID              Unieke tenant identifier
 *   OPENMRS_BASE_URL               Basis-URL van de OpenMRS instantie (of fake-openmrs)
 *   OPENMRS_USERNAME               OpenMRS gebruikersnaam (Basic Auth)
 *   OPENMRS_PASSWORD               OpenMRS wachtwoord    (Basic Auth)
 *   OPENMRS_NOTIFICATION_PROVIDER  Provider: SWIFTSEND / LEGACYLINK / ASYNCFLOW / SECUREPOST
 *
 * Tenant 2 (optioneel — alleen geregistreerd als OPENMRS2_BASE_URL is ingesteld):
 *   OPENMRS2_TENANT_ID
 *   OPENMRS2_ORGANIZATION_NAME
 *   OPENMRS2_BASE_URL
 *   OPENMRS2_USERNAME
 *   OPENMRS2_PASSWORD
 *   OPENMRS2_NOTIFICATION_PROVIDER
 */
@Component
public class TenantInitializer {

    private static final Logger log = LoggerFactory.getLogger(TenantInitializer.class);

    // ── Tenant 1 ────────────────────────────────────────────────────────────
    @Value("${OPENMRS_TENANT_ID:test-ziekenhuis}")
    private String tenantId;

    @Value("${OPENMRS_ORGANIZATION_NAME:Test Ziekenhuis}")
    private String organizationName;

    @Value("${OPENMRS_BASE_URL:http://localhost:8090}")
    private String baseUrl;

    @Value("${OPENMRS_USERNAME:admin}")
    private String username;

    @Value("${OPENMRS_PASSWORD:admin}")
    private String password;

    @Value("${OPENMRS_NOTIFICATION_PROVIDER:SWIFTSEND}")
    private String notificationProvider;

    // NFR13: IANA timezone for tenant 1 (e.g. Europe/Amsterdam, Africa/Nairobi)
    @Value("${OPENMRS_TIMEZONE:UTC}")
    private String timezone;

    // ── Tenant 2 (optioneel) ─────────────────────────────────────────────────
    @Value("${OPENMRS2_TENANT_ID:}")
    private String tenant2Id;

    @Value("${OPENMRS2_ORGANIZATION_NAME:}")
    private String tenant2OrganizationName;

    @Value("${OPENMRS2_BASE_URL:}")
    private String tenant2BaseUrl;

    @Value("${OPENMRS2_USERNAME:}")
    private String tenant2Username;

    @Value("${OPENMRS2_PASSWORD:}")
    private String tenant2Password;

    @Value("${OPENMRS2_NOTIFICATION_PROVIDER:SWIFTSEND}")
    private String tenant2NotificationProvider;

    @Value("${OPENMRS2_TIMEZONE:UTC}")
    private String tenant2Timezone;

    private final TenantService tenantService;

    public TenantInitializer(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerTenants() {
        // ── Tenant 1 ─────────────────────────────────────────────────────────
        TenantConfiguration config = new TenantConfiguration(tenantId, organizationName);
        config.setOpenMrsBaseUrl(baseUrl);
        config.setOpenMrsUsername(username);
        config.setOpenMrsPassword(password);
        config.setNotificationProvider(notificationProvider);
        config.setTimezone(timezone);
        config.setActive(true);

        tenantService.registerTenant(config);
        log.info("[TENANT] Geregistreerd: id={} | org={} | url={} | provider={} | timezone={}",
                tenantId, organizationName, baseUrl, notificationProvider, timezone);

        // ── Tenant 2 (alleen als OPENMRS2_BASE_URL is geconfigureerd) ────────
        if (tenant2BaseUrl != null && !tenant2BaseUrl.isBlank()) {
            String id   = tenant2Id.isBlank()               ? "real-openmrs"   : tenant2Id;
            String org  = tenant2OrganizationName.isBlank() ? "Real OpenMRS"   : tenant2OrganizationName;

            TenantConfiguration config2 = new TenantConfiguration(id, org);
            config2.setOpenMrsBaseUrl(tenant2BaseUrl);
            config2.setOpenMrsUsername(tenant2Username);
            config2.setOpenMrsPassword(tenant2Password);
            config2.setNotificationProvider(tenant2NotificationProvider);
            config2.setTimezone(tenant2Timezone);
            config2.setActive(true);

            tenantService.registerTenant(config2);
            log.info("[TENANT] Geregistreerd: id={} | org={} | url={} | provider={} | timezone={}",
                    id, org, tenant2BaseUrl, tenant2NotificationProvider, tenant2Timezone);
        } else {
            log.info("[TENANT] Geen tweede tenant geconfigureerd (OPENMRS2_BASE_URL niet ingesteld)");
        }
    }
}
