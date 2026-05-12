package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collection;

@Service
public class PollingJob {

    private static final Logger logger = LoggerFactory.getLogger(PollingJob.class);
    private static final String APPOINTMENT_EVENTS_EXCHANGE = "appointment.events";
    private static final String APPOINTMENT_ROUTING_KEY = "appointment.changed";

    @Autowired
    private TenantService tenantService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RestTemplate restTemplate;

    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void pollOpenMrsAppointments() {
        logger.info("Starting appointment polling job");

        Collection<TenantConfiguration> activeTenants = tenantService.getAllActiveTenants();

        for (TenantConfiguration tenant : activeTenants) {
            try {
                pollTenantAppointments(tenant);
            } catch (Exception e) {
                logger.error("Error polling appointments for tenant {}: {}", tenant.getTenantId(), e.getMessage());
            }
        }

        logger.info("Completed appointment polling job");
    }

    private void pollTenantAppointments(TenantConfiguration tenant) {
        String tenantId = tenant.getTenantId();

        try {
            // Basic Auth header opbouwen
            String credentials = tenant.getOpenMrsUsername() + ":" + tenant.getOpenMrsPassword();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encoded);
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Eerst sessie starten (vereist door OpenMRS)
            String sessionUrl = tenant.getOpenMrsBaseUrl() + "/ws/rest/v1/session";
            ResponseEntity<String> sessionResponse = restTemplate.exchange(
                    sessionUrl, HttpMethod.GET, entity, String.class
            );

            logger.info("OpenMRS verbinding gelukt voor tenant {}: status {}",
                    tenantId, sessionResponse.getStatusCode());

            // Appointments ophalen
            String appointmentsUrl = tenant.getOpenMrsBaseUrl() + "/ws/rest/v1/appointmentscheduling/appointment?v=full";
            ResponseEntity<String> response = restTemplate.exchange(
                    appointmentsUrl, HttpMethod.GET, entity, String.class
            );

            logger.info("Appointments opgehaald voor tenant {}: status {}",
                    tenantId, response.getStatusCode());
            logger.debug("Response body: {}", response.getBody());

        } catch (Exception e) {
            logger.error("Kon OpenMRS niet bereiken voor tenant {}: {}", tenantId, e.getMessage());
        }

        tenantService.updateLastPolledAt(tenantId, LocalDateTime.now());
    }
}