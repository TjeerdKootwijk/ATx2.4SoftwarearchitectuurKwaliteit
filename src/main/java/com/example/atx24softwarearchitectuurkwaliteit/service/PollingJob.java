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
import java.util.List;

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
            String credentials = tenant.getOpenMrsUsername() + ":" + tenant.getOpenMrsPassword();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encoded);
            headers.set("Accept", "application/json");

            // Sessie starten
            String sessionUrl = tenant.getOpenMrsBaseUrl() + "/ws/rest/v1/session";
            HttpEntity<String> sessionEntity = new HttpEntity<>(headers);

            ResponseEntity<String> sessionResponse = restTemplate.exchange(
                    sessionUrl, HttpMethod.GET, sessionEntity, String.class
            );

            // JSESSIONID cookie veilig ophalen
            String jsessionId = null;
            List<String> cookies = sessionResponse.getHeaders().get("Set-Cookie");

            if (cookies != null) {
                for (String cookie : cookies) {
                    if (cookie.startsWith("JSESSIONID=")) {
                        jsessionId = cookie.split(";")[0];
                        break;
                    }
                }
            }

            logger.info("OpenMRS sessie gestart voor tenant {}, JSESSIONID: {}",
                    tenantId, jsessionId != null ? "verkregen" : "niet gevonden");

            // Appointments ophalen
            HttpHeaders appointmentHeaders = new HttpHeaders();
            appointmentHeaders.set("Authorization", "Basic " + encoded);
            appointmentHeaders.set("Accept", "application/json");
            if (jsessionId != null) {
                appointmentHeaders.set("Cookie", jsessionId);
            }

            HttpEntity<String> appointmentEntity = new HttpEntity<>(appointmentHeaders);
            String appointmentsUrl = tenant.getOpenMrsBaseUrl() + "/ws/rest/v1/appointments?v=full";

            ResponseEntity<String> response = restTemplate.exchange(
                    appointmentsUrl, HttpMethod.GET, appointmentEntity, String.class
            );

            logger.info("Appointments opgehaald voor tenant {}: status {}",
                    tenantId, response.getStatusCode());
            logger.debug("Response body: {}", response.getBody());

        } catch (Exception e) {
            logger.error("Kon OpenMRS niet bereiken voor tenant {}: {}", tenantId, e.getMessage(), e);
        }

        tenantService.updateLastPolledAt(tenantId, LocalDateTime.now());
    }
}