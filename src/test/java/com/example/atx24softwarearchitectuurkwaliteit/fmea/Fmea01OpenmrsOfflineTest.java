package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * FMEA Scenario 1 - RPN 16 - HOOG - NFR7
 *
 * Scenario: OpenMRS offline tijdens gepland onderhoud.
 * Effect:   Polls worden overgeslagen; afspraken gemist tot herstel.
 * Maatregel: Fetcher geeft bij HTTP-fout een lege lijst terug (geen crash).
 *            Volgende poll-cyclus hervat normaal via date-range query.
 */
class Fmea01OpenmrsOfflineTest extends FmeaBaseTest {

    @Test
    void openmrs_offline_503_geeft_lege_lijst_geen_crash() {
        openMrsMock.stubFor(get(urlPathMatching("/ws/rest/v1/appointments.*"))
                .willReturn(aResponse().withStatus(503).withBody("Service Unavailable")));

        List<JsonNode> result = appointmentFetcher.fetchAppointments(openMrsTenant("fmea01-tenant"));

        assertThat(result)
                .as("OpenMRS offline mag de applicatie niet laten crashen; volgende cyclus hervat normaal")
                .isEmpty();
    }

    @Test
    void openmrs_offline_geeft_lege_lijst_bij_elke_5xx_statuscode() {
        for (int status : new int[]{500, 502, 503, 504}) {
            openMrsMock.stubFor(get(urlPathMatching("/ws/rest/v1/appointments.*"))
                    .willReturn(aResponse().withStatus(status)));

            List<JsonNode> result = appointmentFetcher.fetchAppointments(openMrsTenant("fmea01-tenant-" + status));

            assertThat(result)
                    .as("HTTP %d van OpenMRS moet resulteren in een lege lijst (geen crash)", status)
                    .isEmpty();
        }
    }
}
