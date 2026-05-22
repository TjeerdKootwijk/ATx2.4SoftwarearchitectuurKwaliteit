package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests voor FhirAppointmentValidator.
 *
 * Dekt NFR 6 — berichtontvangst en validatie:
 * controle op verplichte velden, structuur en logische consistentie.
 * Elk geval verifieert ook het ACK-patroon: ongeldige berichten worden
 * afgewezen met een duidelijke foutmelding.
 */
class FhirAppointmentValidatorTest {

    private FhirAppointmentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FhirAppointmentValidator();
    }

    // ── Geldige afspraken ─────────────────────────────────────────────────────

    @Test
    void validate_volledigeGeldigeAfspraak_geeftValidTerug() {
        Appointment appointment = maakGeldigeAfspraak();

        ValidationResult result = validator.validate(appointment);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void validate_geenEindtijd_isNogSteedsGeldig() {
        // Eindtijd is optioneel in FHIR R4
        Appointment appointment = maakGeldigeAfspraak();
        appointment.setEnd(null);

        ValidationResult result = validator.validate(appointment);

        assertThat(result.isValid()).isTrue();
    }

    // ── Ontbrekende verplichte velden ─────────────────────────────────────────

    @Test
    void validate_geenId_geeftOngeldigMetFoutmelding() {
        Appointment appointment = maakGeldigeAfspraak();
        appointment.setIdElement(null);

        ValidationResult result = validator.validate(appointment);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("id"));
    }

    @Test
    void validate_leegId_geeftOngeldigTerug() {
        Appointment appointment = maakGeldigeAfspraak();
        appointment.setIdElement(new org.hl7.fhir.r4.model.IdType("   "));

        ValidationResult result = validator.validate(appointment);

        assertThat(result.isValid()).isFalse();
    }

    @Test
    void validate_geenStatus_geeftOngeldigMetFoutmelding() {
        Appointment appointment = maakGeldigeAfspraak();
        appointment.setStatus(null);

        ValidationResult result = validator.validate(appointment);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("status"));
    }

    @Test
    void validate_geenStarttijd_geeftOngeldigMetFoutmelding() {
        Appointment appointment = maakGeldigeAfspraak();
        appointment.setStart(null);

        ValidationResult result = validator.validate(appointment);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("start"));
    }

    @Test
    void validate_geenParticipants_geeftOngeldigMetFoutmelding() {
        Appointment appointment = maakGeldigeAfspraak();
        appointment.getParticipant().clear();

        ValidationResult result = validator.validate(appointment);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("participant"));
    }

    // ── Logische validatie ────────────────────────────────────────────────────

    @Test
    void validate_startNaEinde_geeftOngeldigTerug() {
        Appointment appointment = maakGeldigeAfspraak();
        // Zet start LATER dan einde — logisch onmogelijk
        Instant now = Instant.now();
        appointment.setStart(Date.from(now.plus(3, ChronoUnit.HOURS)));
        appointment.setEnd(Date.from(now.plus(1, ChronoUnit.HOURS)));

        ValidationResult result = validator.validate(appointment);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("start"));
    }

    @Test
    void validate_startGelijkAanEinde_geeftOngeldigTerug() {
        Appointment appointment = maakGeldigeAfspraak();
        Date zelfdetijd = Date.from(Instant.now().plus(2, ChronoUnit.HOURS));
        appointment.setStart(zelfdetijd);
        appointment.setEnd(zelfdetijd);

        ValidationResult result = validator.validate(appointment);

        assertThat(result.isValid()).isFalse();
    }

    // ── Meerdere fouten tegelijk ──────────────────────────────────────────────

    @Test
    void validate_meerdereFoutenTegelijk_geeftAlleFortenTerug() {
        Appointment appointment = new Appointment();
        // Geen ID, geen status, geen starttijd, geen participants

        ValidationResult result = validator.validate(appointment);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSizeGreaterThanOrEqualTo(3);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Appointment maakGeldigeAfspraak() {
        Appointment appointment = new Appointment();
        appointment.setId("test-uuid-001");
        appointment.setStatus(Appointment.AppointmentStatus.BOOKED);

        Instant now = Instant.now();
        appointment.setStart(Date.from(now.plus(2, ChronoUnit.HOURS)));
        appointment.setEnd(Date.from(now.plus(3, ChronoUnit.HOURS)));

        Appointment.AppointmentParticipantComponent participant =
                new Appointment.AppointmentParticipantComponent();
        participant.setActor(new Reference()
                .setReference("Patient/patient-001")
                .setDisplay("Jan de Vries"));
        participant.setStatus(Appointment.ParticipationStatus.ACCEPTED);
        appointment.addParticipant(participant);

        return appointment;
    }
}
