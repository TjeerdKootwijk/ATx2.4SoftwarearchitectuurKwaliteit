package com.example.atx24softwarearchitectuurkwaliteit.listener;

import com.example.atx24softwarearchitectuurkwaliteit.config.RabbitMQConfig;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.RabbitMQProducer;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import com.example.atx24softwarearchitectuurkwaliteit.service.TenantService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Listens for {@link AppointmentChangedEvent} messages on the appointment queue
 * and forwards a {@link NotificationQueueMessage} to the notification queue.
 *
 * The messaging provider is resolved per tenant via the
 * {@code OPENMRS_NOTIFICATION_PROVIDER} environment variable.
 * Falls back to SWIFTSEND when no provider is configured.
 */
@Component
public class AppointmentEventListener {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventListener.class);

    private final TenantService tenantService;
    private final RabbitMQProducer producer;

    public AppointmentEventListener(TenantService tenantService, RabbitMQProducer producer) {
        this.tenantService = tenantService;
        this.producer = producer;
    }

    @RabbitListener(queues = RabbitMQConfig.APPOINTMENT_QUEUE)
    public void handleAppointmentEvent(AppointmentChangedEvent event) {
        log.info("------------------------------------------------");
        log.info("[STEP 2] AppointmentEvent received from RabbitMQ");
        log.info("  Event ID    : {}", event.getEventId());
        log.info("  Tenant      : {}", event.getTenantId());
        log.info("  Appointment : {}", event.getAppointmentDateTime());
        log.info("  Status      : {}", event.getStatus());
        log.info("  Change type : {}", event.getChangeType());
        log.info("------------------------------------------------");

        try {
            switch (event.getChangeType()) {
                case "CREATED", "UPDATED" -> sendNotification(event);
                case "DELETED"            -> log.info("Appointment cancelled — no notification sent for: {}", event.getAppointmentId());
                default                   -> log.warn("Unknown changeType '{}' for event: {}", event.getChangeType(), event.getEventId());
            }
        } catch (Exception e) {
            log.error("Error processing appointment event {}: {}", event.getEventId(), e.getMessage(), e);
        }
    }

    private void sendNotification(AppointmentChangedEvent event) {
        if (event.getAppointmentDateTime() == null) {
            log.warn("No appointment time present for event: {} — notification skipped", event.getEventId());
            return;
        }

        TenantConfiguration tenant = tenantService.getTenantConfiguration(event.getTenantId());
        if (tenant == null) {
            log.error("Tenant '{}' not found — cannot send notification", event.getTenantId());
            return;
        }

        String provider = resolveProvider(tenant.getNotificationProvider());
        NotificationQueueMessage message = buildNotificationMessage(event, provider);

        log.info("[STEP 2→3] NotificationQueueMessage created — forwarding to notification queue");
        log.info("  Notification ID : {}", message.getNotificationId());
        log.info("  Provider        : {}", provider);

        producer.publish(message);

        log.info("[STEP 2✓] Notification published to RabbitMQ notification queue");
    }

    private NotificationQueueMessage buildNotificationMessage(AppointmentChangedEvent event, String provider) {
        String body = String.format(
                "Reminder: you have an appointment on %s%s%s.",
                event.getAppointmentDateTime(),
                event.getLocation()   != null ? " | Location: " + event.getLocation()   : "",
                event.getPatientName() != null ? " | Patient: "  + event.getPatientName() : ""
        );

        return new NotificationQueueMessage(
                UUID.randomUUID(),
                event.getTenantId(),
                event.getPatientId() != null ? event.getPatientId() : "unknown",
                "Appointment Reminder",
                body,
                provider,
                "APPOINTMENT_REMINDER",
                Instant.now()
        );
    }

    /**
     * Normaliseert de provider-naam uit de tenant-configuratie naar hoofdletters.
     * Falls back to SWIFTSEND if the value is missing.
     * Configure via: OPENMRS_NOTIFICATION_PROVIDER=SWIFTSEND|LEGACYLINK|ASYNCFLOW|SECUREPOST
     */
    private String resolveProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            log.warn("No provider configured for tenant — falling back to SWIFTSEND");
            return ProviderType.SWIFTSEND;
        }
        return providerName.trim().toUpperCase();
    }
}
