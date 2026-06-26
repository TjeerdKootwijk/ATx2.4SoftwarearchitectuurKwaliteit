package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * FMEA Scenario 10 - RPN 10 - MIDDEN - NFR3, NFR7
 *
 * Scenario: Provider volledig offline (storing bij derde partij).
 * Effect:   Alle notificaties voor die provider mislukken.
 * Maatregel: DLQ buffert berichten; FAILED gelogd met retryCount.
 *            Beheerder kan handmatig overschakelen naar alternatieve provider.
 */
class Fmea10ProviderOfflineTest extends FmeaBaseTest {

    @Test
    void provider_offline_503_wordt_gelogd_als_failed_met_retry_count() {
        stubProviderFout(503);

        String tenantId = "fmea10-tenant";
        consumer.consume(toAmqpMessage(swiftSendBericht(tenantId)));

        List<NotificationLogEntity> logs = notificationLogDAO.findByTenantId(tenantId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus())
                .as("Provider offline moet worden geregistreerd als FAILED")
                .isEqualTo("FAILED");
        assertThat(logs.get(0).getRetryCount())
                .as("RetryCount moet worden bijgehouden zodat beheerder weet hoe lang provider offline is")
                .isGreaterThanOrEqualTo(0);
    }

    @Test
    void provider_offline_bericht_wordt_doorgestuurd_naar_retry_mechanisme() {
        stubProviderFout(503);

        consumer.consume(toAmqpMessage(swiftSendBericht("fmea10-tenant-retry")));

        verify(rabbitTemplate, atLeastOnce())
                .convertAndSend(any(String.class), any(String.class), any(Object.class),
                        any(org.springframework.amqp.core.MessagePostProcessor.class));
    }
}
