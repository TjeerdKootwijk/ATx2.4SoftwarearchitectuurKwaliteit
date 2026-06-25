package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.RabbitMQProducer;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AppointmentService {

    private static final Logger log =
            LoggerFactory.getLogger(AppointmentService.class);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.forLanguageTag("nl"));
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    private final RabbitMQProducer rabbitMQProducer;
    private final String defaultNotificationProvider;

    public AppointmentService(RabbitMQProducer rabbitMQProducer, Environment env) {
        this.rabbitMQProducer = rabbitMQProducer;
        // Globale fallback als de tenant geen eigen provider heeft geconfigureerd
        this.defaultNotificationProvider = env.getProperty("OPENMRS_NOTIFICATION_PROVIDER", "SWIFTSEND").toUpperCase();
    }

    public void handleAppointment(AppointmentChangedEvent event) {

        log.info("Handling appointment: {} | dateTime={} | changeType={}",
                event.getAppointmentId(),
                event.getAppointmentDateTime(),
                event.getChangeType());

        LocalDateTime appointmentTime = event.getAppointmentDateTime();

        Instant appointmentInstant = appointmentTime
                .atZone(ZoneId.of(event.getTimezone()))
                .toInstant();

        Instant now = Instant.now();

        // Per-tenant provider; val terug op de globale default als niet gezet
        String provider = (event.getNotificationProvider() != null && !event.getNotificationProvider().isBlank())
                ? event.getNotificationProvider().toUpperCase()
                : defaultNotificationProvider;

        String location = event.getLocation() != null
                ? event.getLocation()
                : "locatie onbekend";

        // Naam voor in de berichttekst
        String patientName = event.getPatientName() != null
                ? event.getPatientName()
                : "Geachte patiënt";

        // Telefoonnummer van de patiënt (afkomstig uit OpenMRS person.attributes)
        String phone = event.getPatientPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("No phone number found for appointment {} (patient: {}), notification will be sent without recipient",
                    event.getAppointmentId(), event.getPatientName());
            phone = "onbekend";
        }

        String datumStr = appointmentTime.format(DATE_FMT);
        String tijdStr  = appointmentTime.format(TIME_FMT);

        /*
         * =========================
         * 24H WINDOW
         * =========================
         *
         * Venster: 21 tot 27 uur vóór de afspraak (6 uur breed).
         * Zo wordt een afspraak die ~24h weg is altijd opgepikt,
         * ongeacht kleine timing-afwijkingen in het script of de pollcyclus.
         */

        Instant start24hWindow = appointmentInstant.minus(27, ChronoUnit.HOURS);
        Instant end24hWindow   = appointmentInstant.minus(21, ChronoUnit.HOURS);

        boolean in24hWindow = now.isAfter(start24hWindow) && now.isBefore(end24hWindow);

        if (in24hWindow) {
            NotificationQueueMessage notification24h =
                    new NotificationQueueMessage(
                            UUID.randomUUID(),
                            event.getTenantId(),
                            phone,
                            "Afspraak herinnering",
                            patientName + ", u heeft morgen een afspraak op " + location +
                                    " om " + tijdStr + " uur (" + datumStr + ").",
                            provider,
                            "APPOINTMENT_REMINDER_24H",
                            Instant.now()
                    );

            rabbitMQProducer.publish(notification24h);
            log.info("Sent 24h reminder for appointment {}", event.getAppointmentId());
        } else {
            log.info("Not in 24h window for appointment {} (now={}, appointmentTime={})",
                    event.getAppointmentId(), now, appointmentInstant);
        }

        /*
         * =========================
         * 1H WINDOW
         * =========================
         *
         * Venster: 0 tot 1 uur vóór de afspraak.
         */

        Instant start1hWindow = appointmentInstant.minus(1, ChronoUnit.HOURS);

        boolean in1hWindow = now.isAfter(start1hWindow)
                && now.isBefore(appointmentInstant);

        if (in1hWindow) {
            NotificationQueueMessage notification1h =
                    new NotificationQueueMessage(
                            UUID.randomUUID(),
                            event.getTenantId(),
                            phone,
                            "Afspraak herinnering",
                            patientName + ", over ongeveer een uur heeft u een afspraak op " +
                                    location + " om " + tijdStr + " uur.",
                            provider,
                            "APPOINTMENT_REMINDER_1H",
                            Instant.now()
                    );

            rabbitMQProducer.publish(notification1h);
            log.info("Sent 1h reminder for appointment {}", event.getAppointmentId());
        } else {
            log.info("Not in 1h window for appointment {} (now={}, appointmentTime={})",
                    event.getAppointmentId(), now, appointmentInstant);
        }
    }
}
