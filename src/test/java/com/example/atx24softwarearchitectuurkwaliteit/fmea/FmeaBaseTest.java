package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.example.atx24softwarearchitectuurkwaliteit.dao.NotificationLogDAO;
import com.example.atx24softwarearchitectuurkwaliteit.dao.ProcessedEventDAO;
import com.example.atx24softwarearchitectuurkwaliteit.dao.TenantDAO;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.TenantEntity;
import com.example.atx24softwarearchitectuurkwaliteit.fhir.FhirAppointmentValidator;
import com.example.atx24softwarearchitectuurkwaliteit.fhir.OpenMrsRestAppointmentFetcher;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.RabbitMQConsumer;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.retry.ExponentialBackoffRetryPolicy;
import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProviderFactory;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import com.example.atx24softwarearchitectuurkwaliteit.service.DataService;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Gedeelde basisklasse voor alle FMEA-resilientietesten.
 *
 * Biedt:
 *   - Spring Boot testcontext met gemockte RabbitTemplate
 *   - WireMock op poort 19879 (nep messaging-provider)
 *   - WireMock op poort 19880 (nep OpenMRS REST API)
 *   - Hulpfuncties voor het bouwen van testberichten en tenantconfiguraties
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "providers.base-url=http://localhost:19879"
)
abstract class FmeaBaseTest {

    // ── WireMock servers ─────────────────────────────────────────────────────

    static WireMockServer providerMock;  // poort 19879 — nep messaging-provider
    static WireMockServer openMrsMock;   // poort 19880 — nep OpenMRS

    @BeforeAll
    static void startWireMock() {
        providerMock = new WireMockServer(options().port(19879));
        providerMock.start();
        openMrsMock = new WireMockServer(options().port(19880));
        openMrsMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (providerMock != null && providerMock.isRunning()) providerMock.stop();
        if (openMrsMock != null && openMrsMock.isRunning()) openMrsMock.stop();
    }

    // ── Spring-beans ─────────────────────────────────────────────────────────

    @MockBean
    RabbitTemplate rabbitTemplate;

    @Autowired TenantDAO tenantDAO;
    @Autowired RabbitMQConsumer consumer;
    @Autowired Jackson2JsonMessageConverter messageConverter;
    @Autowired NotificationLogDAO notificationLogDAO;
    @Autowired ProcessedEventDAO processedEventDAO;
    @Autowired DataService dataService;
    @Autowired MessagingProviderFactory providerFactory;
    @Autowired ExponentialBackoffRetryPolicy retryPolicy;
    @Autowired FhirAppointmentValidator fhirValidator;
    @Autowired OpenMrsRestAppointmentFetcher appointmentFetcher;
    @Autowired TestRestTemplate restTemplate;

    // ── Toestandsreset ───────────────────────────────────────────────────────

    @BeforeEach
    void basisReset() {
        providerMock.resetAll();
        openMrsMock.resetAll();
        notificationLogDAO.deleteSentAtBefore(LocalDateTime.now().plusDays(1));
    }

    // ── Hulpfuncties ─────────────────────────────────────────────────────────

    Message toAmqpMessage(NotificationQueueMessage dto) {
        MessageProperties props = new MessageProperties();
        props.setContentType("application/json");
        return messageConverter.toMessage(dto, props);
    }

    Message toAmqpMessageMetRetryCount(NotificationQueueMessage dto, int retryCount) {
        MessageProperties props = new MessageProperties();
        props.setContentType("application/json");
        props.setHeader("x-notification-retry-count", retryCount);
        props.setHeader("x-notification-first-failed-at", "2024-01-01T00:00:00Z");
        return messageConverter.toMessage(dto, props);
    }

    TenantConfiguration openMrsTenant(String tenantId) {
        TenantConfiguration tenant = new TenantConfiguration(tenantId, "FMEA Test Ziekenhuis");
        tenant.setOpenMrsBaseUrl("http://localhost:19880");
        tenant.setOpenMrsUsername("admin");
        tenant.setOpenMrsPassword("admin");
        return tenant;
    }

    NotificationQueueMessage swiftSendBericht(String tenantId) {
        return new NotificationQueueMessage(
                UUID.randomUUID(), tenantId, "+31600000001",
                "FMEA Test", "Testbericht", ProviderType.SWIFTSEND, "SMS", Instant.now());
    }

    TenantEntity opslaanActieveTenant(String tenantId) {
        TenantEntity tenant = new TenantEntity();
        tenant.setTenantId(tenantId);
        tenant.setOrganizationName("Test Ziekenhuis " + tenantId);
        tenant.setOpenMrsBaseUrl("http://localhost:19880");
        tenant.setOpenMrsUsername("admin");
        tenant.setOpenMrsPassword("admin");
        tenant.setNotificationProvider("SWIFTSEND");
        tenant.setActive(true);
        return tenantDAO.save(tenant);
    }

    void stubProviderSucces() {
        providerMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .post(com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/swiftsend"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"messageId\":\"STUB-OK\"}")));
    }

    void stubProviderFout(int statusCode) {
        providerMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .post(com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/swiftsend"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(statusCode)));
    }
}
