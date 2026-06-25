package com.example.atx24softwarearchitectuurkwaliteit.dashboard;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.RabbitMQConsumer;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.retry.RetryHandler;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.retry.RetryPolicy;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProvider;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProviderFactory;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import com.example.atx24softwarearchitectuurkwaliteit.service.DataService;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.nio.file.ProviderNotFoundException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifieert dat de backend de Prometheus-metric {@code notifications_sent_total} met de
 * <em>juiste waarde</em> bijhoudt. Dit is de metric die de RabbitMQ-dashboardpanelen voedt:
 * "Geslaagde Transacties", "Mislukte Transacties", "Totaal Geprobeerd", "Verstuurd vs Mislukt"
 * en "Notificatie Slagingspercentage" (per provider), plus de Grafana-alert
 * <em>Veel mislukte notificaties</em> ({@code increase(notifications_sent_total{status="failed"}[5m]) > 5}).
 *
 * <p>Zonder draaiende stack: een {@link SimpleMeterRegistry} vangt de counters op zodat we
 * exact kunnen aantonen welk getal het dashboard te zien zou krijgen.</p>
 *
 * <p>FMEA-koppeling: scenario 17/18 (monitoring &amp; operationele zichtbaarheid) — als deze
 * counter verkeerd telt, tonen de dashboardpanelen een onjuist beeld en vuurt de
 * mislukkings-alert op het verkeerde moment.</p>
 */
class NotificationMetricsDashboardTest {

    private static final String COUNTER = "notifications_sent_total";

    private MessagingProviderFactory providerFactory;
    private Jackson2JsonMessageConverter messageConverter;
    private DataService dataService;
    private SimpleMeterRegistry meterRegistry;
    private RabbitMQConsumer consumer;

    @BeforeEach
    void setUp() {
        providerFactory = mock(MessagingProviderFactory.class);
        RetryHandler retryHandler = mock(RetryHandler.class);
        RetryPolicy retryPolicy = mock(RetryPolicy.class);
        messageConverter = mock(Jackson2JsonMessageConverter.class);
        dataService = mock(DataService.class);
        meterRegistry = new SimpleMeterRegistry();
        when(retryPolicy.getMaxRetries()).thenReturn(3);

        consumer = new RabbitMQConsumer(
                providerFactory, meterRegistry, retryHandler,
                retryPolicy, messageConverter, dataService);
    }

    private NotificationQueueMessage message(String provider) {
        return new NotificationQueueMessage(
                UUID.randomUUID(), "ziekenhuis-a", "+31600000000", "Herinnering", "Body",
                provider, "SMS", Instant.now());
    }

    private Message rawMessage() {
        return new Message("{}".getBytes(), new MessageProperties());
    }

    /** Laat de consumer één bericht verwerken waarvoor de provider {@code result} teruggeeft. */
    private void consumeOnce(String provider, ProviderSendResult result) {
        NotificationQueueMessage msg = message(provider);
        MessagingProvider messagingProvider = mock(MessagingProvider.class);
        when(messageConverter.fromMessage(any())).thenReturn(msg);
        when(providerFactory.get(provider)).thenReturn(messagingProvider);
        when(messagingProvider.sendMessage(any())).thenReturn(result);
        consumer.consume(rawMessage());
    }

    private double counter(String status, String provider) {
        return meterRegistry.get(COUNTER)
                .tags("status", status, "provider", provider)
                .counter().count();
    }

    @Test
    void geslaagdeBezorging_verhoogtSuccesCounterMetProviderTag() {
        consumeOnce("SWIFTSEND", ProviderSendResult.send("provider-msg-1"));

        // Voedt het paneel "Geslaagde Transacties" en de groene lijn in "Verstuurd vs Mislukt".
        assertEquals(1.0, counter("success", "SWIFTSEND"),
                "Een geslaagde bezorging hoort de success-counter met provider-tag op 1 te zetten");
    }

    @Test
    void providerWeigering_verhoogtMisluktCounter() {
        consumeOnce("LEGACYLINK", ProviderSendResult.error("provider-msg-2", "provider weigerde bericht"));

        // Voedt het paneel "Mislukte Transacties" en de drempel van de alert "Veel mislukte notificaties".
        assertEquals(1.0, counter("failed", "LEGACYLINK"),
                "Een provider-weigering hoort de failed-counter met provider-tag op 1 te zetten");
    }

    @Test
    void onverwachteCrash_teltNietAlsMisluktTransactie_alleenAlsFoutlog() {
        // Een crash in de pijplijn (bv. onbekende provider) gaat via retry/DLQ en wordt als
        // ERROR gelogd, maar verhoogt de notifications_sent_total-counter NIET. Het dashboard
        // toont zo'n crash dus in het foutlog-paneel, niet in "Mislukte Transacties".
        NotificationQueueMessage msg = message("UNKNOWN");
        when(messageConverter.fromMessage(any())).thenReturn(msg);
        when(providerFactory.get("UNKNOWN"))
                .thenThrow(new ProviderNotFoundException("No provider registered for name 'UNKNOWN'"));

        consumer.consume(rawMessage());

        assertThrows(MeterNotFoundException.class,
                () -> meterRegistry.get(COUNTER).counter(),
                "Een crash mag de transactie-counter niet ophogen; hij hoort enkel in de foutlogs");
    }
}
