package com.example.atx24softwarearchitectuurkwaliteit.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter that transparently encrypts sensitive column values
 * before writing to the database and decrypts them after reading.
 *
 * Algorithm : AES-256-GCM (authenticated encryption — detects tampering)
 * Key source : environment variable AES_ENCRYPTION_KEY (Base64-encoded, 32 bytes)
 * Storage    : Base64( IV[12 bytes] + ciphertext + GCM auth tag[16 bytes] )
 *
 * NFR5: "All sensitive data must be encrypted with at least AES-256 for storage."
 *
 * Usage: annotate a String field in an @Entity with
 *   @Convert(converter = AesEncryptionConverter.class)
 */
@Converter
@Component
public class AesEncryptionConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(AesEncryptionConverter.class);

    private static final String ALGORITHM  = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH  = 12;   // 96-bit IV recommended for GCM
    private static final int    TAG_LENGTH = 128;  // 128-bit authentication tag

    /**
     * 32-byte (256-bit) AES key, Base64-encoded.
     * Set via environment variable: AES_ENCRYPTION_KEY
     * Generate a key with: openssl rand -base64 32
     */
    @Value("${AES_ENCRYPTION_KEY}")
    private String base64Key;

    // ── Encrypt (Java value → DB column) ────────────────────────────────────

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) return null;

        try {
            SecretKey key    = buildKey();
            byte[]    iv     = generateIv();
            Cipher    cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV so it is available during decryption
            byte[] payload = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, payload, IV_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(payload);

        } catch (Exception e) {
            log.error("Encryption failed — refusing to write plaintext to database", e);
            throw new IllegalStateException("AES-256-GCM encryption failed", e);
        }
    }

    // ── Decrypt (DB column → Java value) ────────────────────────────────────

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) return null;

        try {
            byte[] payload    = Base64.getDecoder().decode(stored);
            byte[] iv         = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[payload.length - IV_LENGTH];

            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, ciphertext, 0, ciphertext.length);

            SecretKey key    = buildKey();
            Cipher    cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed — data may be corrupted or key may have changed", e);
            throw new IllegalStateException("AES-256-GCM decryption failed", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SecretKey buildKey() {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                "AES_ENCRYPTION_KEY must be 32 bytes (256 bits) after Base64 decoding, got: "
                + keyBytes.length + " bytes");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}
