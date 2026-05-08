package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

/**
 * Polling job that runs periodically to fetch missed appointment events
 * This serves as a fallback mechanism if webhooks were missed
 */
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

    /**
     * Run polling every 5 minutes
     * Fetch appointments from OpenMRS REST API for each tenant
     * Check for new/updated appointments since last poll
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)  // 5 minutes, start after 1 minute
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

    /**
     * Poll appointments for a specific tenant
     * Fetches since lastPolledAt timestamp
     */
    private void pollTenantAppointments(TenantConfiguration tenant) {
        String tenantId = tenant.getTenantId();
        LocalDateTime lastPolled = tenantService.getLastPolledAt(tenantId);

        logger.debug("Polling appointments for tenant {} since: {}", tenantId, lastPolled);

        // In production, this would call OpenMRS REST API
        // Example: GET /openmrs/ws/rest/v1/appointments?v=full&since=2026-05-08T10:00:00

        // For now, simulate polling
        simulatePollingForTenant(tenant, lastPolled);

        // Update lastPolledAt
        tenantService.updateLastPolledAt(tenantId, LocalDateTime.now());
        logger.debug("Updated lastPolledAt for tenant {}", tenantId);
    }

    /**
     * Simulate polling for testing
     * In production, replace with actual OpenMRS REST API call
     */
    private void simulatePollingForTenant(TenantConfiguration tenant, LocalDateTime lastPolled) {
        // This is where you would:
        // 1. Call OpenMRS REST API with since parameter
        // 2. Parse response for appointment changes
        // 3. For each appointment, check idempotency
        // 4. Publish new appointments to RabbitMQ

        logger.debug("Simulated polling for tenant: {} since {}", tenant.getTenantId(), lastPolled);
    }

    /**
     * Helper method to create AppointmentChangedEvent from API response
     */
    private AppointmentChangedEvent createEventFromApiResponse(String tenantId, String appointmentData) {
        AppointmentChangedEvent event = new AppointmentChangedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTenantId(tenantId);
        event.setSource("POLLING");
        // Parse appointmentData and populate event fields
        return event;
    }

    /**
     * Publish event if not already processed
     */
    private void publishEventIfNew(AppointmentChangedEvent event) {
        if (!idempotencyService.isEventProcessed(event.getEventId())) {
            idempotencyService.markEventAsProcessed(event.getEventId());
            rabbitTemplate.convertAndSend(APPOINTMENT_EVENTS_EXCHANGE, APPOINTMENT_ROUTING_KEY, event);
            logger.info("Published polled appointment event: {}", event.getEventId());
        } else {
            logger.debug("Appointment event already processed (skipping): {}", event.getEventId());
        }
    }
}
