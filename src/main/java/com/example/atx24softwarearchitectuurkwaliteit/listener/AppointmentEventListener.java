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
 * Luistert naar AppointmentChangedEvents van RabbitMQ en stuurt een
 * NotificationQueueMessage door naar de notification queue.
 *
 * De provider wordt per tenant bepaald via de OPENMRS_NOTIFICATION_PROVIDER env var.
 * Fallback is SWIFTSEND als er geen provider geconfigureerd is.
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
        log.info("[STAP 2] AppointmentEvent ontvangen uit RabbitMQ");
        log.info("  Event ID    : {}", event.getEventId());
        log.info("  Tenant      : {}", event.getTenantId());
        log.info("  Patiënt     : {}", event.getPatientName());
        log.info("  Afspraak op : {}", event.getAppointmentDateTime());
        log.info("  Locatie     : {}", event.getLocation());
        log.info("  Status      : {}", event.getStatus());
        log.info("  Wijziging   : {}", event.getChangeType());
        log.info("------------------------------------------------");

        try {
            switch (event.getChangeType()) {
                case "CREATED", "UPDATED" -> verstuurNotificatie(event);
                case "DELETED"            -> log.info("Afspraak geannuleerd — geen notificatie verstuurd voor: {}", event.getAppointmentId());
                default                   -> log.warn("Onbekend changeType '{}' voor event: {}", event.getChangeType(), event.getEventId());
            }
        } catch (Exception e) {
            log.error("Fout bij verwerken appointment event {}: {}", event.getEventId(), e.getMessage(), e);
        }
    }

    private void verstuurNotificatie(AppointmentChangedEvent event) {
        if (event.getAppointmentDateTime() == null) {
            log.warn("Geen afspraaktijd aanwezig voor event: {} — notificatie overgeslagen", event.getEventId());
            return;
        }

        TenantConfiguration tenant = tenantService.getTenantConfiguration(event.getTenantId());
        if (tenant == null) {
            log.error("Tenant '{}' niet gevonden — notificatie kan niet verstuurd worden", event.getTenantId());
            return;
        }

        ProviderType provider = bepaalProvider(tenant.getNotificationProvider());

        NotificationQueueMessage bericht = bouwNotificatieBericht(event, provider);

        log.info("[STAP 2→3] NotificationQueueMessage aangemaakt — doorsturen naar notification queue");
        log.info("  Notification ID : {}", bericht.getNotificationId());
        log.info("  Provider        : {}", provider);
        log.info("  Ontvanger       : {}", bericht.getRecipient());
        log.info("  Onderwerp       : {}", bericht.getSubject());

        producer.publish(bericht);

        log.info("[STAP 2✓] Notificatie gepubliceerd naar RabbitMQ notification queue");
    }

    private NotificationQueueMessage bouwNotificatieBericht(AppointmentChangedEvent event, ProviderType provider) {
        String body = String.format(
                "Herinnering: u heeft een afspraak op %s%s%s.",
                event.getAppointmentDateTime(),
                event.getLocation() != null  ? " | Locatie: " + event.getLocation()     : "",
                event.getPatientName() != null ? " | Patiënt: " + event.getPatientName() : ""
        );

        return new NotificationQueueMessage(
                UUID.randomUUID(),
                event.getPatientId() != null ? event.getPatientId() : "onbekend",
                "Afspraakherinnering",
                body,
                provider,
                "APPOINTMENT_REMINDER",
                Instant.now()
        );
    }

    /**
     * Zet de provider-naam uit de tenant config om naar een ProviderType.
     * Valt terug op SWIFTSEND als de waarde ontbreekt of onbekend is.
     * Stel in via env var: OPENMRS_NOTIFICATION_PROVIDER=SWIFTSEND|LEGACYLINK|ASYNCFLOW|SECUREPOST
     */
    private ProviderType bepaalProvider(String providerNaam) {
        if (providerNaam == null || providerNaam.isBlank()) {
            log.warn("Geen provider geconfigureerd voor tenant — fallback naar SWIFTSEND");
            return ProviderType.SWIFTSEND;
        }
        try {
            return ProviderType.valueOf(providerNaam.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Onbekende provider '{}' — fallback naar SWIFTSEND", providerNaam);
            return ProviderType.SWIFTSEND;
        }
    }
}
