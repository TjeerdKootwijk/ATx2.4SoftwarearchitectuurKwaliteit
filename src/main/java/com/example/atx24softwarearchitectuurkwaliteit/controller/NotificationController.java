package com.example.atx24softwarearchitectuurkwaliteit.controller;

import com.example.atx24softwarearchitectuurkwaliteit.dto.NotificationRequest;
import com.example.atx24softwarearchitectuurkwaliteit.model.Notification;
import com.example.atx24softwarearchitectuurkwaliteit.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    @Autowired
    private NotificationService notificationService;

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Notification service is running ✓");
    }

    /**
     * Receive a new notification
     * POST request with notification details
     */
    @PostMapping("/receive")
    public ResponseEntity<Notification> receiveNotification(@RequestBody NotificationRequest request) {
        logger.info("Received notification request from: {}", request.getSource());

        // Validate request
        if (request.getTitle() == null || request.getTitle().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Process the notification
        Notification processedNotification = notificationService.processNotification(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(processedNotification);
    }

    /**
     * Generic endpoint for notifications (alternative route)
     */
    @PostMapping
    public ResponseEntity<Notification> createNotification(@RequestBody NotificationRequest request) {
        logger.info("Creating notification: {}", request.getTitle());
        
        Notification processedNotification = notificationService.processNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(processedNotification);
    }

    /**
     * Status endpoint to check if service is operational
     */
    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status() {
        StatusResponse response = new StatusResponse();
        response.setStatus("UP");
        response.setMessage("Notification service is operational");
        response.setTimestamp(System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    // Inner class for status response
    public static class StatusResponse {
        private String status;
        private String message;
        private long timestamp;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
