package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collection;

@Service
public class PollingJob {

    private static final Logger logger = LoggerFactory.getLogger(PollingJob.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TenantService tenantService;

    @Autowired
    private RestTemplate restTemplate;

    @Scheduled(fixedDelay = 300000, initialDelay = 10000)
    public void pollOpenMrsAppointments() {
        logger.info("=== Starting appointment polling job ===");

        Collection<TenantConfiguration> activeTenants = tenantService.getAllActiveTenants();
        logger.info("Aantal actieve tenants: {}", activeTenants.size());

        for (TenantConfiguration tenant : activeTenants) {
            pollTenantAppointments(tenant);
        }

        logger.info("=== Completed appointment polling job ===");
    }

    private void pollTenantAppointments(TenantConfiguration tenant) {
        String tenantId = tenant.getTenantId();

        try {
            String credentials = tenant.getOpenMrsUsername() + ":" + tenant.getOpenMrsPassword();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

            String appointmentsUrl = tenant.getOpenMrsBaseUrl() + "/ws/rest/v1/appointments?v=full";

            logger.info("Polling tenant {} → URL: {}", tenantId, appointmentsUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encoded);
            headers.set("Accept", "application/json");

            ResponseEntity<String> response = restTemplate.exchange(
                    appointmentsUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            logger.info("Status tenant {}: {}", tenantId, response.getStatusCode());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                processAppointmentsJson(response.getBody(), tenantId);
            }

        } catch (Exception e) {
            logger.error("Fout bij tenant {}: {}", tenantId, e.getMessage(), e);
        }
    }

    private void processAppointmentsJson(String jsonBody, String tenantId) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode appointments = root.isArray() ? root : root.path("results");

            logger.info("Tenant {} → Totaal {} afspraken opgehaald uit OpenMRS", tenantId, appointments.size());

            int relevanteCount = 0;
            LocalDateTime now = LocalDateTime.now();

            for (JsonNode app : appointments) {
                long startMillis = app.path("startDateTime").asLong(0);
                if (startMillis == 0) continue;

                LocalDateTime appointmentTime = Instant.ofEpochMilli(startMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();

                // Filter: Alleen afspraken van nu tot max 7 dagen in de toekomst
                if (appointmentTime.isAfter(now.minusHours(2)) && appointmentTime.isBefore(now.plusDays(7))) {
                    relevanteCount++;

                    String uuid = app.path("uuid").asText();
                    String patientName = app.path("patient").path("name").asText("Onbekend");
                    String status = app.path("status").asText("N/A");

                    String formattedTime = appointmentTime.toString();

                    logger.info("✅ RELEVANTE AFSPRAAK | Patiënt: {} | Tijd: {} | Status: {} | UUID: {}",
                            patientName, formattedTime, status, uuid);
                }
            }

            logger.info("Tenant {} → {} relevante afspraken gevonden (binnen nu + 7 dagen)",
                    tenantId, relevanteCount);

        } catch (Exception e) {
            logger.error("Fout bij verwerken JSON voor tenant {}: {}", tenantId, e.getMessage(), e);
        }
    }
}