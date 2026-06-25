package com.example.atx24softwarearchitectuurkwaliteit;

import com.example.atx24softwarearchitectuurkwaliteit.config.RabbitMQConfig;
import com.example.atx24softwarearchitectuurkwaliteit.dao.NotificationLogDAO;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.RabbitMQConsumer;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Systeemtest — valideert de volledige notificatieketen end-to-end.
 *
 * Keten die getest wordt:
 *   POST /api/notifications/test
 *     → RabbitMQProducer.publish()        [RabbitTemplate gemockt: geen echte MQ nodig]
 *     → RabbitMQConsumer.consume()         [direct aangeroepen, simuleert MQ-aflevering]
 *     → SwiftSendProvider.sendMessage()
 *     → SwiftSendClient (WebClient)        [WireMock op poort 19876]
 *     → DataService.logNotificationSent/Failed()
 *     → NotificationLogDAO                 [H2 in-memory]
 *
 * Dekt: FR2 (auditlog), NFR7 (foutafhandeling/retry), RabbitMQ-serialisatie.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "providers.base-url=http://localhost:19876"
)
class SystemTest {

    // ── WireMock: nep-provider HTTP server op vaste poort ────────────────────

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(options().port(19876));
        wireMock.start();
        configureFor("localhost", 19876);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    // ── Spring-beans ─────────────────────────────────────────────────────────

    // Vervang de echte RabbitTemplate zodat berichten niet naar een externe
    // RabbitMQ-instantie worden verstuurd. Mockito legt de aanroepen vast.
    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Autowired private RabbitMQConsumer consumer;
    @Autowired private Jackson2JsonMessageConverter messageConverter;
    @Autowired private NotificationLogDAO notificationLogDAO;
    @Autowired private TestRestTemplate restTemplate;

    // ── Toestandsreset ───────────────────────────────────────────────────────

    @BeforeEach
    void resetTestState() {
        wireMock.resetAll();
        // Verwijder alle auditlogregels uit de vorige test
        notificationLogDAO.deleteSentAtBefore(LocalDateTime.now().plusDays(1));
    }

    // ── Hulpfunctie ──────────────────────────────────────────────────────────

    /**
     * Converteert de DTO naar een Spring AMQP Message zoals RabbitMQ dat zou doen
     * bij aflevering aan de consumer.
     */
    private Message toAmqpMessage(NotificationQueueMessage dto) {
        MessageProperties props = new MessageProperties();
        props.setContentType("application/json");
        return messageConverter.toMessage(dto, props);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Systeemtest 1 — volledig succes-pad
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Valideert de volledige keten van HTTP-aanroep tot auditlogentry in de database.
     *
     * Verwacht resultaat: NotificationLogDAO bevat één entry met status "SUCCESS"
     * voor tenant "test-tenant" na een succesvolle provider-aanroep.
     */
    @Test
    void volledigeKeten_httpAanroepNaarAuditlog_slaatSuccessOp() {
        // Provider antwoordt met HTTP 200 en success-vlag
        wireMock.stubFor(post(urlPathEqualTo("/swiftsend"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"messageId\":\"SS-SYS-001\"}")));

        // Stap 1 — Roep het publieke HTTP-endpoint aan
        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/notifications/test?providerType=SWIFTSEND", null, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Stap 2 — Verifieer dat het bericht naar de wachtrij is gepubliceerd
        ArgumentCaptor<NotificationQueueMessage> captor =
                ArgumentCaptor.forClass(NotificationQueueMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.NOTIFICATION_EXCHANGE),
                eq(RabbitMQConfig.NOTIFICATION_ROUTING_KEY),
                captor.capture());

        NotificationQueueMessage queueBericht = captor.getValue();
        assertThat(queueBericht.getTenantId()).isEqualTo("test-tenant");
        assertThat(queueBericht.getProvider()).isEqualTo(ProviderType.SWIFTSEND);

        // Stap 3 — Simuleer RabbitMQ-aflevering door de consumer direct aan te roepen
        consumer.consume(toAmqpMessage(queueBericht));

        // Stap 4 — Controleer auditlog in de database (FR2)
        List<NotificationLogEntity> logs = notificationLogDAO.findByTenantId("test-tenant");
        assertThat(logs).hasSize(1);
        NotificationLogEntity log = logs.get(0);
        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getProvider()).isEqualTo(ProviderType.SWIFTSEND);
        assertThat(log.getTenantId()).isEqualTo("test-tenant");
        assertThat(log.getProviderMessageId()).isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Systeemtest 2 — provider geeft HTTP-fout
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Bij een provider-fout (HTTP 503) wordt het bericht als FAILED geregistreerd (NFR7).
     * De consumer mag geen uitzondering gooien; de fout is auditeerbaar.
     */
    @Test
    void volledigeKeten_providerGeeft503_wordtGelogdAlsFailed() {
        wireMock.stubFor(post(urlPathEqualTo("/swiftsend"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        NotificationQueueMessage bericht = new NotificationQueueMessage(
                UUID.randomUUID(),
                "test-tenant-fout",
                "+31699887766",
                "Afspraakherinnering",
                "Uw afspraak is morgen om 10:00.",
                ProviderType.SWIFTSEND,
                "SMS",
                Instant.now());

        // Consumer mag geen uitzondering gooien bij een provider-fout
        consumer.consume(toAmqpMessage(bericht));

        List<NotificationLogEntity> logs = notificationLogDAO.findByTenantId("test-tenant-fout");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(logs.get(0).getProvider()).isEqualTo(ProviderType.SWIFTSEND);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Systeemtest 3 — HTTP endpoint accepteert elke bekende provider
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Het testendpoint geeft HTTP 202 Accepted terug voor elke bekende providernaam.
     * De RabbitMQ-publicatie is via @MockBean onderschept; er is geen MQ nodig.
     */
    @Test
    void httpEndpoint_meerdereProviderTypes_gevenAllemalAccepted() {
        for (String provider : List.of("SWIFTSEND", "LEGACYLINK", "ASYNCFLOW", "SECUREPOST")) {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    "/api/notifications/test?providerType=" + provider, null, String.class);
            assertThat(resp.getStatusCode())
                    .as("Provider %s moet HTTP 202 teruggeven", provider)
                    .isEqualTo(HttpStatus.ACCEPTED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Systeemtest 4 — API-headers in SwiftSend-aanroep
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Valideert dat de SwiftSend HTTP-aanroep de verplichte authenticatieheaders bevat
     * (X-API-KEY en X-STUDENT-GROUP), zoals vereist door de provider-documentatie.
     */
    @Test
    void swiftSendAanroep_bevatVerplichtAuthentatieHeaders() {
        wireMock.stubFor(post(urlPathEqualTo("/swiftsend"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"messageId\":\"SS-SYS-002\"}")));

        NotificationQueueMessage bericht = new NotificationQueueMessage(
                UUID.randomUUID(),
                "test-tenant-headers",
                "+31612345678",
                "Header verificatie",
                "Controleer of de juiste headers worden meegestuurd.",
                ProviderType.SWIFTSEND,
                "SMS",
                Instant.now());

        consumer.consume(toAmqpMessage(bericht));

        // Controleer dat de provider-aanroep de vereiste headers bevatte
        wireMock.verify(postRequestedFor(urlPathEqualTo("/swiftsend"))
                .withHeader("X-API-KEY", matching(".+"))
                .withHeader("X-STUDENT-GROUP", matching(".+")));

        // En dat het succes correct in de database staat
        List<NotificationLogEntity> logs = notificationLogDAO.findByTenantId("test-tenant-headers");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo("SUCCESS");
    }
}
