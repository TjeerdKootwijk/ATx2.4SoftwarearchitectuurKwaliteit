package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import org.hl7.fhir.r4.model.Appointment;

/**
 * SRP: verantwoordelijk voor het omzetten van OpenMRS JSON naar een FHIR R4 Appointment object.
 * ISP: bewust gescheiden van AppointmentEventConverter — niet elke consumer heeft beide nodig.
 */
public interface AppointmentMapper {
    Appointment map(JsonNode openMrsNode);
}
