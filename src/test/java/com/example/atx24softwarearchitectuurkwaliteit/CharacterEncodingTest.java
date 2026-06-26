package com.example.atx24softwarearchitectuurkwaliteit;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.RabbitMQConsumer;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Karakterset-testen — NFR8: berichten met niet-Latijnse tekens (Arabisch, Chinees)
 * worden correct als UTF-8 doorgestuurd naar externe messaging providers.
 *
 * De test stuurt een NotificationQueueMessage met Unicode-tekst direct naar de
 * RabbitMQConsumer en verifieert via WireMock dat de provider de tekst ongewijzigd
 * in de HTTP-body heeft ontvangen.
 *
 * Gedekte providers:
 *   - SwiftSend  (JSON-body, Chinees)
 *   - LegacyLink (XML-body,  Arabisch)
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "providers.base-url=http://localhost:19877"
)
class CharacterEncodingTest {

    // ── WireMock: nep-provider HTTP server op vaste poort ────────────────────

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(options().port(19877));
        wireMock.start();
        configureFor("localhost", 19877);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    // ── Spring-beans ─────────────────────────────────────────────────────────

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Autowired private RabbitMQConsumer consumer;
    @Autowired private Jackson2JsonMessageConverter messageConverter;

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    // ── Hulpfunctie: DTO → AMQP Message ─────────────────────────────────────

    private Message toAmqpMessage(NotificationQueueMessage dto) {
        MessageProperties props = new MessageProperties();
        props.setContentType("application/json");
        return messageConverter.toMessage(dto, props);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NFR8 — Karakterset test 1: Chinees via SwiftSend (JSON)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Chinese tekens moeten ongewijzigd als UTF-8 in de JSON-body van de
     * SwiftSend-aanroep terechtkomen.
     *
     * Achtergrond: SwiftSend serialiseert het bericht via Jackson naar JSON.
     * JSON-strings zijn per definitie UTF-8, maar de test bewijst dat er geen
     * onnodige escaping (bijv. \uXXXX) of tekenverlies optreedt.
     */
    @Test
    void swiftsend_verzendt_chinese_tekens_correct_als_utf8() {
        String chineseTekst = "您的预约明天上午10点"; // "Uw afspraak is morgen om 10:00"

        wireMock.stubFor(post(urlPathEqualTo("/swiftsend"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"messageId\":\"SS-UTF8-CN-001\"}")));

        NotificationQueueMessage bericht = new NotificationQueueMessage(
                UUID.randomUUID(),
                "test-tenant-cn",
                "+8613900000001",
                "预约提醒",
                chineseTekst,
                ProviderType.SWIFTSEND,
                "SMS",
                Instant.now());

        consumer.consume(toAmqpMessage(bericht));

        // Verifieer dat de HTTP-body richting SwiftSend de Chinese tekens bevat
        wireMock.verify(postRequestedFor(urlPathEqualTo("/swiftsend"))
                .withRequestBody(containing(chineseTekst)));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NFR8 — Karakterset test 2: Arabisch via LegacyLink (XML)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Arabische tekens moeten ongewijzigd in de XML-body van de LegacyLink-aanroep
     * terechtkomen.
     *
     * Achtergrond: LegacyLinkXmlMapper bouwt handmatig een XML-string met
     * {@code <?xml version="1.0" encoding="utf-8"?>}. De test bewijst dat de
     * XmlMapper de Arabische tekens niet escapet of verliest.
     */
    @Test
    void legacylink_verzendt_arabische_tekens_correct_als_utf8_xml() {
        String arabischeTekst = "موعدك غداً في الساعة العاشرة"; // "Uw afspraak is morgen om 10:00"

        wireMock.stubFor(post(urlPathEqualTo("/LegacyLink/SendSms"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml;charset=UTF-8")
                        .withBody("<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                                  "<SendSmsResponse xmlns=\"http://legacylink.fakecomworld.com/v1\">" +
                                  "<StatusCode>200</StatusCode>" +
                                  "<StatusMessage>OK</StatusMessage>" +
                                  "<MessageReference>LL-AR-UTF8-001</MessageReference>" +
                                  "<Timestamp>2024-01-01T10:00:00Z</Timestamp>" +
                                  "</SendSmsResponse>")));

        NotificationQueueMessage bericht = new NotificationQueueMessage(
                UUID.randomUUID(),
                "test-tenant-ar",
                "+96650000001",
                "تذكير بالموعد",
                arabischeTekst,
                ProviderType.LEGACYLINK,
                "SMS",
                Instant.now());

        consumer.consume(toAmqpMessage(bericht));

        // Verifieer dat de HTTP-body richting LegacyLink de Arabische tekens bevat
        wireMock.verify(postRequestedFor(urlPathEqualTo("/LegacyLink/SendSms"))
                .withRequestBody(containing(arabischeTekst)));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NFR8 — Karakterset test 3: Gemengde Unicode (emoji + speciale tekens)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Berichten met speciale tekens (accenten, bijzondere leestekens) mogen
     * niet worden afgekapt of gecorrumpeerd bij verzending via SwiftSend.
     */
    @Test
    void swiftsend_verzendt_speciale_europese_tekens_correct() {
        String europeeseTekst = "Ваша встреча завтра в 10:00"; // Russisch/Cyrillisch

        wireMock.stubFor(post(urlPathEqualTo("/swiftsend"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"messageId\":\"SS-UTF8-RU-001\"}")));

        NotificationQueueMessage bericht = new NotificationQueueMessage(
                UUID.randomUUID(),
                "test-tenant-ru",
                "+79000000001",
                "Напоминание",
                europeeseTekst,
                ProviderType.SWIFTSEND,
                "SMS",
                Instant.now());

        consumer.consume(toAmqpMessage(bericht));

        wireMock.verify(postRequestedFor(urlPathEqualTo("/swiftsend"))
                .withRequestBody(containing(europeeseTekst)));
    }
}
