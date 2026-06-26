package com.example.atx24softwarearchitectuurkwaliteit.service.logging;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Plaatst de niet-PII <em>input-context</em> van een notificatie in de SLF4J {@link MDC},
 * zodat elke logregel die tijdens de verwerking ontstaat — inclusief stacktraces van fouten —
 * automatisch herleidbaar is naar de ingevulde gegevens waarmee het misging.
 *
 * <p>De OpenTelemetry-agent exporteert MDC-velden als structured log-attributen naar Loki en
 * voegt zelf {@code trace_id}/{@code span_id} toe. In Grafana zie je daardoor per fout:
 * de stacktrace (klasse + regelnummer), de input-context hieronder, en een klikbare trace.</p>
 *
 * <p><strong>Privacy (NFR4/NFR11):</strong> persoonsgegevens en afspraakdetails
 * ({@code recipient}, {@code subject}, {@code body}) worden bewust <em>niet</em> opgenomen.
 * De echte data blijft herleidbaar via {@link #NOTIFICATION_ID} in de database.</p>
 *
 * <p>Gebruik als try-with-resources zodat de context na verwerking exact wordt opgeruimd en
 * niet lekt naar het volgende bericht op dezelfde thread:</p>
 * <pre>{@code
 * try (var ctx = NotificationDiagnosticContext.open(message, attempt)) {
 *     // ... verwerking; alle logregels dragen de context ...
 * }
 * }</pre>
 */
public final class NotificationDiagnosticContext implements AutoCloseable {

    public static final String NOTIFICATION_ID = "notification.id";
    public static final String TENANT_ID = "tenant.id";
    public static final String PROVIDER = "provider";
    public static final String MESSAGE_TYPE = "message.type";
    public static final String ATTEMPT = "attempt";

    private final Map<String, String> previousValues;

    private NotificationDiagnosticContext(Map<String, String> previousValues) {
        this.previousValues = previousValues;
    }

    /**
     * Vult de MDC met de niet-PII context van het gegeven bericht en de huidige poging.
     * Null-velden worden overgeslagen. Retourneert een handle die bij {@link #close()}
     * de oorspronkelijke MDC-waarden herstelt.
     *
     * @param message het te verwerken bericht (mag null zijn)
     * @param attempt 1-gebaseerde pogingsnummer (0 of negatief wordt niet gezet)
     */
    public static NotificationDiagnosticContext open(NotificationQueueMessage message, int attempt) {
        Map<String, String> previous = new java.util.HashMap<>();
        if (message != null) {
            put(previous, NOTIFICATION_ID, message.getNotificationId() != null
                    ? message.getNotificationId().toString() : null);
            put(previous, TENANT_ID, message.getTenantId());
            put(previous, PROVIDER, message.getProvider());
            put(previous, MESSAGE_TYPE, message.getMessageType());
        }
        if (attempt > 0) {
            put(previous, ATTEMPT, Integer.toString(attempt));
        }
        return new NotificationDiagnosticContext(previous);
    }

    private static void put(Map<String, String> previous, String key, String value) {
        if (value == null) {
            return;
        }
        // Bewaar de bestaande waarde (null als die er niet was) om netjes te kunnen herstellen.
        previous.put(key, MDC.get(key));
        MDC.put(key, value);
    }

    /** Herstelt de MDC naar de staat van vóór {@link #open}. */
    @Override
    public void close() {
        previousValues.forEach((key, previous) -> {
            if (previous == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, previous);
            }
        });
    }
}
