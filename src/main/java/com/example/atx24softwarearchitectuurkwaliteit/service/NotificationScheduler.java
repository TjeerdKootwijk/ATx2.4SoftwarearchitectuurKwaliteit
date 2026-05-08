package com.example.atx24softwarearchitectuurkwaliteit.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Scheduler for sending appointment reminder notifications
 * Checks for appointments that need notifications at:
 * - 24 hours before
 * - 1 hour before
 */
@Service
public class NotificationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NotificationScheduler.class);

    /**
     * Run every minute to check for notifications that should be sent
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)  // Every 1 minute, start after 30 seconds
    public void checkAndSendScheduledNotifications() {
        logger.debug("Checking for notifications to send");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in24Hours = now.plus(24, ChronoUnit.HOURS);
        LocalDateTime in1Hour = now.plus(1, ChronoUnit.HOURS);

        // In production:
        // 1. Query database for appointments with scheduled notifications
        // 2. Filter by current time window (24h and 1h before)
        // 3. For each appointment, send notification via appropriate provider
        // 4. Mark notification as sent in database

        logger.debug("Checking appointments for 24h notifications: {} to {}", now, in24Hours);
        logger.debug("Checking appointments for 1h notifications: {} to {}", now, in1Hour);
    }

    /**
     * Send SMS reminder notification
     */
    private void sendSmsReminder(String tenantId, String patientPhone, String message) {
        logger.info("Sending SMS reminder for tenant {}: {}", tenantId, message);
        // TODO: Integrate with SMS provider (SwiftSend, LegacyLink, etc.)
    }

    /**
     * Send Email reminder notification
     */
    private void sendEmailReminder(String tenantId, String patientEmail, String subject, String message) {
        logger.info("Sending Email reminder for tenant {}: {}", tenantId, subject);
        // TODO: Integrate with Email provider (AsyncFlow, SecurePost, etc.)
    }

    /**
     * Send Push notification
     */
    private void sendPushNotification(String tenantId, String deviceId, String title, String message) {
        logger.info("Sending Push notification for tenant {}: {}", tenantId, title);
        // TODO: Integrate with Push notification provider
    }
}
