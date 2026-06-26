package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * FMEA Scenario 9 - RPN 10 - MIDDEN - NFR7, NFR9
 *
 * Scenario: Provider API breaking change (veldnamen gewijzigd).
 * Effect:   Berichten niet meer verstuurd; HTTP 4xx zonder duidelijke foutmelding.
 * Maatregel: 4xx-responses worden als FAILED gelogd in de audit-log.
 *            Grafana-alert bij >1% 4xx van provider.
 *            WireMock-integratietests detecteren contractbreuk vroegtijdig.
 */
class Fmea09ProviderApiBreakingChangeTest extends FmeaBaseTest {

    @Test
    void provider_400_bad_request_geregistreerd_als_failed_in_audit_log() {
        providerMock.stubFor(post(urlPathEqualTo("/swiftsend"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("Bad Request - unknown field 'content', expected 'message'")));

        String tenantId = "fmea09-tenant-400";
        consumer.consume(toAmqpMessage(swiftSendBericht(tenantId)));

        List<NotificationLogEntity> logs = notificationLogDAO.findByTenantId(tenantId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus())
                .as("HTTP 400 (API breaking change) moet als FAILED worden opgeslagen voor Grafana-alerting")
                .isEqualTo("FAILED");
    }

    @Test
    void provider_404_not_found_geregistreerd_als_failed_bij_verwijderd_endpoint() {
        providerMock.stubFor(post(urlPathEqualTo("/swiftsend"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("Not Found - endpoint /swiftsend no longer exists")));

        String tenantId = "fmea09-tenant-404";
        consumer.consume(toAmqpMessage(swiftSendBericht(tenantId)));

        List<NotificationLogEntity> logs = notificationLogDAO.findByTenantId(tenantId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus())
                .as("HTTP 404 (endpoint verwijderd) moet als FAILED worden opgeslagen")
                .isEqualTo("FAILED");
    }
}
