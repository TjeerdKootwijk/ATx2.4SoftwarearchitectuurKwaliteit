package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.RabbitMQProducer;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AppointmentServiceTest {

    private RabbitMQProducer rabbitMQProducer;
    private TenantService tenantService;
    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        rabbitMQProducer = mock(RabbitMQProducer.class);
        tenantService = mock(TenantService.class);
        appointmentService = new AppointmentService(rabbitMQProducer, tenantService);
    }

    private AppointmentChangedEvent buildEvent(LocalDateTime appointmentTime) {
        AppointmentChangedEvent event = new AppointmentChangedEvent();
        event.setTenantId("tenant-ziekenhuis-a");
        event.setAppointmentId("appt-001");
        event.setAppointmentDateTime(appointmentTime);
        event.setChangeType("CREATED");
        event.setLocation("Polikliniek B, Kamer 12");
        return event;
    }

    @Test
    void handleAppointment_sends24hReminderWhenInWindow() {
        // Afspraak is over precies 23,5 uur (midden in het 24h window)
        LocalDateTime appointmentTime = LocalDateTime.now().plusHours(23).plusMinutes(30);
        AppointmentChangedEvent event = buildEvent(appointmentTime);

        TenantConfiguration tenant = new TenantConfiguration("tenant-ziekenhuis-a", "Ziekenhuis A");
        tenant.setNotificationProvider("SWIFTSEND");
        when(tenantService.getTenantConfiguration("tenant-ziekenhuis-a")).thenReturn(tenant);

        appointmentService.handleAppointment(event);

        ArgumentCaptor<NotificationQueueMessage> captor = ArgumentCaptor.forClass(NotificationQueueMessage.class);
        verify(rabbitMQProducer, atLeastOnce()).publish(captor.capture());

        List<NotificationQueueMessage> published = captor.getAllValues();
        boolean has24h = published.stream()
                .anyMatch(m -> "APPOINTMENT_REMINDER_24H".equals(m.getMessageType()));
        assertThat(has24h).isTrue();
    }

    @Test
    void handleAppointment_sends1hReminderWhenInWindow() {
        // Afspraak is over 30 minuten (midden in het 1h window)
        LocalDateTime appointmentTime = LocalDateTime.now().plusMinutes(30);
        AppointmentChangedEvent event = buildEvent(appointmentTime);

        TenantConfiguration tenant = new TenantConfiguration("tenant-ziekenhuis-a", "Ziekenhuis A");
        tenant.setNotificationProvider("LEGACYLINK");
        when(tenantService.getTenantConfiguration("tenant-ziekenhuis-a")).thenReturn(tenant);

        appointmentService.handleAppointment(event);

        ArgumentCaptor<NotificationQueueMessage> captor = ArgumentCaptor.forClass(NotificationQueueMessage.class);
        verify(rabbitMQProducer, atLeastOnce()).publish(captor.capture());

        List<NotificationQueueMessage> published = captor.getAllValues();
        boolean has1h = published.stream()
                .anyMatch(m -> "APPOINTMENT_REMINDER_1H".equals(m.getMessageType()));
        assertThat(has1h).isTrue();
    }

    @Test
    void handleAppointment_sendsNoNotificationForPastAppointment() {
        // Afspraak was gisteren
        LocalDateTime appointmentTime = LocalDateTime.now().minusDays(1);
        AppointmentChangedEvent event = buildEvent(appointmentTime);

        TenantConfiguration tenant = new TenantConfiguration("tenant-ziekenhuis-a", "Ziekenhuis A");
        tenant.setNotificationProvider("SWIFTSEND");
        when(tenantService.getTenantConfiguration("tenant-ziekenhuis-a")).thenReturn(tenant);

        appointmentService.handleAppointment(event);

        verify(rabbitMQProducer, never()).publish(any());
    }

    @Test
    void handleAppointment_sendsNoNotificationWhenAppointmentIsFarInFuture() {
        // Afspraak is over 3 dagen, buiten beide windows
        LocalDateTime appointmentTime = LocalDateTime.now().plusDays(3);
        AppointmentChangedEvent event = buildEvent(appointmentTime);

        TenantConfiguration tenant = new TenantConfiguration("tenant-ziekenhuis-a", "Ziekenhuis A");
        tenant.setNotificationProvider("SWIFTSEND");
        when(tenantService.getTenantConfiguration("tenant-ziekenhuis-a")).thenReturn(tenant);

        appointmentService.handleAppointment(event);

        verify(rabbitMQProducer, never()).publish(any());
    }

    @Test
    void handleAppointment_fallsBackToSwiftSendWhenTenantHasNoProvider() {
        LocalDateTime appointmentTime = LocalDateTime.now().plusMinutes(30);
        AppointmentChangedEvent event = buildEvent(appointmentTime);

        // Tenant zonder geconfigureerde provider
        when(tenantService.getTenantConfiguration("tenant-ziekenhuis-a")).thenReturn(null);

        appointmentService.handleAppointment(event);

        ArgumentCaptor<NotificationQueueMessage> captor = ArgumentCaptor.forClass(NotificationQueueMessage.class);
        verify(rabbitMQProducer, atLeastOnce()).publish(captor.capture());

        List<NotificationQueueMessage> published = captor.getAllValues();
        assertThat(published).allMatch(m -> "SWIFTSEND".equals(m.getProvider()));
    }
}
