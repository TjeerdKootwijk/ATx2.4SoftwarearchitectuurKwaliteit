package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.RabbitMQProducer;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final TenantService tenantService;

    private final String notificationProvider;


    public AppointmentService(RabbitMQProducer rabbitMQProducer, TenantService tenantService) {
        this.rabbitMQProducer = rabbitMQProducer;
        this.tenantService = tenantService;
    }

    public void handleAppointment(AppointmentChangedEvent event) {

        log.info("Handling appointment: {} | dateTime={} | changeType={}",
                event.getAppointmentId(),
                event.getAppointmentDateTime(),
                event.getChangeType());

        // Zoek de provider op bij de juiste tenant — niet globaal uit env vars
        TenantConfiguration tenant = tenantService.getTenantConfiguration(event.getTenantId());
        String notificationProvider = (tenant != null && tenant.getNotificationProvider() != null)
                ? tenant.getNotificationProvider().toUpperCase()
                : "SWIFTSEND";

        log.debug("Using provider {} for tenant {}", notificationProvider, event.getTenantId());

        LocalDateTime appointmentTime = event.getAppointmentDateTime();

        Instant appointmentInstant = appointmentTime
                .atZone(ZoneId.systemDefault())
                .toInstant();

        Instant now = Instant.now();

        String location = event.getLocation() != null
                ? event.getLocation()
                : "locatie onbekend";

        /*
         * =========================
         * 24H WINDOW
         * =========================
         *
         * Only send if appointment is:
         * - in the future
         * - within next 24–48h window (your rule is 24h reminder)
         */

        Instant start24hWindow = appointmentInstant.minus(24, ChronoUnit.HOURS);
        Instant end24hWindow = appointmentInstant.minus(23, ChronoUnit.HOURS);

        boolean in24hWindow = now.isAfter(start24hWindow) && now.isBefore(end24hWindow);

        if (in24hWindow) {

            NotificationQueueMessage notification24h =
                    new NotificationQueueMessage(
                            UUID.randomUUID(),
                            event.getTenantId(),
                            "06 123456",
                            "Afspraak herinnering",
                            "U heeft over 24 uur een afspraak op " + location +
                                    " om " + appointmentTime + ".",
                            notificationProvider,
                            "APPOINTMENT_REMINDER_24H",
                            Instant.now()
                    );

            rabbitMQProducer.publish(notification24h);

            log.info("Sent 24h reminder for appointment {}",
                    event.getAppointmentId());
        }
        else{
            log.info("Not in 24h window for appointment {} (now={}, appointmentTime={})",event.getAppointmentId(), now, appointmentInstant);
        }
        /*
         * =========================
         * 1H WINDOW
         * =========================
         *
         * Only send if appointment is:
         * - in the future
         * - within last 1 hour before appointment
         */

        Instant start1hWindow = appointmentInstant.minus(1, ChronoUnit.HOURS);

        boolean in1hWindow = now.isAfter(start1hWindow)
                && now.isBefore(appointmentInstant);

        if (in1hWindow) {

            NotificationQueueMessage notification1h =
                    new NotificationQueueMessage(
                            UUID.randomUUID(),
                            event.getTenantId(),
                            "06 123456",
                            "Afspraak herinnering",
                            "U heeft over 1 uur een afspraak op " + location +
                                    " om " + appointmentTime + ".",
                            notificationProvider,
                            "APPOINTMENT_REMINDER_1H",
                            Instant.now()
                    );

            rabbitMQProducer.publish(notification1h);

            log.info("Sent 1h reminder for appointment {}",
                    event.getAppointmentId());
        }
        else{
            log.info("Not in 1h window for appointment {} (now={}, appointmentTime={})",event.getAppointmentId(), now, appointmentInstant);
        }
    }
}