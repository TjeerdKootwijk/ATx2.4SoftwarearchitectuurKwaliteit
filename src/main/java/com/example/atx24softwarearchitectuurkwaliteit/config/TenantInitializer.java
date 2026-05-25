package com.example.atx24softwarearchitectuurkwaliteit.config;

import com.example.atx24softwarearchitectuurkwaliteit.fhir.OpenMrsCompatibilityChecker;
import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.example.atx24softwarearchitectuurkwaliteit.service.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registreert bij opstart automatisch een tenant op basis van env vars.
 *
 * Vereiste env vars:
 *   OPENMRS_TENANT_ID            Unieke tenant identifier
 *   OPENMRS_BASE_URL             Basis-URL van de OpenMRS instantie (of fake-openmrs)
 *   OPENMRS_USERNAME             OpenMRS gebruikersnaam (Basic Auth)
 *   OPENMRS_PASSWORD             OpenMRS wachtwoord    (Basic Auth)
 *   OPENMRS_NOTIFICATION_PROVIDER  Provider: SWIFTSEND / LEGACYLINK / ASYNCFLOW / SECUREPOST
 */
@Component
public class TenantInitializer {

    private static final Logger log = LoggerFactory.getLogger(TenantInitializer.class);

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

    private final TenantService tenantService;
    private final OpenMrsCompatibilityChecker compatibilityChecker;

    public TenantInitializer(TenantService tenantService, OpenMrsCompatibilityChecker compatibilityChecker) {
        this.tenantService = tenantService;
        this.compatibilityChecker = compatibilityChecker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerTenants() {
        TenantConfiguration config = new TenantConfiguration(tenantId, organizationName);
        config.setOpenMrsBaseUrl(baseUrl);
        config.setOpenMrsUsername(username);
        config.setOpenMrsPassword(password);
        config.setNotificationProvider(notificationProvider);
        config.setActive(true);

        tenantService.registerTenant(config);

        log.info("Tenant registered: id={} | org={} | url={} | provider={}",
                tenantId, organizationName, baseUrl, notificationProvider);

        compatibilityChecker.check(config);
    }
}
