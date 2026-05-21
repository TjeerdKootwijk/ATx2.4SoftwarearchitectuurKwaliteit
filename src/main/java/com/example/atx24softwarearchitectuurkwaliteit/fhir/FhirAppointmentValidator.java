package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import org.hl7.fhir.r4.model.Appointment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SRP: verantwoordelijk voor het valideren van FHIR R4 Appointment objecten.
 *
 * Dekt NFR 6 vereisten:
 *   - Berichtontvangst en validatie (controle op verplichte velden en structuur)
 *   - Acknowledgements (ACK): ongeldige berichten worden gelogd en overgeslagen
 *   - Logging en tracking: elk validatieresultaat wordt gelogd voor audit
 */
@Component
public class FhirAppointmentValidator implements AppointmentValidator {

    private static final Logger log = LoggerFactory.getLogger(FhirAppointmentValidator.class);

    @Override
    public ValidationResult validate(Appointment appointment) {
        List<String> errors = new ArrayList<>();

        // Verplicht veld: ID
        if (appointment.getId() == null || appointment.getId().isBlank()) {
            errors.add("Appointment.id is required (FHIR R4)");
        }

        // Verplicht veld: status
        if (appointment.getStatus() == null) {
            errors.add("Appointment.status is required (FHIR R4)");
        }

        // Verplicht veld: starttijd
        if (appointment.getStart() == null) {
            errors.add("Appointment.start is required (FHIR R4)");
        }

        // Verplicht: minimaal één participant (patiënt)
        if (appointment.getParticipant().isEmpty()) {
            errors.add("Appointment.participant must contain at least one entry (FHIR R4)");
        }

        // Logische validatie: start moet voor einde liggen
        if (appointment.getStart() != null && appointment.getEnd() != null
                && !appointment.getStart().before(appointment.getEnd())) {
            errors.add("Appointment.start must be before Appointment.end");
        }

        // ACK-patroon (NFR 6): ongeldige berichten worden gelogd
        if (errors.isEmpty()) {
            log.debug("FHIR R4 validation passed — Appointment.id={}", appointment.getId());
            return ValidationResult.ok();
        } else {
            log.warn("FHIR R4 validation failed — Appointment.id={} | errors={}", appointment.getId(), errors);
            return ValidationResult.failed(errors);
        }
    }
}
