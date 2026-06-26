package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * FMEA Scenario 4 - RPN 12 - HOOG - NFR1, NFR9
 *
 * Scenario: Credential-rotatie door ziekenhuis (API-key vervangen zonder update module).
 * Effect:   Alle polls en notificaties voor die tenant mislukken.
 * Maatregel: HTTP 401 van OpenMRS leidt tot lege lijst en ERROR-log.
 *            De applicatie crasht niet; beheerder kan credential updaten via .env.
 */
class Fmea04CredentialRotatieTest extends FmeaBaseTest {

    @Test
    void credential_rotatie_openmrs_401_geeft_lege_lijst_geen_crash() {
        openMrsMock.stubFor(get(urlPathMatching("/ws/rest/v1/appointments.*"))
                .willReturn(aResponse().withStatus(401).withBody("Unauthorized")));

        List<JsonNode> result = appointmentFetcher.fetchAppointments(openMrsTenant("fmea04-tenant"));

        assertThat(result)
                .as("HTTP 401 (verlopen credential) mag de applicatie niet laten crashen")
                .isEmpty();
    }

    @Test
    void credential_rotatie_openmrs_403_geeft_lege_lijst_geen_crash() {
        openMrsMock.stubFor(get(urlPathMatching("/ws/rest/v1/appointments.*"))
                .willReturn(aResponse().withStatus(403).withBody("Forbidden")));

        List<JsonNode> result = appointmentFetcher.fetchAppointments(openMrsTenant("fmea04-tenant-403"));

        assertThat(result)
                .as("HTTP 403 (onvoldoende rechten) mag de applicatie niet laten crashen")
                .isEmpty();
    }
}
