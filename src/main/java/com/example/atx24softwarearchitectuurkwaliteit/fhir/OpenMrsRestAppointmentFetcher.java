package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * SRP: haalt ruwe afspraakdata op uit OpenMRS via de legacy REST API.
 *      Geen mapping, geen validatie — alleen HTTP ophalen.
 *
 * OpenMRS ondersteunt geen FHIR endpoint; de FHIR-verwerking vindt intern plaats
 * in FhirR4AppointmentMapper en FhirAppointmentValidator.
 */
@Component
public class OpenMrsRestAppointmentFetcher implements AppointmentFetcher {

    private static final Logger log = LoggerFactory.getLogger(OpenMrsRestAppointmentFetcher.class);
    private static final String APPOINTMENTS_PATH = "/ws/rest/v1/appointments?v=full";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenMrsRestAppointmentFetcher(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<JsonNode> fetchAppointments(TenantConfiguration tenant) {
        try {
            HttpHeaders headers = buildAuthHeaders(tenant);
            String url = tenant.getOpenMrsBaseUrl() + APPOINTMENTS_PATH;

            log.info("Fetching appointments for tenant {} → {}", tenant.getTenantId(), url);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("No data received from OpenMRS for tenant {}", tenant.getTenantId());
                return List.of();
            }

            return parseResults(response.getBody(), tenant.getTenantId());

        } catch (Exception e) {
            log.error("Failed to fetch appointments for tenant {}: {}", tenant.getTenantId(), e.getMessage(), e);
            return List.of();
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

    private List<JsonNode> parseResults(String responseBody, String tenantId) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root.isArray() ? root : root.path("results");

        List<JsonNode> appointments = new ArrayList<>();
        results.forEach(appointments::add);

        log.info("Fetched {} raw appointments from OpenMRS for tenant {}", appointments.size(), tenantId);
        return appointments;
    }
}
