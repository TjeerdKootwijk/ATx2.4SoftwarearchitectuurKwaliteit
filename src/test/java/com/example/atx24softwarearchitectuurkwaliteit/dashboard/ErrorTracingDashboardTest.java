package com.example.atx24softwarearchitectuurkwaliteit.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Configuratie-unit-tests die de definitie van het Foutopsporing-dashboard
 * ({@code grafana/dashboards/error-tracing.json}) bewaken. Geen draaiende Grafana nodig:
 * deze tests vangen stille regressies af (hernoemde datasource-uid, gesloopte LogQL-query,
 * uitgezette log-details, dubbele paneel-id's) die anders pas zichtbaar worden in productie.
 * Het bestand wordt gelezen vanaf de projectroot (de werkmap van {@code gradle test}).
 */
class ErrorTracingDashboardTest {

    private static final Path DASHBOARD = Path.of("grafana/dashboards/error-tracing.json");
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

    private JsonNode panelOfType(String type) throws IOException {
        for (JsonNode panel : panels()) {
            if (type.equals(panel.path("type").asText())) {
                return panel;
            }
        }
        return null;
    }

    private boolean isDataPanel(JsonNode panel) {
        String type = panel.path("type").asText();
        return !"text".equals(type) && !"row".equals(type);
    }

    // ── Structuur ────────────────────────────────────────────────────────────

    @Test
    void dashboard_isValidJsonWithStableUid() throws IOException {
        JsonNode root = dashboard();
        assertEquals("error-tracing", root.path("uid").asText(),
                "De uid moet stabiel blijven; provisioning en links verwijzen ernaar");
        assertTrue(root.path("title").asText().toLowerCase().contains("foutopsporing"));
    }

    @Test
    void dashboard_hasAutoRefreshAndTimeRange() throws IOException {
        JsonNode root = dashboard();
        assertFalse(root.path("refresh").asText().isBlank(),
                "Een monitoringdashboard hoort auto-refresh te hebben");
        assertFalse(root.path("time").path("from").asText().isBlank(), "Tijdbereik 'from' ontbreekt");
        assertFalse(root.path("time").path("to").asText().isBlank(), "Tijdbereik 'to' ontbreekt");
    }

    @Test
    void everyPanel_hasUniqueIdTitleAndSize() throws IOException {
        Set<Integer> ids = new HashSet<>();
        for (JsonNode panel : panels()) {
            int id = panel.path("id").asInt(-1);
            assertTrue(id >= 0, "Elk paneel heeft een id nodig");
            assertTrue(ids.add(id), "Dubbele paneel-id " + id + " — Grafana voegt panelen dan samen");
            assertFalse(panel.path("title").asText().isBlank(),
                    "Paneel " + id + " mist een titel");
            assertTrue(panel.path("gridPos").path("w").asInt() > 0
                            && panel.path("gridPos").path("h").asInt() > 0,
                    "Paneel " + id + " heeft geen geldige afmeting (gridPos w/h)");
        }
    }

    // ── Datasource-koppeling ─────────────────────────────────────────────────

    @Test
    void everyDataPanel_usesTheProvisionedLokiDatasource() throws IOException {
        for (JsonNode panel : panels()) {
            if (!isDataPanel(panel)) {
                continue;
            }
            assertEquals("loki-app", panel.path("datasource").path("uid").asText(),
                    "Paneel '" + panel.path("title").asText() + "' moet de loki-app datasource gebruiken");
            // Ook elke target moet expliciet dezelfde datasource benoemen.
            for (JsonNode target : panel.path("targets")) {
                assertEquals("loki-app", target.path("datasource").path("uid").asText(),
                        "Een target in paneel '" + panel.path("title").asText() + "' wijst naar een andere datasource");
            }
        }
    }

    // ── Het logs-paneel: de kern (stacktrace + context openklapbaar) ─────────

    @Test
    void logsPanel_existsAndFiltersAppErrors() throws IOException {
        JsonNode logs = panelOfType("logs");
        assertNotNull(logs, "Er moet een logs-paneel zijn dat de foutregels toont");

        String expr = logs.path("targets").path(0).path("expr").asText();
        assertTrue(expr.contains("service_name=\"atx24-app\""),
                "Query moet op de applicatie filteren, maar was: " + expr);
        assertTrue(expr.toLowerCase().contains("mislukt") || expr.toLowerCase().contains("error")
                        || expr.toLowerCase().contains("exception"),
                "Query moet op foutregels filteren, maar was: " + expr);
    }

    @Test
    void logsPanel_hasLogDetailsEnabledSoStacktraceAndContextCanBeExpanded() throws IOException {
        JsonNode logs = panelOfType("logs");
        assertNotNull(logs);
        assertTrue(logs.path("options").path("enableLogDetails").asBoolean(),
                "enableLogDetails moet aan staan; anders kun je stacktrace + context niet openklappen");
        assertEquals("Descending", logs.path("options").path("sortOrder").asText(),
                "Nieuwste fouten horen bovenaan");
    }

    // ── Overzichtspanelen ────────────────────────────────────────────────────

    @Test
    void dashboard_hasAnErrorCountOverview() throws IOException {
        boolean hasCount = panels().stream()
                .filter(this::isDataPanel)
                .flatMap(p -> {
                    List<JsonNode> t = new ArrayList<>();
                    p.path("targets").forEach(t::add);
                    return t.stream();
                })
                .anyMatch(t -> t.path("expr").asText().contains("count_over_time"));
        assertTrue(hasCount, "Er hoort een paneel te zijn dat het aantal fouten telt (count_over_time)");
    }

    // ── Uitlegpaneel ──────────────────────────────────────────────────────────

    @Test
    void helpPanel_explainsTheTraceWorkflow() throws IOException {
        JsonNode text = panelOfType("text");
        assertNotNull(text, "Er hoort een uitleg-/tekstpaneel te zijn");
        String content = text.path("options").path("content").asText().toLowerCase();
        assertTrue(content.contains("trace_id"), "Uitleg moet de trace_id-doorklik noemen");
        assertTrue(content.contains("tempo"), "Uitleg moet Tempo noemen");
        assertTrue(content.contains("regel") || content.contains("stacktrace"),
                "Uitleg moet de stacktrace/regelnummer-herleidbaarheid noemen");
    }
}
