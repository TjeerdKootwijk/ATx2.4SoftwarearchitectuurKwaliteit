package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.example.atx24softwarearchitectuurkwaliteit.dao.jpa.AsyncFlowTrackingJpaRepository;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import com.example.atx24softwarearchitectuurkwaliteit.provider.asyncflow.AsyncFlowStatusPoller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * FMEA Scenario 19 - Externe messaging providers - NFR3, NFR7, NFR9
 *
 * Scenario:  AsyncFlow is een ASYNCHRONE provider. Bij versturen geeft hij alleen
 *            'accepted' + trackingId terug — het bericht is aangenomen, NIET afgeleverd.
 *            De echte afleverstatus (Completed/Failed) komt pas later beschikbaar.
 * Effect:    Zonder statuscontrole zou een later mislukte aflevering stilzwijgend als
 *            "verstuurd" worden geboekt; de patiënt krijgt geen notificatie en niemand
 *            merkt het (stille mislukking).
 * Maatregel: Een geaccepteerd AsyncFlow-bericht wordt als PENDING vastgelegd (nog niet
 *            als succes geteld). De AsyncFlowStatusPoller pollt GET /asyncflow/{trackingId}
 *            en boekt de definitieve uitkomst: Completed -> SUCCESS, Failed -> FAILED.
 *            Zo wordt elke async-aflevering uiteindelijk correct geregistreerd en is een
 *            mislukking zichtbaar in notification_logs en de Prometheus-metric (Grafana).
 *
 * Koppeling FMEA-rij #8 (provider levert niet af) verfijnd voor het async-geval.
 */
class Fmea19AsyncFlowAfleverstatusTest extends FmeaBaseTest {

    private static final String TRACKING_ID = "ASF-TEST-1";

    @Autowired
    AsyncFlowStatusPoller asyncFlowStatusPoller;

    @Autowired
    AsyncFlowTrackingJpaRepository trackingRepository;

    @BeforeEach
    void resetTracking() {
        trackingRepository.deleteAll();
    }

    /** AsyncFlow accepteert het bericht: 202 met trackingId. */
    private void stubAsyncFlowAccepteert() {
        providerMock.stubFor(post(urlPathEqualTo("/asyncflow"))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accepted\":true,\"trackingId\":\"" + TRACKING_ID +
                                "\",\"message\":\"queued\",\"submittedAt\":\"2026-06-26T10:00:00Z\"}")));
    }

    /** De latere afleverstatus die GET /asyncflow/{trackingId} teruggeeft. */
    private void stubAsyncFlowStatus(String status, String errorDetails) {
        String error = errorDetails == null ? "null" : "\"" + errorDetails + "\"";
        providerMock.stubFor(get(urlPathEqualTo("/asyncflow/" + TRACKING_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"trackingId\":\"" + TRACKING_ID + "\",\"status\":\"" + status +
                                "\",\"processedAt\":\"2026-06-26T10:05:00Z\",\"errorDetails\":" + error + "}")));
    }

    private NotificationQueueMessage asyncFlowBericht(String tenantId) {
        return new NotificationQueueMessage(
                UUID.randomUUID(), tenantId, "+31600000099",
                "FMEA Test", "Async testbericht", ProviderType.ASYNCFLOW, "SMS", Instant.now());
    }

    @Test
    void geaccepteerd_bericht_wordt_nog_niet_als_succes_geboekt() {
        stubAsyncFlowAccepteert();
        String tenantId = "fmea19-pending";

        consumer.consume(toAmqpMessage(asyncFlowBericht(tenantId)));

        // 'accepted' != afgeleverd: er mag nog GEEN notification_log staan...
        assertThat(notificationLogDAO.findByTenantId(tenantId))
                .as("Een asynchroon geaccepteerd bericht mag niet meteen als succes worden geboekt")
                .isEmpty();
        // ...maar het moet wel als PENDING vastliggen voor de statuspoller.
        assertThat(trackingRepository.findAll())
                .as("Geaccepteerd AsyncFlow-bericht moet als PENDING zijn vastgelegd")
                .hasSize(1);
    }

    @Test
    void async_aflevering_die_later_mislukt_wordt_alsnog_als_FAILED_gedetecteerd() {
        stubAsyncFlowAccepteert();
        stubAsyncFlowStatus("Failed", "recipient unreachable");
        String tenantId = "fmea19-failed";

        // Stap 1: bericht wordt geaccepteerd en als PENDING vastgelegd.
        consumer.consume(toAmqpMessage(asyncFlowBericht(tenantId)));

        // Stap 2: de poller haalt de werkelijke status op -> Failed.
        asyncFlowStatusPoller.pollPendingStatuses();

        List<NotificationLogEntity> logs = notificationLogDAO.findByTenantId(tenantId);
        assertThat(logs)
                .as("Async-mislukking moet alsnog worden gedetecteerd, niet stilzwijgend verloren gaan")
                .hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(logs.get(0).getProvider()).isEqualTo(ProviderType.ASYNCFLOW);

        // De tracking-rij is afgehandeld en opgeruimd.
        assertThat(trackingRepository.findAll())
                .as("Na een definitieve uitkomst hoort de PENDING-rij te verdwijnen")
                .isEmpty();
    }

    @Test
    void async_aflevering_die_later_slaagt_wordt_als_SUCCESS_geboekt() {
        stubAsyncFlowAccepteert();
        stubAsyncFlowStatus("Completed", null);
        String tenantId = "fmea19-completed";

        consumer.consume(toAmqpMessage(asyncFlowBericht(tenantId)));
        asyncFlowStatusPoller.pollPendingStatuses();

        List<NotificationLogEntity> logs = notificationLogDAO.findByTenantId(tenantId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(logs.get(0).getProviderMessageId())
                .as("De trackingId van AsyncFlow moet bewaard blijven voor facturatie-reconciliatie")
                .isEqualTo(TRACKING_ID);
        assertThat(trackingRepository.findAll()).isEmpty();
    }
}
