package com.example.atx24softwarearchitectuurkwaliteit.listener;

import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Consumes appointment events from RabbitMQ queue
 * Schedules notifications for 24 hours and 1 hour before appointment
 */
@Component
public class AppointmentEventListener {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentEventListener.class);

    @RabbitListener(queues = "appointment.events.queue")
    public void handleAppointmentEvent(AppointmentChangedEvent event) {
        logger.info("Received appointment event: {} for tenant: {}", event.getEventId(), event.getTenantId());

        try {
            switch (event.getChangeType()) {
                case "CREATED":
                case "UPDATED":
                    processAppointmentScheduling(event);
                    break;
                case "DELETED":
                    processCancelledAppointment(event);
                    break;
                case "RESCHEDULED":
                    processCancelledAppointment(event);
                    processAppointmentScheduling(event);
                    break;
                default:
                    logger.warn("Unknown change type: {}", event.getChangeType());
            }
        } catch (Exception e) {
            logger.error("Error processing appointment event: {}", e.getMessage());
        }
    }

    /**
     * Schedule notifications for new/updated appointment
     * Notifications at: 24 hours before, 1 hour before
     */
    private void processAppointmentScheduling(AppointmentChangedEvent event) {
        if (event.getAppointmentDateTime() == null) {
            logger.warn("No appointment date/time in event: {}", event.getEventId());
            return;
        }

        LocalDateTime appointmentTime = event.getAppointmentDateTime();
        LocalDateTime now = LocalDateTime.now();

        // Calculate notification times
        LocalDateTime notification24hBefore = appointmentTime.minus(24, ChronoUnit.HOURS);
        LocalDateTime notification1hBefore = appointmentTime.minus(1, ChronoUnit.HOURS);

        logger.info("Scheduling notifications for appointment: {}", event.getAppointmentId());
        logger.debug("Appointment time: {}", appointmentTime);
        logger.debug("24h notification at: {}", notification24hBefore);
        logger.debug("1h notification at: {}", notification1hBefore);

        // In production, persist these scheduled notifications to database
        // with a scheduler to check and send them at the right time
    }

    /**
     * Handle cancelled appointment
     * Cancel any scheduled notifications
     */
    private void processCancelledAppointment(AppointmentChangedEvent event) {
        logger.info("Processing cancelled appointment: {} for tenant: {}",
            event.getAppointmentId(), event.getTenantId());

        // In production:
        // 1. Find all scheduled notifications for this appointment
        // 2. Mark them as cancelled
        // 3. Remove from notification queue
    }
}
