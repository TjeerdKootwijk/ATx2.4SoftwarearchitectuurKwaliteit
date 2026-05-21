package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import org.hl7.fhir.r4.model.Appointment;

/**
 * SRP: verantwoordelijk voor het valideren van een FHIR R4 Appointment (NFR 6 — berichtontvangst en validatie).
 * OCP: nieuwe validatieregels kunnen als extra implementatie worden toegevoegd zonder bestaande code te wijzigen.
 */
public interface AppointmentValidator {
    ValidationResult validate(Appointment appointment);
}
