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

import java.time.DateTimeException;
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
    private final AppointmentService appointmentService;
    private final IdempotencyService idempotencyService;

    public PollingJob(TenantService tenantService,
                      AppointmentFetcher fetcher,
                      AppointmentMapper mapper,
                      AppointmentEventConverter converter,
                      AppointmentValidator validator,
                      AppointmentService appointmentService,
                      IdempotencyService idempotencyService) {
        this.tenantService = tenantService;
        this.fetcher = fetcher;
        this.mapper = mapper;
        this.converter = converter;
        this.validator = validator;
        this.appointmentService = appointmentService;
        this.idempotencyService = idempotencyService;
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
        ZoneId tenantZone = resolveZone(tenant);
        LocalDateTime now = LocalDateTime.now(tenantZone);
        int published = 0;

        for (JsonNode node : fetcher.fetchAppointments(tenant)) {
            try {
                if (!isWithinNotificationWindow(node, now, tenantZone)) continue;

                // Stap 1: map OpenMRS JSON → FHIR R4 Appointment (intern, NFR 6 — berichttransformatie)
                Appointment fhirAppointment = mapper.map(node);

                // Stap 2: valideer FHIR R4 object (NFR 6 — berichtontvangst en validatie + ACK)
                ValidationResult validationResult = validator.validate(fhirAppointment);
                if (!validationResult.isValid()) {
                    log.warn("Tenant {} — invalid FHIR appointment skipped: {}", tenantId, validationResult.getErrors());
                    continue;
                }

                // Stap 3: converteer FHIR Appointment → intern event (NFR13: tenant timezone meegeven)
                AppointmentChangedEvent event = converter.convert(fhirAppointment, tenantId, tenantZone);

                // Per-tenant notificatieprovider meegeven (multi-tenancy: elke tenant kan eigen provider hebben)
                event.setNotificationProvider(tenant.getNotificationProvider());

                // Stap 4: idempotency check — skip als dit event al eerder verwerkt is
                if (!idempotencyService.processAppointment(event)) {
                    continue;
                }
                log.info("--------------------------------------- tim eof appointment" + event.getAppointmentDateTime());

                // Stap 5: AppointmentService berekent 24h/1h windows en publiceert naar RabbitMQ
                appointmentService.handleAppointment(event);
                log.info("Appointment {} forwarded to AppointmentService", event.getAppointmentId());
                published++;

            } catch (Exception e) {
                log.error("Error processing appointment for tenant {}: {}", tenantId, e.getMessage(), e);
            }
        }
        log.info("Tenant {} → {} appointments send to appointmentService", tenantId, published);

    }

    /**
     * Filter: alleen afspraken die nog niet begonnen zijn en binnen 7 dagen plaatsvinden.
     * Afspraken die al zijn aangevangen krijgen geen notificatie (functionele eis 1).
     * NFR13: vergelijking vindt plaats in de lokale timezone van de tenant.
     */
    private boolean isWithinNotificationWindow(JsonNode node, LocalDateTime now, ZoneId tenantZone) {
        long startMillis = node.path("startDateTime").asLong(0);
        if (startMillis == 0) return false;

        LocalDateTime appointmentTime = Instant.ofEpochMilli(startMillis)
                .atZone(tenantZone)
                .toLocalDateTime();

        return appointmentTime.isAfter(now) && appointmentTime.isBefore(now.plusDays(7));
    }

    private ZoneId resolveZone(TenantConfiguration tenant) {
        try {
            return ZoneId.of(tenant.getTimezone());
        } catch (DateTimeException e) {
            log.warn("Invalid timezone '{}' for tenant {} — falling back to UTC",
                    tenant.getTimezone(), tenant.getTenantId());
            return ZoneId.of("UTC");
        }
    }
}
