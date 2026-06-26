package com.example.atx24softwarearchitectuurkwaliteit.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Configuratie-unit-tests die de definitie van het RabbitMQ-dashboard
 * ({@code grafana/dashboards/rabbitmq.json}) bewaken. Geen draaiende Grafana nodig.
 * Vangt stille regressies af: hernoemde datasource-uid, gesloopte PromQL, dubbele paneel-id's,
 * of een queue-naam/job-label dat niet meer matcht met wat Prometheus daadwerkelijk scrapet.
 */
class RabbitMqDashboardTest {

    private static final Path DASHBOARD = Path.of("grafana/dashboards/rabbitmq.json");
    private static final Path PROMETHEUS = Path.of("prometheus.yml");
    private static final Path RABBITMQ_DATASOURCE =
            Path.of("grafana/provisioning/datasources/rabbitmq-prometheus.yml");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode dashboard() throws IOException {
        assertTrue(Files.exists(DASHBOARD), "Dashboardbestand ontbreekt: " + DASHBOARD.toAbsolutePath());
        return MAPPER.readTree(Files.readString(DASHBOARD));
    }

    private List<JsonNode> panels() throws IOException {
        List<JsonNode> result = new ArrayList<>();
        dashboard().path("panels").forEach(result::add);
        return result;
    }

    /** Panelen met daadwerkelijke queries (geen row/text/alertlist). */
    private List<JsonNode> dataPanels() throws IOException {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode panel : panels()) {
            if (panel.path("targets").isArray() && panel.path("targets").size() > 0) {
                result.add(panel);
            }
        }
        return result;
    }

    private List<String> allExprs() throws IOException {
        List<String> exprs = new ArrayList<>();
        for (JsonNode panel : dataPanels()) {
            for (JsonNode target : panel.path("targets")) {
                String expr = target.path("expr").asText("");
                if (!expr.isBlank()) {
                    exprs.add(expr);
                }
            }
        }
        return exprs;
    }

    private boolean anyExprContains(String needle) throws IOException {
        return allExprs().stream().anyMatch(e -> e.contains(needle));
    }

    // ── Structuur ────────────────────────────────────────────────────────────

    @Test
    void dashboard_isValidJsonWithStableUid() throws IOException {
        JsonNode root = dashboard();
        assertEquals("rabbitmq-overview", root.path("uid").asText(),
                "De uid moet stabiel blijven; provisioning en links verwijzen ernaar");
        assertFalse(root.path("title").asText().isBlank(), "Dashboard mist een titel");
    }

    @Test
    void everyPanel_hasUniqueIdAndTitle() throws IOException {
        Set<Integer> ids = new HashSet<>();
        for (JsonNode panel : panels()) {
            int id = panel.path("id").asInt(-1);
            assertTrue(id >= 0, "Elk paneel heeft een id nodig");
            assertTrue(ids.add(id), "Dubbele paneel-id " + id + " — Grafana voegt panelen dan samen");
            assertFalse(panel.path("title").asText().isBlank(), "Paneel " + id + " mist een titel");
        }
    }

    // ── Datasource-koppeling ─────────────────────────────────────────────────

    @Test
    void logsPanels_useLoki_metricPanels_usePrometheus() throws IOException {
        for (JsonNode panel : dataPanels()) {
            String type = panel.path("type").asText();
            String uid = panel.path("datasource").path("uid").asText();
            if ("logs".equals(type)) {
                assertEquals("loki", uid, "Logspaneel '" + panel.path("title").asText() + "' moet Loki gebruiken");
            } else {
                assertEquals("prometheus-rabbitmq", uid,
                        "Metricpaneel '" + panel.path("title").asText() + "' moet Prometheus gebruiken");
            }
        }
    }

    // ── Kernpanelen die de FMEA-dashboardwaarden tonen ───────────────────────

    @Test
    void hasDeadLetterQueuePanel() throws IOException {
        assertTrue(anyExprContains("notification.dead-letter.queue"),
                "Er hoort een paneel te zijn dat de dead-letter queue toont (stille mislukking)");
    }

    @Test
    void hasSuccessAndFailedTransactionPanels() throws IOException {
        assertTrue(anyExprContains("notifications_sent_total"),
                "Het dashboard hoort de notifications_sent_total-metric te tonen");
        assertTrue(allExprs().stream().anyMatch(e -> e.contains("notifications_sent_total") && e.contains("status=\"success\"")),
                "Er hoort een paneel te zijn voor geslaagde transacties (status=success)");
        assertTrue(allExprs().stream().anyMatch(e -> e.contains("notifications_sent_total") && e.contains("status=\"failed\"")),
                "Er hoort een paneel te zijn voor mislukte transacties (status=failed)");
    }

    @Test
    void hasProviderRateLimitPanel() throws IOException {
        assertTrue(anyExprContains("status=\"429\""),
                "Er hoort een paneel te zijn voor provider rate-limiting (HTTP 429)");
    }

    @Test
    void hasErrorAndWarnLogsPanel() throws IOException {
        assertTrue(anyExprContains("detected_level"),
                "Er hoort een logspaneel te zijn dat op ERROR/WARN filtert");
    }

    @Test
    @SuppressWarnings("unchecked")
    void prometheusDatasource_isActuallyProvisioned() throws IOException {
        // De metricpanelen wijzen op uid 'prometheus-rabbitmq'. Staat die datasource niet in
        // de provisioning, dan blijft het hele dashboard leeg zonder dat iets het opmerkt.
        assertTrue(Files.exists(RABBITMQ_DATASOURCE),
                "Datasource-config ontbreekt: " + RABBITMQ_DATASOURCE.toAbsolutePath());
        Map<String, Object> root = new Yaml().load(Files.readString(RABBITMQ_DATASOURCE));
        List<Map<String, Object>> datasources = (List<Map<String, Object>>) root.get("datasources");
        boolean found = datasources.stream().anyMatch(ds ->
                "prometheus-rabbitmq".equals(ds.get("uid")) && "prometheus".equals(ds.get("type")));
        assertTrue(found, "Datasource 'prometheus-rabbitmq' (type prometheus) moet geprovisioned zijn");
    }

    // ── Kruis-consistentie met Prometheus ────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void everyJobLabelMatchesARealPrometheusScrapeJob() throws IOException {
        assertTrue(Files.exists(PROMETHEUS), "prometheus.yml ontbreekt");
        Map<String, Object> root = new Yaml().load(Files.readString(PROMETHEUS));
        List<Map<String, Object>> scrapeConfigs = (List<Map<String, Object>>) root.get("scrape_configs");
        Set<String> jobs = new HashSet<>();
        for (Map<String, Object> cfg : scrapeConfigs) {
            jobs.add(String.valueOf(cfg.get("job_name")));
        }

        // Elke job="..."-verwijzing in de PromQL moet een echte scrape-job zijn,
        // anders blijft het paneel leeg.
        Pattern jobPattern = Pattern.compile("job=~?\"([^\"]+)\"");
        for (String expr : allExprs()) {
            Matcher m = jobPattern.matcher(expr);
            while (m.find()) {
                String job = m.group(1);
                assertTrue(jobs.contains(job),
                        "PromQL verwijst naar onbekende job '" + job + "'. Bekend: " + jobs);
            }
        }
    }
}
