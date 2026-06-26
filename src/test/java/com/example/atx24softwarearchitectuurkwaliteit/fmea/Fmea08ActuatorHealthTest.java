package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FMEA Scenario 8 - RPN 10 - MIDDEN - NFR5, NFR9
 *
 * Scenario: TLS-certificaat van de applicatie verloopt.
 * Effect:   Alle TLS-verbindingen weigeren; systeem volledig onbereikbaar.
 * Maatregel: /actuator/health is bereikbaar voor Grafana-monitoring.
 *            Grafana-alert bij resterende looptijd <30 dagen.
 *            Keystore gemonitord via actuator/health.
 */
class Fmea08ActuatorHealthTest extends FmeaBaseTest {

    @Test
    void actuator_health_endpoint_bereikbaar_voor_tls_certificaat_monitoring() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        // Endpoint moet antwoorden (2xx of 5xx maakt niet uit voor bereikbaarheid);
        // in de testomgeving is RabbitMQ gemockt, waardoor de status DOWN kan zijn.
        assertThat(response.getStatusCode().value())
                .as("/actuator/health moet antwoorden zodat Grafana TLS-problemen tijdig detecteert")
                .isIn(200, 503);
        assertThat(response.getBody())
                .as("Health response moet een 'status' veld bevatten")
                .contains("status");
    }

    @Test
    void actuator_health_bevat_status_veld() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        // In testomgeving is RabbitMQ gemockt; status is UP of DOWN afhankelijk van health-contributors.
        // Het gaat erom dat het endpoint antwoordt met een geldig JSON-statusveld.
        assertThat(response.getBody())
                .as("Health response moet een 'status' veld bevatten (UP of DOWN)")
                .containsAnyOf("\"UP\"", "\"DOWN\"");
    }
}
