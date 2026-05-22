package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hl7.fhir.r4.model.Appointment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests voor FhirR4AppointmentMapper.
 *
 * Test twee verantwoordelijkheden (AppointmentMapper + AppointmentEventConverter):
 *   1. OpenMRS JSON → FHIR R4 Appointment (map)
 *   2. FHIR R4 Appointment → AppointmentChangedEvent (convert)
 *
 * Dekt NFR 6 — berichttransformatie: mapping tussen formaten.
 */
class FhirR4AppointmentMapperTest {

    private FhirR4AppointmentMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mapper = new FhirR4AppointmentMapper();
        objectMapper = new ObjectMapper();
    }

    // ── map(): OpenMRS JSON → FHIR R4 Appointment ────────────────────────────

    @Test
    void map_volledigeJson_geeftFhirAppointmentMetJuisteId() {
        ObjectNode node = maakOpenMrsNode("afspraak-uuid-001", "Scheduled",
                Instant.now().plus(2, ChronoUnit.HOURS));

        Appointment result = mapper.map(node);

        assertThat(result.getId()).isEqualTo("afspraak-uuid-001");
    }

    @Test
    void map_statusScheduled_wordtBooked() {
        ObjectNode node = maakOpenMrsNode("uuid-001", "Scheduled",
                Instant.now().plus(2, ChronoUnit.HOURS));

        Appointment result = mapper.map(node);

        assertThat(result.getStatus()).isEqualTo(Appointment.AppointmentStatus.BOOKED);
    }

    @Test
    void map_statusCancelled_wordtCancelled() {
        ObjectNode node = maakOpenMrsNode("uuid-002", "Cancelled",
                Instant.now().plus(2, ChronoUnit.HOURS));

        Appointment result = mapper.map(node);

        assertThat(result.getStatus()).isEqualTo(Appointment.AppointmentStatus.CANCELLED);
    }

    @Test
    void map_statusCompleted_wordtFulfilled() {
        ObjectNode node = maakOpenMrsNode("uuid-003", "Completed",
                Instant.now().plus(2, ChronoUnit.HOURS));

        Appointment result = mapper.map(node);

        assertThat(result.getStatus()).isEqualTo(Appointment.AppointmentStatus.FULFILLED);
    }

    @Test
    void map_statusMissed_wordtNoShow() {
        ObjectNode node = maakOpenMrsNode("uuid-004", "Missed",
                Instant.now().plus(2, ChronoUnit.HOURS));

        Appointment result = mapper.map(node);

        assertThat(result.getStatus()).isEqualTo(Appointment.AppointmentStatus.NOSHOW);
    }

    @Test
    void map_onbekendeStatus_wordtPending() {
        ObjectNode node = maakOpenMrsNode("uuid-005", "Onbekend",
                Instant.now().plus(2, ChronoUnit.HOURS));

        Appointment result = mapper.map(node);

        assertThat(result.getStatus()).isEqualTo(Appointment.AppointmentStatus.PENDING);
    }

    @Test
    void map_startTijdWordtCorrectGezet() {
        Instant verwacht = Instant.now().plus(4, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        ObjectNode node = maakOpenMrsNode("uuid-006", "Scheduled", verwacht);

        Appointment result = mapper.map(node);

        assertThat(result.getStart().toInstant().truncatedTo(ChronoUnit.SECONDS))
                .isEqualTo(verwacht);
    }

    @Test
    void map_patiëntWordtAlsParticipantToegevoegd() {
        ObjectNode node = maakOpenMrsNode("uuid-007", "Scheduled",
                Instant.now().plus(2, ChronoUnit.HOURS));

        Appointment result = mapper.map(node);

        boolean heeftPatiëntParticipant = result.getParticipant().stream()
                .anyMatch(p -> p.getActor() != null
                        && p.getActor().getReference() != null
                        && p.getActor().getReference().startsWith("Patient/"));

        assertThat(heeftPatiëntParticipant).isTrue();
    }

    @Test
    void map_patiëntReferentieBevatUuid() {
        ObjectNode node = maakOpenMrsNode("uuid-008", "Scheduled",
                Instant.now().plus(2, ChronoUnit.HOURS));

        Appointment result = mapper.map(node);

        String patiëntRef = result.getParticipant().stream()
                .map(p -> p.getActor().getReference())
                .filter(r -> r != null && r.startsWith("Patient/"))
                .findFirst().orElse("");

        assertThat(patiëntRef).isEqualTo("Patient/patient-uuid-001");
    }

    @Test
    void map_locatieWordtAlsParticipantToegevoegd() {
        ObjectNode node = maakOpenMrsNode("uuid-009", "Scheduled",
                Instant.now().plus(2, ChronoUnit.HOURS));

        Appointment result = mapper.map(node);

        boolean heeftLocatieParticipant = result.getParticipant().stream()
                .anyMatch(p -> p.getActor() != null
                        && p.getActor().getReference() == null
                        && "Polikliniek Kamer 3B".equals(p.getActor().getDisplay()));

        assertThat(heeftLocatieParticipant).isTrue();
    }

    @Test
    void map_commentWordtGezet() {
        ObjectNode node = maakOpenMrsNode("uuid-010", "Scheduled",
                Instant.now().plus(2, ChronoUnit.HOURS));

        Appointment result = mapper.map(node);

        assertThat(result.getComment()).isEqualTo("Nuchter komen");
    }

    // ── convert(): FHIR R4 Appointment → AppointmentChangedEvent ─────────────

    @Test
    void convert_eventIdIsDeterministisch() {
        ObjectNode node = maakOpenMrsNode("uuid-011", "Scheduled",
                Instant.now().plus(2, ChronoUnit.HOURS));
        Appointment appointment = mapper.map(node);

        AppointmentChangedEvent event1 = mapper.convert(appointment, "tenant-a");
        AppointmentChangedEvent event2 = mapper.convert(appointment, "tenant-a");

        assertThat(event1.getEventId()).isEqualTo(event2.getEventId());
    }

    @Test
    void convert_verschillendeTenant_geeftVerschillendEventId() {
        ObjectNode node = maakOpenMrsNode("uuid-012", "Scheduled",
                Instant.now().plus(2, ChronoUnit.HOURS));
        Appointment appointment = mapper.map(node);

        AppointmentChangedEvent event1 = mapper.convert(appointment, "tenant-a");
        AppointmentChangedEvent event2 = mapper.convert(appointment, "tenant-b");

        assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
    }

    @Test
    void convert_tenantIdWordtGezet() {
        ObjectNode node = maakOpenMrsNode("uuid-013", "Scheduled",
                Instant.now().plus(2, ChronoUnit.HOURS));
        Appointment appointment = mapper.map(node);

        AppointmentChangedEvent event = mapper.convert(appointment, "mijn-tenant");

        assertThat(event.getTenantId()).isEqualTo("mijn-tenant");
    }

    @Test
    void convert_appointmentUuidWordtGezet() {
        ObjectNode node = maakOpenMrsNode("uuid-014", "Scheduled",
                Instant.now().plus(2, ChronoUnit.HOURS));
        Appointment appointment = mapper.map(node);

        AppointmentChangedEvent event = mapper.convert(appointment, "tenant");

        assertThat(event.getAppointmentUuid()).isEqualTo("uuid-014");
    }

    @Test
    void convert_bronIsPolling() {
        ObjectNode node = maakOpenMrsNode("uuid-015", "Scheduled",
                Instant.now().plus(2, ChronoUnit.HOURS));
        Appointment appointment = mapper.map(node);

        AppointmentChangedEvent event = mapper.convert(appointment, "tenant");

        assertThat(event.getSource()).isEqualTo("POLLING");
    }

    @Test
    void convert_gecanceldAfspraak_geeftChangeTypeDeleted() {
        ObjectNode node = maakOpenMrsNode("uuid-016", "Cancelled",
                Instant.now().plus(2, ChronoUnit.HOURS));
        Appointment appointment = mapper.map(node);

        AppointmentChangedEvent event = mapper.convert(appointment, "tenant");

        assertThat(event.getChangeType()).isEqualTo("DELETED");
    }

    @Test
    void convert_geplandAfspraak_geeftChangeTypeCreated() {
        ObjectNode node = maakOpenMrsNode("uuid-017", "Scheduled",
                Instant.now().plus(2, ChronoUnit.HOURS));
        Appointment appointment = mapper.map(node);

        AppointmentChangedEvent event = mapper.convert(appointment, "tenant");

        assertThat(event.getChangeType()).isEqualTo("CREATED");
    }

    @Test
    void convert_patiëntIdWordtExtractedUitParticipants() {
        ObjectNode node = maakOpenMrsNode("uuid-018", "Scheduled",
                Instant.now().plus(2, ChronoUnit.HOURS));
        Appointment appointment = mapper.map(node);

        AppointmentChangedEvent event = mapper.convert(appointment, "tenant");

        assertThat(event.getPatientId()).isEqualTo("patient-uuid-001");
        assertThat(event.getPatientName()).isEqualTo("Jan de Vries");
    }

    @Test
    void convert_locatieWordtExtractedUitParticipants() {
        ObjectNode node = maakOpenMrsNode("uuid-019", "Scheduled",
                Instant.now().plus(2, ChronoUnit.HOURS));
        Appointment appointment = mapper.map(node);

        AppointmentChangedEvent event = mapper.convert(appointment, "tenant");

        assertThat(event.getLocation()).isEqualTo("Polikliniek Kamer 3B");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ObjectNode maakOpenMrsNode(String uuid, String status, Instant startInstant) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("uuid", uuid);
        node.put("status", status);
        node.put("startDateTime", startInstant.toEpochMilli());
        node.put("endDateTime", startInstant.plus(30, ChronoUnit.MINUTES).toEpochMilli());
        node.put("comments", "Nuchter komen");

        ObjectNode patient = objectMapper.createObjectNode();
        patient.put("uuid", "patient-uuid-001");
        patient.put("display", "Jan de Vries");
        node.set("patient", patient);

        ObjectNode appointmentType = objectMapper.createObjectNode();
        appointmentType.put("display", "Consultatie");
        node.set("appointmentType", appointmentType);

        ObjectNode location = objectMapper.createObjectNode();
        location.put("display", "Polikliniek Kamer 3B");
        node.set("location", location);

        return node;
    }
}
