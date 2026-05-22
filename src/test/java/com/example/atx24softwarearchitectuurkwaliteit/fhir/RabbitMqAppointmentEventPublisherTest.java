package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import com.example.atx24softwarearchitectuurkwaliteit.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests voor RabbitMqAppointmentEventPublisher.
 *
 * Verifieert idempotency: hetzelfde event mag slechts éénmaal naar RabbitMQ
 * worden gepubliceerd, ook als de PollingJob het event meerdere keren aanbiedt.
 *
 * Dekt NFR 6 — queueing: events worden correct doorgestuurd.
 */
@ExtendWith(MockitoExtension.class)
class RabbitMqAppointmentEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private IdempotencyService idempotencyService;

    private RabbitMqAppointmentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new RabbitMqAppointmentEventPublisher(rabbitTemplate, idempotencyService);
    }

    @Test
    void publish_nieuwEvent_wordtNaarRabbitMqGestuurd() {
        AppointmentChangedEvent event = maakEvent("event-nieuw-001");
        when(idempotencyService.isEventProcessed("event-nieuw-001")).thenReturn(false);

        publisher.publish(event);

        verify(rabbitTemplate, times(1))
                .convertAndSend(eq("appointment.events"), eq("appointment.changed"), eq(event));
    }

    @Test
    void publish_nieuwEvent_wordtGemarkerdAlsVerwerkt() {
        AppointmentChangedEvent event = maakEvent("event-nieuw-002");
        when(idempotencyService.isEventProcessed("event-nieuw-002")).thenReturn(false);

        publisher.publish(event);

        verify(idempotencyService, times(1)).markEventAsProcessed("event-nieuw-002");
    }

    @Test
    void publish_duplicaatEvent_wordtNietNaarRabbitMqGestuurd() {
        AppointmentChangedEvent event = maakEvent("event-duplicaat-001");
        when(idempotencyService.isEventProcessed("event-duplicaat-001")).thenReturn(true);

        publisher.publish(event);

        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(Object.class));
    }

    @Test
    void publish_duplicaatEvent_wordtNietNogmaalsGemarkerd() {
        AppointmentChangedEvent event = maakEvent("event-duplicaat-002");
        when(idempotencyService.isEventProcessed("event-duplicaat-002")).thenReturn(true);

        publisher.publish(event);

        verify(idempotencyService, never()).markEventAsProcessed(any());
    }

    @Test
    void publish_tweeVerschillendeEvents_wordenBeidePgubliceerd() {
        AppointmentChangedEvent event1 = maakEvent("event-a");
        AppointmentChangedEvent event2 = maakEvent("event-b");
        when(idempotencyService.isEventProcessed(any())).thenReturn(false);

        publisher.publish(event1);
        publisher.publish(event2);

        verify(rabbitTemplate, times(2))
                .convertAndSend(eq("appointment.events"), eq("appointment.changed"), any(Object.class));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private AppointmentChangedEvent maakEvent(String eventId) {
        AppointmentChangedEvent event = new AppointmentChangedEvent();
        event.setEventId(eventId);
        event.setTenantId("test-ziekenhuis");
        event.setAppointmentUuid("afspraak-uuid-001");
        return event;
    }
}
