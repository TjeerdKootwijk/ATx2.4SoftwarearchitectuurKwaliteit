package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * FMEA Scenario 3 - RPN 12 - HOOG - NFR7, NFR9
 *
 * Scenario: Provider rate-limiting (HTTP 429 Too Many Requests).
 * Effect:   Berichten geweigerd; notificaties vertraagd of verloren.
 * Maatregel: Exponential backoff respecteert Retry-After header.
 *            429-responses worden als FAILED gelogd en via backoff opnieuw geprobeerd.
 *            Bericht gaat naar DLQ na max retries, nooit verloren.
 */
class Fmea03ProviderRateLimitingTest extends FmeaBaseTest {

    @Test
    void provider_429_wordt_geregistreerd_als_failed_en_retry_ingepland() {
        providerMock.stubFor(post(urlPathEqualTo("/swiftsend"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "30")
                        .withBody("Too Many Requests")));

        String tenantId = "fmea03-tenant";
        consumer.consume(toAmqpMessage(swiftSendBericht(tenantId)));

        List<NotificationLogEntity> logs = notificationLogDAO.findByTenantId(tenantId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus())
                .as("HTTP 429 moet worden geregistreerd als FAILED voor Grafana-alerting")
                .isEqualTo("FAILED");

        verify(rabbitTemplate, org.mockito.Mockito.atLeastOnce())
                .convertAndSend(any(String.class), any(String.class), any(Object.class),
                        any(org.springframework.amqp.core.MessagePostProcessor.class));
    }
}
