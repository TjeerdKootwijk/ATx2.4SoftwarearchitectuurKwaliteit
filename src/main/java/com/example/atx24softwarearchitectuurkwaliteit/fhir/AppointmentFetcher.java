package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * SRP: verantwoordelijk voor het ophalen van ruwe afspraakdata vanuit OpenMRS.
 * OCP: nieuwe implementaties (bijv. andere OpenMRS-versie) kunnen worden toegevoegd
 *      zonder bestaande code aan te passen.
 */
public interface AppointmentFetcher {
    List<JsonNode> fetchAppointments(TenantConfiguration tenant);
}
