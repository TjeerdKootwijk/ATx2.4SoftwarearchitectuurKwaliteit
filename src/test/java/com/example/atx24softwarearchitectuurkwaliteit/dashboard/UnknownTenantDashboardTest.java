package com.example.atx24softwarearchitectuurkwaliteit.dashboard;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.atx24softwarearchitectuurkwaliteit.listener.AppointmentEventListener;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.RabbitMQProducer;
import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.example.atx24softwarearchitectuurkwaliteit.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FMEA-scenario 13 — <em>Onbekende tenantId in binnenkomend bericht</em>.
 *
 * <p>Effect uit de FMEA: "Bericht valt stilzwijgend weg; patiënt krijgt geen notificatie."
 * De maatregel die het dashboard zichtbaar moet maken, is een ERROR-logregel met de tenantId.
 * Die regel verschijnt in het RabbitMQ-dashboard in het paneel
 * <em>"Errors &amp; Warnings (laatste 10 min)"</em> ({@code detected_level=~"error|warn"}),
 * zodat een onbekende tenant niet stil wegvalt.</p>
 *
 * <p>Deze test toont automatisch aan dat de backend bij een onbekende tenant:</p>
 * <ol>
 *   <li>een ERROR logt die de tenantId bevat (zichtbaar + herleidbaar op het dashboard);</li>
 *   <li>géén notificatie publiceert (de patiënt krijgt terecht niets dubbel/fout).</li>
 * </ol>
 */
class UnknownTenantDashboardTest {

    private TenantService tenantService;
    private RabbitMQProducer producer;
    private AppointmentEventListener listener;

    private Logger listenerLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        tenantService = mock(TenantService.class);
        producer = mock(RabbitMQProducer.class);
        listener = new AppointmentEventListener(tenantService, producer);

        listenerLogger = (Logger) LoggerFactory.getLogger(AppointmentEventListener.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        listenerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        listenerLogger.detachAppender(logAppender);
    }

    private AppointmentChangedEvent event(String tenantId) {
        AppointmentChangedEvent event = new AppointmentChangedEvent("evt-1", tenantId, "appt-1");
        event.setChangeType("CREATED");
        event.setAppointmentDateTime(LocalDateTime.now().plusDays(1));
        return event;
    }

    @Test
    void onbekendeTenant_logtErrorMetTenantId_zichtbaarOpDashboard() {
        when(tenantService.getTenantConfiguration("ziekenhuis-onbekend")).thenReturn(null);

        listener.handleAppointmentEvent(event("ziekenhuis-onbekend"));

        ILoggingEvent error = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Verwacht een ERROR-logregel voor de onbekende tenant"));

        // Het ERROR-niveau zorgt dat de regel in het "Errors & Warnings"-paneel verschijnt...
        // ...en de tenantId maakt op het dashboard herleidbaar wélke tenant onbekend was.
        assertTrue(error.getFormattedMessage().contains("ziekenhuis-onbekend"),
                "De foutregel moet de tenantId bevatten zodat het dashboard toont wélke tenant ontbreekt, was: "
                        + error.getFormattedMessage());
    }

    @Test
    void onbekendeTenant_publiceertGeenNotificatie() {
        when(tenantService.getTenantConfiguration("ziekenhuis-onbekend")).thenReturn(null);

        listener.handleAppointmentEvent(event("ziekenhuis-onbekend"));

        verify(producer, never()).publish(any());
    }

    @Test
    void bekendeTenant_publiceert_enLogtGeenError() {
        TenantConfiguration tenant = new TenantConfiguration("ziekenhuis-a", "Ziekenhuis A");
        tenant.setNotificationProvider("SWIFTSEND");
        when(tenantService.getTenantConfiguration("ziekenhuis-a")).thenReturn(tenant);

        listener.handleAppointmentEvent(event("ziekenhuis-a"));

        // Positieve controle: bij een bekende tenant gaat het bericht door en blijft het
        // dashboard-foutpaneel leeg voor dit event.
        verify(producer).publish(any());
        List<ILoggingEvent> errors = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .toList();
        assertTrue(errors.isEmpty(), "Een bekende tenant mag geen ERROR op het dashboard zetten");
    }
}
