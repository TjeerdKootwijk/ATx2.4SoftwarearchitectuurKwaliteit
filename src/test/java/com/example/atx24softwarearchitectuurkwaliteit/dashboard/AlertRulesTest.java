package com.example.atx24softwarearchitectuurkwaliteit.dashboard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bewaakt de Grafana alert-regels ({@code grafana/provisioning/alerting/alert-rules.yml}).
 * De alerts zijn het "actieve" deel van het dashboard: ze vuren bij DLQ-groei, 429's,
 * provider-storingen en down-targets. Deze tests vangen zonder draaiende stack af dat een
 * alert per ongeluk wordt verwijderd, de drempel verschuift, of de PromQL-query breekt.
 */
class AlertRulesTest {

    private static final Path ALERTS = Path.of("grafana/provisioning/alerting/alert-rules.yml");

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rules() throws IOException {
        assertTrue(Files.exists(ALERTS), "Alert-config ontbreekt: " + ALERTS.toAbsolutePath());
        Map<String, Object> root = new Yaml().load(Files.readString(ALERTS));
        List<Map<String, Object>> groups = (List<Map<String, Object>>) root.get("groups");
        assertNotNull(groups, "groups-sectie ontbreekt");
        List<Map<String, Object>> all = new ArrayList<>();
        for (Map<String, Object> group : groups) {
            all.addAll((List<Map<String, Object>>) group.get("rules"));
        }
        return all;
    }

    private Map<String, Object> ruleByUid(String uid) throws IOException {
        return rules().stream()
                .filter(r -> uid.equals(r.get("uid")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Alert met uid '" + uid + "' ontbreekt"));
    }

    /** Haalt de PromQL (expr) op uit het eerste data-model (refId A) van een regel. */
    @SuppressWarnings("unchecked")
    private String exprOf(Map<String, Object> rule) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) rule.get("data");
        for (Map<String, Object> d : data) {
            Map<String, Object> model = (Map<String, Object>) d.get("model");
            Object expr = model.get("expr");
            if (expr != null) {
                return expr.toString();
            }
        }
        throw new AssertionError("Regel '" + rule.get("title") + "' heeft geen PromQL-expr");
    }

    @Test
    void allExpectedAlertsArePresent() throws IOException {
        Set<String> expected = Set.of(
                "alert-dlq-not-empty",
                "alert-provider-429",
                "alert-provider-5xx",
                "alert-app-metrics-down",
                "alert-rabbitmq-down",
                "alert-veel-mislukt",
                "alert-retry-vol");
        Set<String> actual = new java.util.HashSet<>();
        for (Map<String, Object> rule : rules()) {
            actual.add(String.valueOf(rule.get("uid")));
        }
        assertTrue(actual.containsAll(expected),
                "Ontbrekende alert(s). Verwacht: " + expected + " maar gevonden: " + actual);
    }

    @Test
    void everyRule_hasTitleConditionAndExpr() throws IOException {
        for (Map<String, Object> rule : rules()) {
            assertFalse(String.valueOf(rule.get("title")).isBlank(),
                    "Alert " + rule.get("uid") + " mist een titel");
            assertNotNull(rule.get("condition"), "Alert " + rule.get("uid") + " mist een condition");
            assertFalse(exprOf(rule).isBlank(), "Alert " + rule.get("uid") + " mist een PromQL-expr");
        }
    }

    @Test
    void dlqAlert_watchesTheDeadLetterQueue() throws IOException {
        String expr = exprOf(ruleByUid("alert-dlq-not-empty"));
        assertTrue(expr.contains("notification.dead-letter.queue"),
                "De DLQ-alert moet de dead-letter queue bewaken, was: " + expr);
    }

    @Test
    void rateLimitAlert_watchesHttp429() throws IOException {
        String expr = exprOf(ruleByUid("alert-provider-429"));
        assertTrue(expr.contains("status=\"429\""),
                "De rate-limit-alert moet op HTTP 429 letten, was: " + expr);
    }

    @Test
    void veelMisluktAlert_usesTheFailedNotificationCounter() throws IOException {
        String expr = exprOf(ruleByUid("alert-veel-mislukt"));
        assertTrue(expr.contains("notifications_sent_total") && expr.contains("status=\"failed\""),
                "De mislukkings-alert moet op de failed-counter letten, was: " + expr);
    }

    @Test
    @SuppressWarnings("unchecked")
    void veelMisluktAlert_firesAboveFiveFailures() throws IOException {
        // De drempel hoort > 5 te zijn; verschuift die ongemerkt, dan alarmeert het dashboard
        // te vroeg of te laat.
        Map<String, Object> rule = ruleByUid("alert-veel-mislukt");
        List<Map<String, Object>> data = (List<Map<String, Object>>) rule.get("data");
        boolean found = false;
        for (Map<String, Object> d : data) {
            Map<String, Object> model = (Map<String, Object>) d.get("model");
            List<Map<String, Object>> conditions = (List<Map<String, Object>>) model.get("conditions");
            if (conditions == null) {
                continue;
            }
            for (Map<String, Object> cond : conditions) {
                Map<String, Object> evaluator = (Map<String, Object>) cond.get("evaluator");
                if (evaluator != null && "gt".equals(evaluator.get("type"))) {
                    List<Object> params = (List<Object>) evaluator.get("params");
                    assertEquals(5, ((Number) params.get(0)).intValue(),
                            "De mislukkings-alert hoort bij > 5 te vuren");
                    found = true;
                }
            }
        }
        assertTrue(found, "Geen 'gt'-drempel gevonden in de mislukkings-alert");
    }
}
