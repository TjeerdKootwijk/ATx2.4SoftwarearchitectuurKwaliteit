package com.example.atx24softwarearchitectuurkwaliteit.messaging.queue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.retry.RetryHandler;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.retry.RetryPolicy;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProvider;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProviderFactory;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import com.example.atx24softwarearchitectuurkwaliteit.service.DataService;
import com.example.atx24softwarearchitectuurkwaliteit.service.logging.NotificationDiagnosticContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.nio.file.ProviderNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifieert dat fouten in de notificatieverwerking herleidbaar worden gelogd:
 * met een volledige stacktrace (de precieze regel) én de niet-PII input-context (MDC),
 * en dat die context na verwerking niet lekt naar het volgende bericht.
 */
class RabbitMQConsumerTest {

    /**
     * Legt per logregel een momentopname van de MDC vast op het moment van loggen.
     * Logback leest de MDC normaal lazy uit; tegen de tijd dat de test hem opvraagt is de
     * try-with-resources de context al opgeruimd. Door in append() (synchroon tijdens de
     * log-call) te snapshotten leggen we de context vast zoals die werkelijk gold.
     */
    static class CapturingAppender extends AppenderBase<ILoggingEvent> {
        final List<ILoggingEvent> events = new ArrayList<>();
        final List<Map<String, String>> mdcSnapshots = new ArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            events.add(event);
            Map<String, String> map = MDC.getCopyOfContextMap();
            mdcSnapshots.add(map == null ? Map.of() : map);
        }

        int firstErrorIndex() {
            for (int i = 0; i < events.size(); i++) {
                if (events.get(i).getLevel() == Level.ERROR) {
                    return i;
                }
            }
            throw new AssertionError("Verwacht een ERROR-logregel, maar geen gevonden");
        }

        ILoggingEvent firstError() {
            return events.get(firstErrorIndex());
        }

        Map<String, String> firstErrorMdc() {
            return mdcSnapshots.get(firstErrorIndex());
        }

        long errorCount() {
            return events.stream().filter(e -> e.getLevel() == Level.ERROR).count();
        }
    }

    private MessagingProviderFactory providerFactory;
    private RetryHandler retryHandler;
    private RetryPolicy retryPolicy;
    private Jackson2JsonMessageConverter messageConverter;
    private DataService dataService;
    private RabbitMQConsumer consumer;

    private Logger consumerLogger;
    private CapturingAppender logAppender;

    @BeforeEach
    void setUp() {
        providerFactory = mock(MessagingProviderFactory.class);
        retryHandler = mock(RetryHandler.class);
        retryPolicy = mock(RetryPolicy.class);
        messageConverter = mock(Jackson2JsonMessageConverter.class);
        dataService = mock(DataService.class);
        when(retryPolicy.getMaxRetries()).thenReturn(3);

        consumer = new RabbitMQConsumer(
                providerFactory, new SimpleMeterRegistry(), retryHandler,
                retryPolicy, messageConverter, dataService);

        consumerLogger = (Logger) LoggerFactory.getLogger(RabbitMQConsumer.class);
        logAppender = new CapturingAppender();
        logAppender.start();
        consumerLogger.addAppender(logAppender);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        consumerLogger.detachAppender(logAppender);
        MDC.clear();
    }

    private NotificationQueueMessage message(String provider) {
        return new NotificationQueueMessage(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "ziekenhuis-a", "+31600000000", "Herinnering", "Body",
                provider, "SMS", Instant.now());
    }

    private Message rawMessage() {
        return new Message("{}".getBytes(), new MessageProperties());
    }

    @Test
    void unexpectedException_isLoggedWithStacktraceAndInputContext() {
        NotificationQueueMessage msg = message("UNKNOWN");
        when(messageConverter.fromMessage(any())).thenReturn(msg);
        when(providerFactory.get("UNKNOWN"))
                .thenThrow(new ProviderNotFoundException("No provider registered for name 'UNKNOWN'"));

        consumer.consume(rawMessage());

        ILoggingEvent event = logAppender.firstError();
        // Stacktrace aanwezig -> herleidbaar tot de precieze regel waar het misging.
        assertNotNull(event.getThrowableProxy(), "ERROR moet de exception (stacktrace) bevatten");
        assertEquals(ProviderNotFoundException.class.getName(), event.getThrowableProxy().getClassName());

        // Input-context (niet-PII) vastgelegd op het moment van loggen.
        Map<String, String> mdc = logAppender.firstErrorMdc();
        assertEquals("33333333-3333-3333-3333-333333333333", mdc.get(NotificationDiagnosticContext.NOTIFICATION_ID));
        assertEquals("ziekenhuis-a", mdc.get(NotificationDiagnosticContext.TENANT_ID));
        assertEquals("UNKNOWN", mdc.get(NotificationDiagnosticContext.PROVIDER));
        assertEquals("SMS", mdc.get(NotificationDiagnosticContext.MESSAGE_TYPE));
        assertEquals("1", mdc.get(NotificationDiagnosticContext.ATTEMPT));

        // Geen persoonsgegevens in de logcontext (NFR11).
        assertTrue(mdc.values().stream().noneMatch(v -> v.contains("+31600000000")));

        // Bericht gaat via dezelfde retry/dead-letter-afhandeling.
        verify(retryHandler, times(1)).onFailure(any(), any());
    }

    @Test
    void providerFailureResult_isLoggedWithInputContext() {
        NotificationQueueMessage msg = message("SWIFTSEND");
        MessagingProvider provider = mock(MessagingProvider.class);
        when(messageConverter.fromMessage(any())).thenReturn(msg);
        when(providerFactory.get("SWIFTSEND")).thenReturn(provider);
        when(provider.sendMessage(any())).thenReturn(
                ProviderSendResult.error(msg.getNotificationId().toString(), "provider weigerde bericht"));

        consumer.consume(rawMessage());

        Map<String, String> mdc = logAppender.firstErrorMdc();
        assertEquals("ziekenhuis-a", mdc.get(NotificationDiagnosticContext.TENANT_ID));
        assertEquals("SWIFTSEND", mdc.get(NotificationDiagnosticContext.PROVIDER));

        verify(dataService, times(1)).logNotificationFailed(any(), any(), any(), any(), anyInt());
        verify(retryHandler, times(1)).onFailure(any(), any());
    }

    @Test
    void diagnosticContext_isClearedAfterProcessing() {
        NotificationQueueMessage msg = message("SWIFTSEND");
        MessagingProvider provider = mock(MessagingProvider.class);
        when(messageConverter.fromMessage(any())).thenReturn(msg);
        when(providerFactory.get("SWIFTSEND")).thenReturn(provider);
        when(provider.sendMessage(any())).thenReturn(ProviderSendResult.send("ok-1"));

        consumer.consume(rawMessage());

        // Geen lekkage naar het volgende bericht op deze thread.
        assertNull(MDC.get(NotificationDiagnosticContext.NOTIFICATION_ID));
        assertNull(MDC.get(NotificationDiagnosticContext.TENANT_ID));
    }

    @Test
    void deserializationFailure_isLoggedAndRethrown() {
        when(messageConverter.fromMessage(any()))
                .thenThrow(new IllegalStateException("kapotte payload"));

        try {
            consumer.consume(rawMessage());
            throw new AssertionError("Verwacht dat de deserialisatiefout wordt doorgegooid");
        } catch (IllegalStateException expected) {
            // verwacht
        }

        assertEquals(1, logAppender.errorCount());
        assertNotNull(logAppender.firstError().getThrowableProxy());
        verify(retryHandler, never()).onFailure(any(), any());
    }
}
