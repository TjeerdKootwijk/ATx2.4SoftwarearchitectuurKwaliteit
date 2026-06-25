package com.example.atx24softwarearchitectuurkwaliteit.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Systeemtest voor het dashboard — valideert het <em>draaiende</em> Grafana via de HTTP API.
 *
 * <p>Waar de unit-tests alleen de dashboard-definitie en de backend-waarde controleren, bewijst
 * deze test dat elk onderdeel ook echt live werkt: dashboards geladen, datasources aangesloten,
 * Grafana kan Prometheus bereiken, alerts geprovisioned, en er komt echte data uit de pijplijn.
 * Dit is het dashboard-equivalent van de end-to-end {@code SystemTest} van de notificatieketen.</p>
 *
 * <p><strong>Vereist een draaiende stack</strong> ({@code docker-compose up}). Is Grafana niet
 * bereikbaar, dan slaat de test zichzelf over (JUnit assumptions) zodat de gewone build groen
 * blijft. De Grafana-URL is instelbaar via de omgevingsvariabele {@code GRAFANA_URL}
 * (standaard {@code http://localhost:3000}).</p>
 */
@Tag("system")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DashboardSystemTest {

    private static final String BASE_URL = resolveBaseUrl();
    private static final String AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8));
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    private static boolean grafanaUp = false;

    private static String resolveBaseUrl() {
        String env = System.getenv("GRAFANA_URL");
        if (env != null && !env.isBlank()) {
            return env.replaceAll("/+$", "");
        }
        String prop = System.getProperty("grafana.url");
        if (prop != null && !prop.isBlank()) {
            return prop.replaceAll("/+$", "");
        }
        return "http://localhost:3000";
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", AUTH)
                .GET()
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @BeforeAll
    static void pingGrafana() {
        try {
            grafanaUp = get("/api/health").statusCode() == 200;
        } catch (Exception e) {
            grafanaUp = false;
        }
    }

    @BeforeEach
    void requireRunningStack() {
        assumeTrue(grafanaUp,
                "Grafana niet bereikbaar op " + BASE_URL
                        + " — start eerst de stack (docker-compose up). Test overgeslagen.");
    }

    // ── 1. Grafana zelf ───────────────────────────────────────────────────────

    @Test
    @Order(1)
    void grafana_isHealthy() throws Exception {
        HttpResponse<String> r = get("/api/health");
        assertEquals(200, r.statusCode(), "Grafana /api/health hoort 200 te geven");
    }

    // ── 2. De dashboards zijn geladen ─────────────────────────────────────────

    @Test
    @Order(2)
    void rabbitMqDashboard_isLoadedWithPanels() throws Exception {
        HttpResponse<String> r = get("/api/dashboards/uid/rabbitmq-overview");
        assertEquals(200, r.statusCode(), "RabbitMQ-dashboard is niet geladen in Grafana");
        JsonNode panels = MAPPER.readTree(r.body()).path("dashboard").path("panels");
        assertTrue(panels.isArray() && panels.size() > 0,
                "RabbitMQ-dashboard is geladen maar heeft geen panelen");
    }

    @Test
    @Order(3)
    void errorTracingDashboard_isLoaded() throws Exception {
        HttpResponse<String> r = get("/api/dashboards/uid/error-tracing");
        assertEquals(200, r.statusCode(), "Foutopsporing-dashboard is niet geladen in Grafana");
    }

    // ── 3. De datasources bestaan en zijn aangesloten ─────────────────────────

    @Test
    @Order(4)
    void provisionedDatasources_exist() throws Exception {
        HttpResponse<String> r = get("/api/datasources");
        assertEquals(200, r.statusCode(), "Datasources-endpoint niet bereikbaar");
        Set<String> uids = new HashSet<>();
        MAPPER.readTree(r.body()).forEach(ds -> uids.add(ds.path("uid").asText()));
        assertTrue(uids.contains("prometheus-rabbitmq"),
                "Datasource 'prometheus-rabbitmq' ontbreekt in Grafana. Aanwezig: " + uids);
        assertTrue(uids.contains("loki-app"),
                "Datasource 'loki-app' ontbreekt in Grafana. Aanwezig: " + uids);
    }

    @Test
    @Order(5)
    void prometheusDatasource_isHealthy() throws Exception {
        // Health-check vanuit Grafana zelf: bewijst dat Grafana Prometheus echt kan bereiken.
        HttpResponse<String> r = get("/api/datasources/uid/prometheus-rabbitmq/health");
        assertEquals(200, r.statusCode(), "Health-endpoint van prometheus-rabbitmq niet bereikbaar");
        String status = MAPPER.readTree(r.body()).path("status").asText();
        assertEquals("OK", status,
                "Grafana kan Prometheus niet bereiken (datasource health is niet OK)");
    }

    // ── 4. De alerts zijn geprovisioned ───────────────────────────────────────

    @Test
    @Order(6)
    void alertRules_areProvisioned() throws Exception {
        HttpResponse<String> r = get("/api/v1/provisioning/alert-rules");
        assertEquals(200, r.statusCode(), "Provisioning alert-rules endpoint niet bereikbaar");
        Set<String> uids = new HashSet<>();
        MAPPER.readTree(r.body()).forEach(rule -> uids.add(rule.path("uid").asText()));
        assertTrue(uids.contains("alert-dlq-not-empty"),
                "DLQ-alert is niet geprovisioned in Grafana. Aanwezig: " + uids);
        assertTrue(uids.contains("alert-rabbitmq-down"),
                "RabbitMQ-down-alert is niet geprovisioned in Grafana. Aanwezig: " + uids);
    }

    // ── 5. Er komt echt data uit de pijplijn ──────────────────────────────────

    @Test
    @Order(7)
    void prometheusDatasource_returnsLiveData() throws Exception {
        // Echte query dóór de Prometheus-datasource heen: bewijst dat de hele keten
        // (Grafana -> Prometheus -> targets) data oplevert, anders zouden de panelen leeg zijn.
        HttpResponse<String> r =
                get("/api/datasources/proxy/uid/prometheus-rabbitmq/api/v1/query?query=up");
        assertEquals(200, r.statusCode(), "Query via de Prometheus-datasource mislukt");
        JsonNode result = MAPPER.readTree(r.body()).path("data").path("result");
        assertTrue(result.isArray() && result.size() > 0,
                "Prometheus levert geen 'up'-data; de metricpanelen zouden leeg zijn");
    }
}
