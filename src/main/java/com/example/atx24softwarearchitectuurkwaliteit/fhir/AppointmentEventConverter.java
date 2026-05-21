package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import org.hl7.fhir.r4.model.Appointment;

/**
 * SRP: verantwoordelijk voor het omzetten van een FHIR R4 Appointment naar een intern event.
 * ISP: bewust gescheiden van AppointmentMapper — twee aparte verantwoordelijkheden,
 *      twee aparte interfaces.
 */
public interface AppointmentEventConverter {
    AppointmentChangedEvent convert(Appointment appointment, String tenantId);
}
