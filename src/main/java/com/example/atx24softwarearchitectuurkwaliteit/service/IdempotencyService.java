package com.example.atx24softwarearchitectuurkwaliteit.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class IdempotencyService {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyService.class);
    
    // In-memory store for processed events (in production, use database)
    private final Set<String> processedEventIds = Collections.synchronizedSet(new HashSet<>());

    /**
     * Check if event was already processed
     * @param eventId Unique event identifier
     * @return true if event was already processed, false if new
     */
    public boolean isEventProcessed(String eventId) {
        return processedEventIds.contains(eventId);
    }

    /**
     * Mark event as processed
     * @param eventId Unique event identifier
     */
    public void markEventAsProcessed(String eventId) {
        processedEventIds.add(eventId);
        logger.debug("Event marked as processed: {}", eventId);
    }

    /**
     * Validate HMAC-SHA256 signature from webhook
     * @param payload Request body
     * @param signature HMAC signature from header
     * @param secret Webhook secret
     * @return true if signature is valid
     */
    public boolean validateHmacSignature(String payload, String signature, String secret) {
        try {
            String computedSignature = computeHmac(payload, secret);
            boolean isValid = constantTimeEquals(computedSignature, signature);
            
            if (!isValid) {
                logger.warn("Invalid HMAC signature detected");
                logger.warn("Received signature: {}", signature);
                logger.warn("Computed signature: {}", computedSignature);
                logger.warn("Payload length: {}", payload.length());
                logger.warn("Payload (first 200 chars): {}", payload.length() > 200 ? payload.substring(0, 200) : payload);
            }
            
            return isValid;
        } catch (Exception e) {
            logger.error("Error validating HMAC signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Compute HMAC-SHA256
     */
    private String computeHmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8),
            0,
            secret.getBytes(StandardCharsets.UTF_8).length,
            "HmacSHA256"
        );
        mac.init(secretKeySpec);
        byte[] hmacData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        
        // Convert to hex string
        StringBuilder sb = new StringBuilder();
        for (byte b : hmacData) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Constant time comparison to prevent timing attacks
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * Generate event ID from appointment data
     */
    public String generateEventId(String tenantId, String appointmentId, String changeType) {
        String input = String.format("%s:%s:%s:%d", tenantId, appointmentId, changeType, System.currentTimeMillis());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            logger.error("Error generating event ID: {}", e.getMessage());
            return UUID.randomUUID().toString();
        }
    }
}
