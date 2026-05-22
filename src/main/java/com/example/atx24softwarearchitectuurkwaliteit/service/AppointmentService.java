package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.RabbitMQProducer;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
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
    private final ProviderType notificationProvider;

    public AppointmentService(RabbitMQProducer rabbitMQProducer, Environment env) {
        this.rabbitMQProducer = rabbitMQProducer;
        this.env = env;
        String providerName = env.getProperty("OPENMRS_NOTIFICATION_PROVIDER", "SWIFTSEND");
        this.notificationProvider = ProviderType.valueOf(providerName.toUpperCase());
    }

    public void handleAppointment(AppointmentChangedEvent event) {

        log.info("Handling appointment: {}",
                event.getAppointmentId());

        /*
         * Appointment time
         *
         * Assumes AppointmentChangedEvent contains:
         * event.getAppointmentTime()
         */
        LocalDateTime appointmentTime = event.getAppointmentDateTime();

        /*
         * Calculate notification moments
         */
        LocalDateTime reminder24hLocal = appointmentTime.minus(24, ChronoUnit.HOURS);
        LocalDateTime reminder1hLocal = appointmentTime.minus(1, ChronoUnit.HOURS);

        Instant reminder24h = reminder24hLocal.atZone(ZoneId.systemDefault()).toInstant();
        Instant reminder1h = reminder1hLocal.atZone(ZoneId.systemDefault()).toInstant();

        /*
         * Get location or use default
         */
        String location = event.getLocation() != null ? event.getLocation() : "locatie onbekend";

        /*
         * Create 24-hour reminder
         */
        String body24h = "U heeft over 24 uur een afspraak op " + location + " om " + appointmentTime + ".";
        NotificationQueueMessage notification24h =
                new NotificationQueueMessage(
                        UUID.randomUUID(),
                        "06 123456",
                        "Afspraak herinnering",
                        body24h,
                        notificationProvider,
                        "APPOINTMENT_REMINDER_24H",
                        reminder24h
                );

        /*
         * Create 1-hour reminder
         */
        String body1h = "U heeft over 1 uur een afspraak op " + location + " om " + appointmentTime + ".";
        NotificationQueueMessage notification1h =
                new NotificationQueueMessage(
                        UUID.randomUUID(),
                        "06 123456",
                        "Afspraak herinnering",
                        body1h,
                        notificationProvider,
                        "APPOINTMENT_REMINDER_1H",
                        reminder1h
                );

        /*
         * Publish to RabbitMQ
         */
        rabbitMQProducer.publish(notification24h);
        rabbitMQProducer.publish(notification1h);

        log.info("Queued 24h and 1h reminders for appointment {}",
                event.getAppointmentId());
    }
}