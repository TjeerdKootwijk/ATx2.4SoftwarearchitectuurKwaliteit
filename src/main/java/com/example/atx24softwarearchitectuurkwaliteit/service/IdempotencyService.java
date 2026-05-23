package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Prevents duplicate processing of appointment events.
 *
 * Previously used an in-memory HashSet, which lost its state on every restart.
 * Now delegates to {@link DataService}, which persists processed event IDs
 * in the {@code processed_events} table — surviving restarts and horizontal scaling.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final DataService dataService;

    public IdempotencyService(DataService dataService) {
        this.dataService = dataService;
    }

    /**
     * Combined idempotency check for an appointment event.
     *
     * Returns {@code true} if the event is new and should be processed.
     * Returns {@code false} if the event was already seen — caller should skip it.
     *
     * When the event is new, it is immediately marked as processed so concurrent
     * or future deliveries of the same event are rejected.
     */
    public boolean processAppointment(AppointmentChangedEvent event) {
        String eventId = generateEventId(
                event.getTenantId(),
                event.getAppointmentId(),
                event.getChangeType()
        );

        log.debug("Checking idempotency for appointment {} (eventId={})",
                event.getAppointmentId(), eventId);

        if (dataService.isEventProcessed(eventId)) {
            log.info("Duplicate appointment skipped: {} (eventId={})",
                    event.getAppointmentId(), eventId);
            return false;
        }

        dataService.markEventAsProcessed(eventId);
        log.info("New appointment accepted: {} (eventId={})",
                event.getAppointmentId(), eventId);
        return true;
    }

    /**
     * Returns true if this event ID was already recorded as processed.
     * Used for direct event ID checks outside of an AppointmentChangedEvent context.
     */
    public boolean isEventProcessed(String eventId) {
        return dataService.isEventProcessed(eventId);
    }

    /** Records the event ID so future calls to {@link #isEventProcessed} return true. */
    public void markEventAsProcessed(String eventId) {
        dataService.markEventAsProcessed(eventId);
    }

    /**
     * Generates a SHA-256 event ID from tenant, appointment ID, and change type.
     * Falls back to a random UUID if hashing fails.
     */
    public String generateEventId(String tenantId, String appointmentId, String changeType) {
        String input = String.format("%s:%s:%s", tenantId, appointmentId, changeType);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to generate event ID for appointment {}: {}", appointmentId, e.getMessage());
            return UUID.randomUUID().toString();
        }
    }
}