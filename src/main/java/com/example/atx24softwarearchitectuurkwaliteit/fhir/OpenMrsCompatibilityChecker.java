package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Verifies at tenant-registration time that an OpenMRS instance is reachable
 * and exposes the Appointments endpoint required by this module (NFR4).
 *
 * Checks performed:
 *   1. /ws/rest/v1/session   — verifies Basic Auth credentials and base connectivity
 *   2. /ws/rest/v1/appointments?limit=1 — verifies the Appointments Module is installed
 *
 * A failed check logs a warning but does NOT block startup (NFR7: the module must
 * remain operational when OpenMRS is temporarily unavailable).
 *
 * Required: OpenMRS 2.7.x+ with the Appointments Module enabled.
 */
@Component
public class OpenMrsCompatibilityChecker {

    private static final Logger log = LoggerFactory.getLogger(OpenMrsCompatibilityChecker.class);

    static final String SESSION_PATH      = "/ws/rest/v1/session";
    static final String APPOINTMENTS_PATH = "/ws/rest/v1/appointments?limit=1";

    private final RestTemplate restTemplate;

    public OpenMrsCompatibilityChecker(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void check(TenantConfiguration tenant) {
        String base = tenant.getOpenMrsBaseUrl();
        HttpHeaders headers = buildAuthHeaders(tenant);

        verifyAuthentication(tenant.getTenantId(), base, headers);
        verifyAppointmentsEndpoint(tenant.getTenantId(), base, headers);
    }

    private void verifyAuthentication(String tenantId, String base, HttpHeaders headers) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    base + SESSION_PATH, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("NFR4 — OpenMRS connectivity OK for tenant {} (session endpoint responded)", tenantId);
            } else {
                log.warn("NFR4 — OpenMRS session endpoint returned {} for tenant {} — check credentials or OpenMRS availability",
                        response.getStatusCode(), tenantId);
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401) {
                log.warn("NFR4 — OpenMRS authentication failed for tenant {} (401 Unauthorized) — verify OPENMRS_USERNAME and OPENMRS_PASSWORD",
                        tenantId);
            } else {
                log.warn("NFR4 — OpenMRS session endpoint returned {} for tenant {}", e.getStatusCode(), tenantId);
            }
        } catch (RestClientException e) {
            log.warn("NFR4 — Cannot reach OpenMRS for tenant {} at {}{}: {} — polling will be retried each cycle",
                    tenantId, base, SESSION_PATH, e.getMessage());
        }
    }

    private void verifyAppointmentsEndpoint(String tenantId, String base, HttpHeaders headers) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    base + APPOINTMENTS_PATH, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("NFR4 — OpenMRS Appointments Module confirmed for tenant {} (OpenMRS 2.7.x+ requirement met)",
                        tenantId);
            } else {
                log.warn("NFR4 — OpenMRS Appointments endpoint returned {} for tenant {}", response.getStatusCode(), tenantId);
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                log.warn("NFR4 — OpenMRS Appointments endpoint not found (404) for tenant {} — " +
                         "the Appointments Module may not be installed. " +
                         "Required: OpenMRS 2.7.x+ with the Appointments Module enabled (openmrs-module-appointments).",
                        tenantId);
            } else {
                log.warn("NFR4 — OpenMRS Appointments endpoint returned {} for tenant {}", e.getStatusCode(), tenantId);
            }
        } catch (RestClientException e) {
            log.warn("NFR4 — Cannot reach OpenMRS Appointments endpoint for tenant {} at {}{}: {}",
                    tenantId, base, APPOINTMENTS_PATH, e.getMessage());
        }
    }

    private HttpHeaders buildAuthHeaders(TenantConfiguration tenant) {
        String credentials = tenant.getOpenMrsUsername() + ":" + tenant.getOpenMrsPassword();
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);
        headers.set("Accept", "application/json");
        return headers;
    }
}
