package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {

    private DataService dataService;
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        dataService = mock(DataService.class);
        idempotencyService = new IdempotencyService(dataService);
    }

    private AppointmentChangedEvent buildEvent(String tenantId, String appointmentId, String changeType) {
        AppointmentChangedEvent event = new AppointmentChangedEvent();
        event.setTenantId(tenantId);
        event.setAppointmentId(appointmentId);
        event.setChangeType(changeType);
        return event;
    }

    @Test
    void processAppointment_returnsTrueForNewEvent() {
        AppointmentChangedEvent event = buildEvent("tenant-1", "appt-42", "CREATED");
        when(dataService.isEventProcessed(anyString())).thenReturn(false);

        boolean result = idempotencyService.processAppointment(event);

        assertThat(result).isTrue();
        verify(dataService).markEventAsProcessed(anyString());
    }

    @Test
    void processAppointment_returnsFalseForDuplicateEvent() {
        AppointmentChangedEvent event = buildEvent("tenant-1", "appt-42", "CREATED");
        when(dataService.isEventProcessed(anyString())).thenReturn(true);

        boolean result = idempotencyService.processAppointment(event);

        assertThat(result).isFalse();
        verify(dataService, never()).markEventAsProcessed(anyString());
    }

    @Test
    void generateEventId_isConsistentForSameInputs() {
        String id1 = idempotencyService.generateEventId("tenant-1", "appt-99", "UPDATED");
        String id2 = idempotencyService.generateEventId("tenant-1", "appt-99", "UPDATED");

        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void generateEventId_isDifferentForDifferentChangeType() {
        String createdId = idempotencyService.generateEventId("tenant-1", "appt-1", "CREATED");
        String cancelledId = idempotencyService.generateEventId("tenant-1", "appt-1", "DELETED");

        assertThat(createdId).isNotEqualTo(cancelledId);
    }

    @Test
    void generateEventId_isDifferentForDifferentTenants() {
        String tenantAId = idempotencyService.generateEventId("tenant-A", "appt-1", "CREATED");
        String tenantBId = idempotencyService.generateEventId("tenant-B", "appt-1", "CREATED");

        assertThat(tenantAId).isNotEqualTo(tenantBId);
    }

    @Test
    void generateEventId_returnsSha256HexString() {
        String eventId = idempotencyService.generateEventId("t1", "a1", "CREATED");

        // SHA-256 levert altijd 64 hex karakters op
        assertThat(eventId).hasSize(64).matches("[0-9a-f]+");
    }
}
