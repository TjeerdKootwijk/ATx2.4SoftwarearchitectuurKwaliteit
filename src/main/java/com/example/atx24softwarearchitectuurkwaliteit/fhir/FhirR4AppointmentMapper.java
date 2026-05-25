package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * SRP: verantwoordelijk voor twee samenhangende mapping-verantwoordelijkheden:
 *      1. OpenMRS JSON  → FHIR R4 Appointment (AppointmentMapper)
 *      2. FHIR R4 Appointment → intern AppointmentChangedEvent (AppointmentEventConverter)
 *
 * LSP: kan overal waar AppointmentMapper of AppointmentEventConverter verwacht wordt worden gebruikt.
 *
 * Intern FHIR-gebruik: OpenMRS levert geen FHIR endpoint, maar wij verwerken afspraken
 * intern als FHIR R4 objecten zodat de module voldoet aan de HL7/FHIR-specificatie (NFR 6).
 */
@Component
public class FhirR4AppointmentMapper implements AppointmentMapper, AppointmentEventConverter {

    private static final Logger log = LoggerFactory.getLogger(FhirR4AppointmentMapper.class);

    // ── AppointmentMapper ──────────────────────────────────────────────────────

    @Override
    public Appointment map(JsonNode node) {
        Appointment appointment = new Appointment();

        String uuid = node.path("uuid").asText(null);
        if (uuid != null) {
            appointment.setId(uuid);
        }

        appointment.setStatus(mapStatus(node.path("status").asText("Scheduled")));

        long startMillis = node.path("startDateTime").asLong(0);
        if (startMillis > 0) {
            appointment.setStart(Date.from(Instant.ofEpochMilli(startMillis)));
        }

        long endMillis = node.path("endDateTime").asLong(0);
        if (endMillis > 0) {
            appointment.setEnd(Date.from(Instant.ofEpochMilli(endMillis)));
        }

        String serviceType = node.path("appointmentType").path("display").asText(null);
        if (serviceType != null) {
            appointment.addServiceType(new CodeableConcept().setText(serviceType));
        }

        addPatientParticipant(appointment, node);
        addLocationParticipant(appointment, node);

        String comments = node.path("comments").asText(null);
        if (comments != null) {
            appointment.setComment(comments);
        }

        log.debug("Mapped OpenMRS appointment {} to FHIR R4 Appointment with status {}",
                uuid, appointment.getStatus());

        return appointment;
    }

    // ── AppointmentEventConverter ──────────────────────────────────────────────

    @Override
    public AppointmentChangedEvent convert(Appointment appointment, String tenantId, ZoneId tenantZone) {
        AppointmentChangedEvent event = new AppointmentChangedEvent();

        // Deterministisch event-ID: zelfde afspraak + status = zelfde ID (idempotency)
        String deterministicId = tenantId + ":" + appointment.getId() + ":"
                + (appointment.getStatus() != null ? appointment.getStatus().toCode() : "unknown");
        event.setEventId(deterministicId);

        event.setTenantId(tenantId);
        event.setAppointmentId(appointment.getId());
        event.setAppointmentUuid(appointment.getId());
        event.setSource("POLLING");
        event.setReceivedAt(LocalDateTime.now());
        event.setTimezone(tenantZone.getId());

        if (appointment.getStatus() != null) {
            event.setStatus(appointment.getStatus().toCode());
            event.setChangeType(mapChangeType(appointment.getStatus()));
        }

        if (appointment.getStart() != null) {
            // NFR13: convert UTC epoch to LocalDateTime in the tenant's local timezone
            event.setAppointmentDateTime(
                    appointment.getStart().toInstant()
                            .atZone(tenantZone)
                            .toLocalDateTime());
        }

        for (Appointment.AppointmentParticipantComponent participant : appointment.getParticipant()) {
            Reference actor = participant.getActor();
            if (actor == null) continue;

            String ref = actor.getReference();
            if (ref != null && ref.startsWith("Patient/")) {
                event.setPatientId(ref.replace("Patient/", ""));
                event.setPatientName(actor.getDisplay());
            } else if (ref == null && actor.getDisplay() != null) {
                event.setLocation(actor.getDisplay());
            }
        }

        log.debug("Converted FHIR Appointment {} to AppointmentChangedEvent for tenant {}",
                appointment.getId(), tenantId);

        return event;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void addPatientParticipant(Appointment appointment, JsonNode node) {
        String patientUuid = node.path("patient").path("uuid").asText(null);
        String patientName = node.path("patient").path("display").asText(null);

        if (patientUuid != null) {
            Appointment.AppointmentParticipantComponent participant =
                    new Appointment.AppointmentParticipantComponent();
            participant.setActor(new Reference()
                    .setReference("Patient/" + patientUuid)
                    .setDisplay(patientName));
            participant.setStatus(Appointment.ParticipationStatus.ACCEPTED);
            appointment.addParticipant(participant);
        }
    }

    private void addLocationParticipant(Appointment appointment, JsonNode node) {
        String location = node.path("location").path("display").asText(null);

        if (location != null) {
            Appointment.AppointmentParticipantComponent participant =
                    new Appointment.AppointmentParticipantComponent();
            participant.setActor(new Reference().setDisplay(location));
            participant.setStatus(Appointment.ParticipationStatus.ACCEPTED);
            appointment.addParticipant(participant);
        }
    }

    private Appointment.AppointmentStatus mapStatus(String openMrsStatus) {
        return switch (openMrsStatus.toLowerCase()) {
            case "scheduled"  -> Appointment.AppointmentStatus.BOOKED;
            case "cancelled"  -> Appointment.AppointmentStatus.CANCELLED;
            case "completed"  -> Appointment.AppointmentStatus.FULFILLED;
            case "missed"     -> Appointment.AppointmentStatus.NOSHOW;
            default           -> Appointment.AppointmentStatus.PENDING;
        };
    }

    private String mapChangeType(Appointment.AppointmentStatus status) {
        return switch (status) {
            case CANCELLED  -> "DELETED";
            case FULFILLED  -> "COMPLETED";
            default         -> "CREATED";
        };
    }
}
