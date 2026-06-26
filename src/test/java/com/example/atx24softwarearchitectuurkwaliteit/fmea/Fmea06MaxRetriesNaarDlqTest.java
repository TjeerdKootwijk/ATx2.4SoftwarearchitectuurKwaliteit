package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.example.atx24softwarearchitectuurkwaliteit.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * FMEA Scenario 6 - RPN 12 - HOOG - NFR7, NFR9
 *
 * Scenario: Dead-letter queue groeit onbeperkt (geen DLQ-consumer).
 * Effect:   Stille mislukking; fouten worden nooit opgemerkt of verwerkt.
 * Maatregel: Na het bereiken van het maximale aantal retries (5) stuurt
 *            RabbitMqRetryHandler het bericht naar de DLQ-exchange.
 *            Berichten worden nooit stilletjes verwijderd.
 */
class Fmea06MaxRetriesNaarDlqTest extends FmeaBaseTest {

    @Test
    void na_max_retries_wordt_bericht_naar_dead_letter_exchange_gestuurd() {
        stubProviderFout(503);

        // retryCount op maximaal zetten: policy weigert verdere retry, stuurt naar DLQ
        consumer.consume(toAmqpMessageMetRetryCount(
                swiftSendBericht("fmea06-tenant"), retryPolicy.getMaxRetries()));

        ArgumentCaptor<String> exchangeCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, atLeastOnce())
                .convertAndSend(exchangeCaptor.capture(), any(String.class), any(Object.class));

        assertThat(exchangeCaptor.getAllValues())
                .as("Na max retries moet het bericht naar de dead-letter exchange worden gestuurd")
                .contains(RabbitMQConfig.DEAD_LETTER_EXCHANGE);
    }

    @Test
    void voor_max_retries_wordt_bericht_naar_retry_exchange_gestuurd_niet_dlq() {
        stubProviderFout(503);

        // retryCount op 0: er zijn nog retry-pogingen beschikbaar
        consumer.consume(toAmqpMessageMetRetryCount(swiftSendBericht("fmea06-tenant-retry"), 0));

        ArgumentCaptor<String> exchangeCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, atLeastOnce())
                .convertAndSend(exchangeCaptor.capture(), any(String.class), any(Object.class),
                        any(org.springframework.amqp.core.MessagePostProcessor.class));

        assertThat(exchangeCaptor.getAllValues())
                .as("Voor max retries mag het bericht NIET naar de DLQ; het moet worden herbehandeld")
                .doesNotContain(RabbitMQConfig.DEAD_LETTER_EXCHANGE);
    }
}
