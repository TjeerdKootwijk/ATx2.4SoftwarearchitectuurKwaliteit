package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.fhir.*;
import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.hl7.fhir.r4.model.Appointment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * SRP: puur een orkestrator — coördineert de stappen maar doet zelf geen HTTP-calls,
 *      mapping, validatie of publicatie.
 *
 * DIP: hangt af van interfaces (AppointmentFetcher, AppointmentMapper, AppointmentEventConverter,
 *      AppointmentValidator, AppointmentEventPublisher), niet van concrete klassen.
 *
 * OCP: nieuwe fetch-, map- of validatie-strategieën kunnen worden ingeplugd
 *      zonder deze klasse aan te passen.
 */
@Service
public class PollingJob {

    private static final Logger log = LoggerFactory.getLogger(PollingJob.class);

    private final TenantService tenantService;
    private final AppointmentFetcher fetcher;
    private final AppointmentMapper mapper;
    private final AppointmentEventConverter converter;
    private final AppointmentValidator validator;
    private final AppointmentEventPublisher publisher;

    public PollingJob(TenantService tenantService,
                      AppointmentFetcher fetcher,
                      AppointmentMapper mapper,
                      AppointmentEventConverter converter,
                      AppointmentValidator validator,
                      AppointmentEventPublisher publisher) {
        this.tenantService = tenantService;
        this.fetcher = fetcher;
        this.mapper = mapper;
        this.converter = converter;
        this.validator = validator;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 10000)
    public void pollOpenMrsAppointments() {
        log.info("=== Starting appointment polling job ===");

        for (TenantConfiguration tenant : tenantService.getAllActiveTenants()) {
            try {
                processTenant(tenant);
            } catch (Exception e) {
                log.error("Unexpected error processing tenant {}: {}", tenant.getTenantId(), e.getMessage(), e);
            }
        }

        log.info("=== Completed appointment polling job ===");
    }

    private void processTenant(TenantConfiguration tenant) {
        String tenantId = tenant.getTenantId();
        LocalDateTime now = LocalDateTime.now();
        int published = 0;

        for (JsonNode node : fetcher.fetchAppointments(tenant)) {
            try {
                if (!isWithinNotificationWindow(node, now)) continue;

                // Stap 1: map OpenMRS JSON → FHIR R4 Appointment (intern, NFR 6 — berichttransformatie)
                Appointment fhirAppointment = mapper.map(node);

                // Stap 2: valideer FHIR R4 object (NFR 6 — berichtontvangst en validatie + ACK)
                ValidationResult validationResult = validator.validate(fhirAppointment);
                if (!validationResult.isValid()) {
                    log.warn("Tenant {} — invalid FHIR appointment skipped: {}", tenantId, validationResult.getErrors());
                    continue;
                }

                // Stap 3: converteer FHIR Appointment → intern event
                AppointmentChangedEvent event = converter.convert(fhirAppointment, tenantId);

                // Stap 4: publiceer naar RabbitMQ (NFR 6 — queueing)
                publisher.publish(event);
                published++;

            } catch (Exception e) {
                log.error("Error processing appointment for tenant {}: {}", tenantId, e.getMessage(), e);
            }
        }

        log.info("Tenant {} → {} appointments published to queue", tenantId, published);
    }

    /**
     * Filter: alleen afspraken die nog niet begonnen zijn en binnen 7 dagen plaatsvinden.
     * Afspraken die al zijn aangevangen krijgen geen notificatie (functionele eis 1).
     */
    private boolean isWithinNotificationWindow(JsonNode node, LocalDateTime now) {
        long startMillis = node.path("startDateTime").asLong(0);
        if (startMillis == 0) return false;

        LocalDateTime appointmentTime = Instant.ofEpochMilli(startMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        return appointmentTime.isAfter(now) && appointmentTime.isBefore(now.plusDays(7));
    }
}
