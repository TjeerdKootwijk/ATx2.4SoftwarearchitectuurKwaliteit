package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.fhir.*;
import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hl7.fhir.r4.model.Appointment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests voor {@link PollingJob}.
 *
 * PollingJob is een orchestrator: deze tests verifiëren dat de stappen
 * (filteren → mappen → valideren → converteren → idempotency → publiceren)
 * in de juiste volgorde en met de juiste voorwaarden worden uitgevoerd.
 * Alle samenwerkende componenten worden gemockt.
 */
class PollingJobTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TenantService tenantService;
    private AppointmentFetcher fetcher;
    private AppointmentMapper mapper;
    private AppointmentEventConverter converter;
    private AppointmentValidator validator;
    private AppointmentService appointmentService;
    private IdempotencyService idempotencyService;

    private PollingJob pollingJob;
    private TenantConfiguration tenant;

    @BeforeEach
    void setUp() {
        tenantService = mock(TenantService.class);
        fetcher = mock(AppointmentFetcher.class);
        mapper = mock(AppointmentMapper.class);
        converter = mock(AppointmentEventConverter.class);
        validator = mock(AppointmentValidator.class);
        appointmentService = mock(AppointmentService.class);
        idempotencyService = mock(IdempotencyService.class);

        pollingJob = new PollingJob(tenantService, fetcher, mapper, converter,
                validator, appointmentService, idempotencyService);

        tenant = new TenantConfiguration("test-tenant", "Test Ziekenhuis");
        tenant.setTimezone("UTC");
        tenant.setNotificationProvider("ASYNCFLOW");
        tenant.setActive(true);
        when(tenantService.getAllActiveTenants()).thenReturn(List.of(tenant));
    }

    /** Een afspraak met startDateTime over {@code days} dagen (epoch millis). */
    private JsonNode appointmentInDays(long days) {
        ObjectNode node = JSON.createObjectNode();
        node.put("startDateTime", Instant.now().plus(Duration.ofDays(days)).toEpochMilli());
        return node;
    }

    @Test
    void validAppointmentWithinWindow_isForwardedToAppointmentService() {
        when(fetcher.fetchAppointments(tenant)).thenReturn(List.of(appointmentInDays(2)));
        when(mapper.map(any())).thenReturn(new Appointment());
        when(validator.validate(any())).thenReturn(ValidationResult.ok());

        AppointmentChangedEvent event = new AppointmentChangedEvent("evt-1", "test-tenant", "appt-1");
        when(converter.convert(any(), eq("test-tenant"), any())).thenReturn(event);
        when(idempotencyService.processAppointment(event)).thenReturn(true);

        pollingJob.pollOpenMrsAppointments();

        verify(appointmentService).handleAppointment(event);
        // De tenant-specifieke provider moet op het event gezet zijn (multi-tenancy)
        org.junit.jupiter.api.Assertions.assertEquals("ASYNCFLOW", event.getNotificationProvider());
    }

    @Test
    void appointmentOutsideWindow_isSkipped() {
        // Afspraak in het verleden valt buiten het notificatievenster
        when(fetcher.fetchAppointments(tenant)).thenReturn(List.of(appointmentInDays(-1)));

        pollingJob.pollOpenMrsAppointments();

        verifyNoInteractions(mapper, validator, converter, appointmentService);
    }

    @Test
    void invalidFhirAppointment_isSkipped() {
        when(fetcher.fetchAppointments(tenant)).thenReturn(List.of(appointmentInDays(2)));
        when(mapper.map(any())).thenReturn(new Appointment());
        when(validator.validate(any())).thenReturn(ValidationResult.failed(List.of("missing id")));

        pollingJob.pollOpenMrsAppointments();

        verifyNoInteractions(converter, appointmentService);
        verify(idempotencyService, never()).processAppointment(any());
    }

    @Test
    void duplicateEvent_isSkipped() {
        when(fetcher.fetchAppointments(tenant)).thenReturn(List.of(appointmentInDays(2)));
        when(mapper.map(any())).thenReturn(new Appointment());
        when(validator.validate(any())).thenReturn(ValidationResult.ok());
        when(converter.convert(any(), anyString(), any()))
                .thenReturn(new AppointmentChangedEvent("evt-1", "test-tenant", "appt-1"));
        when(idempotencyService.processAppointment(any())).thenReturn(false);

        pollingJob.pollOpenMrsAppointments();

        verify(appointmentService, never()).handleAppointment(any());
    }

    @Test
    void exceptionForOneAppointment_doesNotStopOthers() {
        when(fetcher.fetchAppointments(tenant))
                .thenReturn(List.of(appointmentInDays(2), appointmentInDays(3)));
        // Eerste mapping faalt, tweede slaagt
        when(mapper.map(any()))
                .thenThrow(new RuntimeException("mapping kapot"))
                .thenReturn(new Appointment());
        when(validator.validate(any())).thenReturn(ValidationResult.ok());

        AppointmentChangedEvent event = new AppointmentChangedEvent("evt-2", "test-tenant", "appt-2");
        when(converter.convert(any(), anyString(), any())).thenReturn(event);
        when(idempotencyService.processAppointment(event)).thenReturn(true);

        pollingJob.pollOpenMrsAppointments();

        // Ondanks de fout bij de eerste afspraak wordt de tweede gewoon verwerkt
        verify(appointmentService, times(1)).handleAppointment(event);
    }
}
