package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import com.example.atx24softwarearchitectuurkwaliteit.service.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * SRP: verantwoordelijk voor het publiceren van een AppointmentChangedEvent naar RabbitMQ.
 *      Controleert idempotency zodat dezelfde afspraak niet dubbel verwerkt wordt.
 *
 * DIP: PollingJob hangt af van de AppointmentEventPublisher interface, niet van deze klasse direct.
 *
 * Dekt NFR 6: queueing en retry-mechanisme (dead-letter queue geconfigureerd in RabbitMQConfig).
 */
@Component
public class RabbitMqAppointmentEventPublisher implements AppointmentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqAppointmentEventPublisher.class);
    private static final String APPOINTMENT_EXCHANGE = "appointment.events";
    private static final String APPOINTMENT_ROUTING_KEY = "appointment.changed";

    private final RabbitTemplate rabbitTemplate;
    private final IdempotencyService idempotencyService;

    public RabbitMqAppointmentEventPublisher(RabbitTemplate rabbitTemplate,
                                             IdempotencyService idempotencyService) {
        this.rabbitTemplate = rabbitTemplate;
        this.idempotencyService = idempotencyService;
    }

    @Override
    public void publish(AppointmentChangedEvent event) {
        if (idempotencyService.isEventProcessed(event.getEventId())) {
            log.debug("Skipping duplicate event: {}", event.getEventId());
            return;
        }

        idempotencyService.markEventAsProcessed(event.getEventId());
        rabbitTemplate.convertAndSend(APPOINTMENT_EXCHANGE, APPOINTMENT_ROUTING_KEY, event);

        log.info("------------------------------------------------");
        log.info("[STAP 1✓] AppointmentChangedEvent gepubliceerd naar RabbitMQ");
        log.info("  Event ID    : {}", event.getEventId());
        log.info("  Tenant      : {}", event.getTenantId());
        log.info("  Afspraak ID : {}", event.getAppointmentUuid());
        log.info("  Patiënt     : {}", event.getPatientName());
        log.info("  Tijd        : {}", event.getAppointmentDateTime());
        log.info("------------------------------------------------");
    }
}
