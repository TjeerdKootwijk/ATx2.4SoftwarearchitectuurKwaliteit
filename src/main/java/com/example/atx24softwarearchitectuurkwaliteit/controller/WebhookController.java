package com.example.atx24softwarearchitectuurkwaliteit.controller;

import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.example.atx24softwarearchitectuurkwaliteit.service.IdempotencyService;
import com.example.atx24softwarearchitectuurkwaliteit.service.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/events")
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);
    private static final String APPOINTMENT_EVENTS_EXCHANGE = "appointment.events";
    private static final String APPOINTMENT_ROUTING_KEY = "appointment.changed";

    @Autowired
    private TenantService tenantService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Webhook endpoint for OpenMRS appointment events
     * Receives HMAC-signed events, validates them, and publishes to RabbitMQ
     * Returns 202 Accepted immediately without waiting for processing
     */
    @PostMapping("/appointment")
    public ResponseEntity<WebhookResponse> handleAppointmentEvent(
            HttpServletRequest request,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Event-Id") String eventId,
            @RequestHeader("X-HMAC-SHA256") String hmacSignature
    ) {
        logger.info("Received appointment event for tenant: {}, event: {}", tenantId, eventId);
        logger.debug("Headers - X-Tenant-Id: {}, X-Event-Id: {}, X-HMAC-SHA256: {}", tenantId, eventId, hmacSignature);

        try {
            // Read raw request body
            String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            // Validate tenant first
            if (!tenantService.isTenantValid(tenantId)) {
                logger.warn("Invalid tenant: {}", tenantId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    new WebhookResponse("INVALID_TENANT", "Tenant not found or inactive")
                );
            }

            // Get tenant config
            TenantConfiguration tenantConfig = tenantService.getTenantConfiguration(tenantId);
            
            // Debug logging
            logger.debug("Received payload: {}", payload);
            logger.debug("Payload length: {}", payload.length());
            logger.debug("Received signature: {}", hmacSignature);
            logger.debug("Secret: {}", tenantConfig.getWebhookSecret());

            // Validate HMAC signature
            if (!idempotencyService.validateHmacSignature(payload, hmacSignature, tenantConfig.getWebhookSecret())) {
                logger.warn("Invalid HMAC signature for tenant: {}", tenantId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    new WebhookResponse("INVALID_SIGNATURE", "HMAC validation failed")
                );
            }

            // Check idempotency
            if (idempotencyService.isEventProcessed(eventId)) {
                logger.info("Event already processed (idempotent): {}", eventId);
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                    new WebhookResponse("ALREADY_PROCESSED", "Event was already processed")
                );
            }

            // Parse event from payload
            AppointmentChangedEvent event = objectMapper.readValue(payload, AppointmentChangedEvent.class);
            event.setEventId(eventId);
            event.setTenantId(tenantId);
            event.setSource("WEBHOOK");

            // Mark as processed
            idempotencyService.markEventAsProcessed(eventId);

            // Publish to RabbitMQ
            rabbitTemplate.convertAndSend(APPOINTMENT_EVENTS_EXCHANGE, APPOINTMENT_ROUTING_KEY, event);

            logger.info("Appointment event published to RabbitMQ: {} for tenant: {}", eventId, tenantId);

            // Return 202 Accepted immediately
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                new WebhookResponse("ACCEPTED", "Event accepted for processing")
            );

        } catch (Exception e) {
            logger.error("Error processing webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new WebhookResponse("PROCESSING_ERROR", e.getMessage())
            );
        }
    }

    /**
     * Health check for webhook endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<WebhookHealthResponse> health() {
        return ResponseEntity.ok(new WebhookHealthResponse("UP", "Webhook service is running"));
    }

    /**
     * Webhook response DTO
     */
    public static class WebhookResponse {
        private String status;
        private String message;
        private long timestamp;

        public WebhookResponse(String status, String message) {
            this.status = status;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Webhook health response DTO
     */
    public static class WebhookHealthResponse {
        private String status;
        private String message;

        public WebhookHealthResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }
}
