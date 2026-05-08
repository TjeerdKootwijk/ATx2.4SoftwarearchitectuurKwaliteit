package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.dto.NotificationRequest;
import com.example.atx24softwarearchitectuurkwaliteit.model.Notification;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private static final String NOTIFICATION_EXCHANGE = "notifications.exchange";
    private static final String NOTIFICATION_ROUTING_KEY = "notification.created";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * Process an incoming notification request
     * Creates a Notification object and publishes it to RabbitMQ
     */
    public Notification processNotification(NotificationRequest request) {
        logger.info("Processing notification request: {}", request.getTitle());

        // Create notification object
        Notification notification = new Notification(
                request.getTitle(),
                request.getMessage(),
                request.getType(),
                request.getSource(),
                request.getRecipientId()
        );

        // Set unique ID
        notification.setId(UUID.randomUUID().toString());

        try {
            // Publish to RabbitMQ
            rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, NOTIFICATION_ROUTING_KEY, notification);
            notification.setStatus("SENT");
            notification.setSentAt(LocalDateTime.now());
            logger.info("Notification published to RabbitMQ: {}", notification.getId());
        } catch (Exception e) {
            notification.setStatus("FAILED");
            logger.error("Failed to publish notification: {}", e.getMessage());
        }

        return notification;
    }

    /**
     * Handle notification message from RabbitMQ queue
     */
    public void handleNotificationMessage(Notification notification) {
        logger.info("Handling notification message: {} - {}", notification.getId(), notification.getTitle());
        // Here you can implement the actual notification logic
        // e.g., send email, SMS, push notification, etc.
    }
}
