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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AppointmentService {

    private static final Logger log =
            LoggerFactory.getLogger(AppointmentService.class);

    private final RabbitMQProducer rabbitMQProducer;
    private final Environment env;
    private final String notificationProvider;

    public AppointmentService(RabbitMQProducer rabbitMQProducer, Environment env) {
        this.rabbitMQProducer = rabbitMQProducer;
        this.env = env;
        this.notificationProvider = env.getProperty("OPENMRS_NOTIFICATION_PROVIDER", "SWIFTSEND").toUpperCase();
    }

    public void handleAppointment(AppointmentChangedEvent event) {

        log.info("Handling appointment: {} | dateTime={} | changeType={}",
                event.getAppointmentId(), event.getAppointmentDateTime(), event.getChangeType());

        LocalDateTime appointmentTime = event.getAppointmentDateTime();
        Instant appointmentInstant = appointmentTime
                .atZone(ZoneId.systemDefault())
                .toInstant();

        String location = event.getLocation() != null
                ? event.getLocation()
                : "locatie onbekend";

        Instant now = Instant.now();

        /*
         * =========================
         * 24H REMINDER
         * =========================
         */

        Instant reminder24h = appointmentTime
                .minusHours(24)
                .atZone(ZoneId.systemDefault())
                .toInstant();

        Instant cutoff24h = appointmentTime
                .minusHours(23)   // 🔥 grace window (1 hour late allowed)
                .atZone(ZoneId.systemDefault())
                .toInstant();

        if (now.isBefore(cutoff24h)) {

            String body24h =
                    "U heeft over 24 uur een afspraak op " + location +
                            " om " + appointmentTime + ".";

            NotificationQueueMessage notification24h =
                    new NotificationQueueMessage(
                            UUID.randomUUID(),
                            event.getTenantId(),
                            "06 123456",
                            "Afspraak herinnering",
                            body24h,
                            notificationProvider,
                            "APPOINTMENT_REMINDER_24H",
                            reminder24h
                    );

            rabbitMQProducer.publish(notification24h);

            log.info("Queued 24h reminder for appointment {}", event.getAppointmentId());

        } else {
            log.info("24h reminder skipped — appointment not far enough in future | appointment={} | now={} | cutoff24h={}",
                    event.getAppointmentId(), now, cutoff24h);
        }

        /*
         * =========================
         * 1H REMINDER
         * =========================
         */

        Instant reminder1h = appointmentTime
                .minusHours(1)
                .atZone(ZoneId.systemDefault())
                .toInstant();

        Instant cutoff1h     = appointmentInstant;                   // uiterste grens: afspraak mag nog niet begonnen zijn
        Instant window1hMax  = now.plus(12, ChronoUnit.HOURS);      // bovengrens: afspraak moet binnen 12 uur zijn

        if (now.isBefore(cutoff1h) && appointmentInstant.isBefore(window1hMax)) {

            String body1h =
                    "U heeft over 1 uur een afspraak op " + location +
                            " om " + appointmentTime + ".";

            NotificationQueueMessage notification1h =
                    new NotificationQueueMessage(
                            UUID.randomUUID(),
                            event.getTenantId(),
                            "06 123456",
                            "Afspraak herinnering",
                            body1h,
                            notificationProvider,
                            "APPOINTMENT_REMINDER_1H",
                            reminder1h
                    );

            rabbitMQProducer.publish(notification1h);

            log.info("Queued 1h reminder for appointment {}", event.getAppointmentId());

        } else {
            log.info("1h reminder skipped — appointment already started or passed | appointment={} | now={} | cutoff1h={}",
                    event.getAppointmentId(), now, cutoff1h);
        }
    }
}