package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FMEA Scenario 17 - RPN 6 - LAAG - NFR9
 *
 * Scenario: Prometheus scrape mislukt (metrics niet beschikbaar).
 * Effect:   Grafana-dashboard leeg; operationele problemen onzichtbaar.
 * Maatregel: MeterRegistry is actief en bevat notificatiemetrics.
 *            Grafana-alert bij meer dan 2 minuten dataverlies.
 */
class Fmea17PrometheusTest extends FmeaBaseTest {

    @Autowired
    MeterRegistry meterRegistry;

    @Test
    void meter_registry_is_aanwezig_voor_prometheus_export() {
        assertThat(meterRegistry)
                .as("MeterRegistry moet beschikbaar zijn voor Prometheus-export")
                .isNotNull();
    }

    @Test
    void meter_registry_bevat_standaard_jvm_metrics() {
        assertThat(meterRegistry.getMeters())
                .as("MeterRegistry moet standaard JVM-metrics bevatten zodat Grafana basistelemetrie heeft")
                .isNotEmpty();
    }

    @Test
    void na_verwerking_bevat_registry_notificatie_counter() {
        stubProviderSucces();
        consumer.consume(toAmqpMessage(swiftSendBericht("fmea17-tenant")));

        // De counter wordt aangemaakt bij de eerste notificatie
        boolean counterAanwezig = meterRegistry.getMeters().stream()
                .anyMatch(m -> m.getId().getName().contains("notification"));

        assertThat(counterAanwezig)
                .as("Na een verwerkte notificatie moet er een 'notification'-counter in de registry zijn")
                .isTrue();
    }
}
