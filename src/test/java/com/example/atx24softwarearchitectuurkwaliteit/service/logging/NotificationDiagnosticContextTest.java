package com.example.atx24softwarearchitectuurkwaliteit.service.logging;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationDiagnosticContextTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private NotificationQueueMessage sampleMessage() {
        return new NotificationQueueMessage(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "test-tenant",
                "+31612345678",           // recipient (PII)
                "Afspraakherinnering",     // subject (PII)
                "U heeft morgen een afspraak", // body (PII)
                "SWIFTSEND",
                "SMS",
                Instant.now());
    }

    @Test
    void open_putsNonPiiContextOnMdc() {
        try (NotificationDiagnosticContext ignored =
                     NotificationDiagnosticContext.open(sampleMessage(), 2)) {
            assertEquals("11111111-1111-1111-1111-111111111111", MDC.get(NotificationDiagnosticContext.NOTIFICATION_ID));
            assertEquals("test-tenant", MDC.get(NotificationDiagnosticContext.TENANT_ID));
            assertEquals("SWIFTSEND", MDC.get(NotificationDiagnosticContext.PROVIDER));
            assertEquals("SMS", MDC.get(NotificationDiagnosticContext.MESSAGE_TYPE));
            assertEquals("2", MDC.get(NotificationDiagnosticContext.ATTEMPT));
        }
    }

    @Test
    void open_neverExposesPersonalData() {
        try (NotificationDiagnosticContext ignored =
                     NotificationDiagnosticContext.open(sampleMessage(), 1)) {
            // NFR4/NFR11: recipient, subject en body mogen nooit in de logcontext belanden.
            assertFalse(MDC.getCopyOfContextMap().containsValue("+31612345678"));
            assertFalse(MDC.getCopyOfContextMap().containsValue("Afspraakherinnering"));
            assertFalse(MDC.getCopyOfContextMap().containsValue("U heeft morgen een afspraak"));
        }
    }

    @Test
    void close_clearsContextSoItDoesNotLeak() {
        try (NotificationDiagnosticContext ignored =
                     NotificationDiagnosticContext.open(sampleMessage(), 1)) {
            assertEquals("test-tenant", MDC.get(NotificationDiagnosticContext.TENANT_ID));
        }
        assertNull(MDC.get(NotificationDiagnosticContext.NOTIFICATION_ID));
        assertNull(MDC.get(NotificationDiagnosticContext.TENANT_ID));
        assertNull(MDC.get(NotificationDiagnosticContext.PROVIDER));
        assertNull(MDC.get(NotificationDiagnosticContext.MESSAGE_TYPE));
        assertNull(MDC.get(NotificationDiagnosticContext.ATTEMPT));
    }

    @Test
    void close_restoresPreviousValue() {
        MDC.put(NotificationDiagnosticContext.TENANT_ID, "outer-tenant");
        try (NotificationDiagnosticContext ignored =
                     NotificationDiagnosticContext.open(sampleMessage(), 1)) {
            assertEquals("test-tenant", MDC.get(NotificationDiagnosticContext.TENANT_ID));
        }
        assertEquals("outer-tenant", MDC.get(NotificationDiagnosticContext.TENANT_ID));
    }

    @Test
    void open_skipsZeroAttemptAndNullFields() {
        NotificationQueueMessage partial = new NotificationQueueMessage();
        partial.setNotificationId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        try (NotificationDiagnosticContext ignored =
                     NotificationDiagnosticContext.open(partial, 0)) {
            assertEquals("22222222-2222-2222-2222-222222222222", MDC.get(NotificationDiagnosticContext.NOTIFICATION_ID));
            assertNull(MDC.get(NotificationDiagnosticContext.TENANT_ID));
            assertNull(MDC.get(NotificationDiagnosticContext.ATTEMPT));
        }
    }
}
