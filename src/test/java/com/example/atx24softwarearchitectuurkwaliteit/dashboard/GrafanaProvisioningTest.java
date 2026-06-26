package com.example.atx24softwarearchitectuurkwaliteit.dashboard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bewaakt de Grafana-<em>provisioning</em>: de Loki/Tempo-datasources én de wiring in
 * docker-compose. Als deze koppelingen breken, laadt het dashboard wel maar werkt het niet
 * (lege panelen, dode trace-links). Deze tests vangen dat af zonder een draaiende stack.
 */
class GrafanaProvisioningTest {

    private static final Path DATASOURCES = Path.of("grafana/provisioning/datasources/loki-tempo.yml");
    private static final Path DASHBOARD_PROVIDER = Path.of("grafana/provisioning/dashboards/rabbitmq-dashboards.yml");
    private static final Path COMPOSE = Path.of("docker-compose.yml");

    @SuppressWarnings("unchecked")
    private Map<String, Object> findDatasource(String uid) throws IOException {
        assertTrue(Files.exists(DATASOURCES), "Datasource-config ontbreekt: " + DATASOURCES.toAbsolutePath());
        Map<String, Object> root = new Yaml().load(Files.readString(DATASOURCES));
        List<Map<String, Object>> datasources = (List<Map<String, Object>>) root.get("datasources");
        assertNotNull(datasources, "datasources-sectie ontbreekt");
        return datasources.stream()
                .filter(ds -> uid.equals(ds.get("uid")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Datasource met uid '" + uid + "' ontbreekt"));
    }

    @Test
    void lokiDatasource_pointsAtLokiOnPort3100() throws IOException {
        Map<String, Object> loki = findDatasource("loki-app");
        assertEquals("loki", loki.get("type"));
        assertEquals("http://localhost:3100", loki.get("url"),
                "Loki draait binnen het otel-lgtm-image op poort 3100");
    }

    @Test
    @SuppressWarnings("unchecked")
    void lokiDatasource_linksTraceIdToTempo() throws IOException {
        Map<String, Object> loki = findDatasource("loki-app");
        Map<String, Object> jsonData = (Map<String, Object>) loki.get("jsonData");
        List<Map<String, Object>> derivedFields = (List<Map<String, Object>>) jsonData.get("derivedFields");
        assertNotNull(derivedFields, "Loki moet een derived field hebben om naar Tempo te linken");

        Map<String, Object> traceField = derivedFields.get(0);
        assertEquals("trace_id", traceField.get("matcherRegex"));
        assertEquals("tempo-app", traceField.get("datasourceUid"),
                "Het trace_id-veld moet naar de Tempo-datasource verwijzen");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tempoDatasource_existsAndLinksBackToLoki() throws IOException {
        Map<String, Object> tempo = findDatasource("tempo-app");
        assertEquals("tempo", tempo.get("type"));
        assertEquals("http://localhost:3200", tempo.get("url"),
                "Tempo draait binnen het otel-lgtm-image op poort 3200");

        Map<String, Object> jsonData = (Map<String, Object>) tempo.get("jsonData");
        Map<String, Object> tracesToLogs = (Map<String, Object>) jsonData.get("tracesToLogsV2");
        assertNotNull(tracesToLogs, "Tempo moet een traces->logs terugkoppeling hebben");
        assertEquals("loki-app", tracesToLogs.get("datasourceUid"),
                "Vanuit een trace moet je terug naar de Loki-logs kunnen");
    }

    @Test
    void dockerCompose_mountsLokiTempoDatasourceIntoLgtm() throws IOException {
        assertTrue(Files.exists(COMPOSE), "docker-compose.yml ontbreekt");
        String compose = Files.readString(COMPOSE);
        assertTrue(compose.contains(
                        "loki-tempo.yml:/otel-lgtm/grafana/conf/provisioning/datasources/"),
                "docker-compose moet de Loki/Tempo-datasource in de lgtm-provisioning mounten");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dashboardJson_isReachableByTheGrafanaDashboardProvider() throws IOException {
        // De provider scant een map; docker-compose mount onze dashboards naar diezelfde map.
        assertTrue(Files.exists(DASHBOARD_PROVIDER), "Dashboard-provider config ontbreekt");
        Map<String, Object> providerRoot = new Yaml().load(Files.readString(DASHBOARD_PROVIDER));
        List<Map<String, Object>> providers = (List<Map<String, Object>>) providerRoot.get("providers");
        String scannedPath = (String) ((Map<String, Object>) providers.get(0).get("options")).get("path");
        assertEquals("/grafana/dashboards", scannedPath);

        String compose = Files.readString(COMPOSE);
        assertTrue(compose.contains("./grafana/dashboards:/grafana/dashboards"),
                "docker-compose moet de dashboardmap naar de gescande provider-map mounten, "
                        + "anders wordt error-tracing.json nooit geladen");
    }
}
