package com.example.atx24softwarearchitectuurkwaliteit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyService.class);

    private final DataService dataService;

    public IdempotencyService(DataService dataService) {
        this.dataService = dataService;
    }

    /**
     * Returns true if this event ID was already recorded as processed.
     * Used to skip re-delivery of the same appointment event.
     */
    public boolean isEventProcessed(String eventId) {
        return dataService.isEventProcessed(eventId);
    }

    /** Records the event ID so future calls to {@link #isEventProcessed} return true. */
    public void markEventAsProcessed(String eventId) {
        dataService.markEventAsProcessed(eventId);
    }

    /**
     * Generates a deterministic SHA-256 event ID from tenant, appointment, and change type.
     * Falls back to a random UUID if hashing fails.
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
            logger.error("Failed to generate event ID: {}", e.getMessage());
            return UUID.randomUUID().toString();
        }
    }

    /** Computes an HMAC-SHA256 signature for webhook payload validation. */
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

        StringBuilder sb = new StringBuilder();
        for (byte b : hmacData) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Constant-time string comparison to prevent timing attacks
     * when validating HMAC signatures.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
