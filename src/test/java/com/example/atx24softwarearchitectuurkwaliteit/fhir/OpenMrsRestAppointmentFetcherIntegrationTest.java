package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integratietest voor OpenMrsRestAppointmentFetcher.
 *
 * Gebruikt WireMock als nep-OpenMRS HTTP server zodat de volledige
 * HTTP-stack (RestTemplate, auth-headers, JSON-parsing) lokaal aangetoond
 * kan worden zonder een echte OpenMRS instantie.
 *
 * Dekt NFR 6 — berichtontvangst: de fetcher haalt correct data op en
 * handelt fouten netjes af (lege lijst bij HTTP-fouten).
 */
class OpenMrsRestAppointmentFetcherIntegrationTest {

    private WireMockServer wireMock;
    private OpenMrsRestAppointmentFetcher fetcher;
    private TenantConfiguration tenant;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        fetcher = new OpenMrsRestAppointmentFetcher(new RestTemplate(), new ObjectMapper());

        tenant = new TenantConfiguration("test-ziekenhuis", "Test Ziekenhuis");
        tenant.setOpenMrsBaseUrl("http://localhost:" + wireMock.port());
        tenant.setOpenMrsUsername("admin");
        tenant.setOpenMrsPassword("Admin123");
        tenant.setActive(true);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // ── Succesgevallen ────────────────────────────────────────────────────────

    @Test
    void fetchAppointments_200MetResultsArray_geeftAfsprakenTerug() {
        long startMillis = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli();
        String body = """
                {
                  "results": [
                    {
                      "uuid": "fake-001",
                      "status": "Scheduled",
                      "startDateTime": %d,
                      "endDateTime":   %d,
                      "patient": { "uuid": "p-001", "display": "Jan de Vries" },
                      "location": { "display": "Kamer 3B" }
                    }
                  ]
                }
                """.formatted(startMillis, startMillis + 1800000);

        wireMock.stubFor(get(urlPathEqualTo("/ws/rest/v1/appointments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));

        List<JsonNode> resultaat = fetcher.fetchAppointments(tenant);

        assertThat(resultaat).hasSize(1);
        assertThat(resultaat.get(0).path("uuid").asText()).isEqualTo("fake-001");
    }

    @Test
    void fetchAppointments_200MetDirecArray_geeftAfsprakenTerug() {
        long startMillis = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli();
        String body = """
                [
                  { "uuid": "fake-002", "status": "Scheduled", "startDateTime": %d }
                ]
                """.formatted(startMillis);

        wireMock.stubFor(get(urlPathEqualTo("/ws/rest/v1/appointments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));

        List<JsonNode> resultaat = fetcher.fetchAppointments(tenant);

        assertThat(resultaat).hasSize(1);
        assertThat(resultaat.get(0).path("uuid").asText()).isEqualTo("fake-002");
    }

    @Test
    void fetchAppointments_200MetMeerdereAfspraken_geeftAlleTerug() {
        long startMillis = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli();
        String body = """
                {
                  "results": [
                    { "uuid": "fake-003", "status": "Scheduled", "startDateTime": %d },
                    { "uuid": "fake-004", "status": "Scheduled", "startDateTime": %d },
                    { "uuid": "fake-005", "status": "Scheduled", "startDateTime": %d }
                  ]
                }
                """.formatted(startMillis, startMillis + 86400000, startMillis + 172800000);

        wireMock.stubFor(get(urlPathEqualTo("/ws/rest/v1/appointments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));

        List<JsonNode> resultaat = fetcher.fetchAppointments(tenant);

        assertThat(resultaat).hasSize(3);
    }

    @Test
    void fetchAppointments_stuurtBasicAuthHeader() {
        wireMock.stubFor(get(urlPathEqualTo("/ws/rest/v1/appointments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"results\":[]}")));

        fetcher.fetchAppointments(tenant);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/ws/rest/v1/appointments"))
                .withHeader("Authorization", matching("Basic .+")));
    }

    // ── Foutgevallen — lege lijst terug, geen exception ───────────────────────

    @Test
    void fetchAppointments_404_geeftLegeListTerug() {
        wireMock.stubFor(get(urlPathEqualTo("/ws/rest/v1/appointments"))
                .willReturn(aResponse().withStatus(404)));

        List<JsonNode> resultaat = fetcher.fetchAppointments(tenant);

        assertThat(resultaat).isEmpty();
    }

    @Test
    void fetchAppointments_500ServerFout_geeftLegeListTerug() {
        wireMock.stubFor(get(urlPathEqualTo("/ws/rest/v1/appointments"))
                .willReturn(aResponse().withStatus(500)));

        List<JsonNode> resultaat = fetcher.fetchAppointments(tenant);

        assertThat(resultaat).isEmpty();
    }

    @Test
    void fetchAppointments_legeResultsArray_geeftLegeListTerug() {
        wireMock.stubFor(get(urlPathEqualTo("/ws/rest/v1/appointments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"results\":[]}")));

        List<JsonNode> resultaat = fetcher.fetchAppointments(tenant);

        assertThat(resultaat).isEmpty();
    }

    @Test
    void fetchAppointments_connectieFout_geeftLegeListTerug() {
        // Punt naar een poort waarop niets luistert
        tenant.setOpenMrsBaseUrl("http://localhost:1");

        List<JsonNode> resultaat = fetcher.fetchAppointments(tenant);

        assertThat(resultaat).isEmpty();
    }
}
