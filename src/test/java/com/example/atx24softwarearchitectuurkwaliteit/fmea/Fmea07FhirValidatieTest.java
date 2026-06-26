package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.example.atx24softwarearchitectuurkwaliteit.fhir.ValidationResult;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FMEA Scenario 7 - RPN 10 - MIDDEN - NFR6, NFR9
 *
 * Scenario: OpenMRS REST API wijzigt response-formaat (breaking change).
 * Effect:   FHIR-mapper gooit ParseException; alle polls mislukken.
 * Maatregel: FhirAppointmentValidator vangt structuurfouten op en retourneert
 *            ValidationResult.failed() met duidelijke foutbeschrijving.
 *            Grafana-alert bij >5% validatiefouten.
 */
class Fmea07FhirValidatieTest extends FmeaBaseTest {

    @Test
    void lege_appointment_zonder_verplichte_velden_faalt_validatie() {
        Appointment legeAfspraak = new Appointment();

        ValidationResult result = fhirValidator.validate(legeAfspraak);

        assertThat(result.isValid())
                .as("Appointment zonder verplichte velden moet validatie laten falen")
                .isFalse();
        assertThat(result.getErrors())
                .as("Validatiefouten moeten duidelijk beschreven zijn voor operationeel toezicht")
                .isNotEmpty();
    }

    @Test
    void appointment_zonder_starttijd_faalt_validatie() {
        Appointment afspraak = new Appointment();
        afspraak.setId("test-id");
        afspraak.setStatus(Appointment.AppointmentStatus.BOOKED);
        afspraak.addParticipant().setActor(new Reference("Patient/1"));
        // start ontbreekt intentioneel (simuleert breaking change in OpenMRS response)

        ValidationResult result = fhirValidator.validate(afspraak);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .anyMatch(e -> e.toLowerCase().contains("start"));
    }

    @Test
    void geldige_appointment_slaagt_validatie() {
        Appointment geldigeAfspraak = new Appointment();
        geldigeAfspraak.setId("geldig-id");
        geldigeAfspraak.setStatus(Appointment.AppointmentStatus.BOOKED);
        geldigeAfspraak.setStart(new Date());
        geldigeAfspraak.setEnd(new Date(System.currentTimeMillis() + 3_600_000));
        geldigeAfspraak.addParticipant().setActor(new Reference("Patient/1"));

        ValidationResult result = fhirValidator.validate(geldigeAfspraak);

        assertThat(result.isValid())
                .as("Appointment met alle verplichte velden moet validatie doorstaan")
                .isTrue();
    }
}
