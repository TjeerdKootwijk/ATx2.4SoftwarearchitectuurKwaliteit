package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * StartupService — herstelt onverwerkte appointment events bij opstart.
 *
 * Verantwoordelijkheid (SRP):
 *   Bij opstart haalt deze service via de DatabaseService alle AppointmentChangedEvents op
 *   die nog niet verwerkt zijn. Elk event wordt aangeboden aan de IdempotencyService,
 *   die zelf controleert op duplicaten en bij een nieuw event de volledige verwerkingsketen
 *   (inclusief notificatie via RabbitMQ) in gang zet.
 *
 * Afhankelijkheden (DIP):
 *   - DatabaseService    — levert de onverwerkte events (gemaakt door iemand anders)
 *   - IdempotencyService — verwerkt het event en voorkomt duplicaten
 *
 * Wanneer dit draait:
 *   Na {@link ApplicationReadyEvent}, zodat RabbitMQ en de database al
 *   volledig beschikbaar zijn voordat events worden aangeboden.
 */
@Service
public class StartupService {

    private static final Logger log = LoggerFactory.getLogger(StartupService.class);

    private final DatabaseService databaseService;
    private final IdempotencyService idempotencyService;

    public StartupService(DatabaseService databaseService,
                          IdempotencyService idempotencyService) {
        this.databaseService = databaseService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Wordt automatisch aangeroepen zodra de applicatie volledig opgestart is.
     * Haalt onverwerkte appointment events op en biedt ze opnieuw aan via de IdempotencyService.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reprocessUnsentAppointments() {
        log.info("=== StartupService: onverwerkte afspraken ophalen ===");

        List<AppointmentChangedEvent> unprocessedEvents;

        try {
            unprocessedEvents = databaseService.getUnprocessedAppointmentEvents();
        } catch (Exception e) {
            log.error("StartupService: kon onverwerkte events niet ophalen uit database: {}", e.getMessage(), e);
            return;
        }

        if (unprocessedEvents.isEmpty()) {
            log.info("StartupService: geen onverwerkte events gevonden.");
            return;
        }

        log.info("StartupService: {} onverwerkt event(s) gevonden — opnieuw aanbieden.", unprocessedEvents.size());

        int reprocessed = 0;
        int failed = 0;

        for (AppointmentChangedEvent event : unprocessedEvents) {
            try {
                // IdempotencyService controleert zelf op duplicaten
                // en zet de volledige verwerkingsketen in gang bij een nieuw event
                idempotencyService.processAppointment(event);
                reprocessed++;

                log.info("StartupService: event {} opnieuw aangeboden | appointment={}",
                        event.getEventId(), event.getAppointmentId());

            } catch (Exception e) {
                failed++;
                log.error("StartupService: fout bij opnieuw verwerken van event {}: {}",
                        event.getEventId(), e.getMessage(), e);
            }
        }

        log.info("=== StartupService klaar: {} verwerkt, {} mislukt ===", reprocessed, failed);
    }
}