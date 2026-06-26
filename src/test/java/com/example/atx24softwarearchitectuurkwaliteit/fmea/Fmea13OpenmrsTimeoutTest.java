package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FMEA Scenario 13 - RPN 9 - MIDDEN - NFR7
 *
 * Scenario: OpenMRS antwoordt traag (>30 s timeout).
 * Effect:   Polling-thread blokkeert; volgende tenant-polls vertragen.
 * Maatregel: Bij verbindingsfout of onbereikbare host geeft de fetcher
 *            een lege lijst terug. De aanroep duurt niet langer dan 10 seconden.
 */
class Fmea13OpenmrsTimeoutTest extends FmeaBaseTest {

    @Test
    void openmrs_onbereikbaar_geeft_lege_lijst_binnen_acceptabele_tijd() {
        // Poort 19881 is niet in gebruik — simuleert onbereikbare host (connection refused)
        TenantConfiguration onbereikbareTenant = new TenantConfiguration("fmea13-tenant", "Timeout Ziekenhuis");
        onbereikbareTenant.setOpenMrsBaseUrl("http://localhost:19881");
        onbereikbareTenant.setOpenMrsUsername("admin");
        onbereikbareTenant.setOpenMrsPassword("admin");

        long start = System.currentTimeMillis();
        List<JsonNode> result = appointmentFetcher.fetchAppointments(onbereikbareTenant);
        long duur = System.currentTimeMillis() - start;

        assertThat(result)
                .as("Bij onbereikbare OpenMRS moet de fetcher een lege lijst teruggeven")
                .isEmpty();
        assertThat(duur)
                .as("Fetch-aanroep mag niet langer dan 10 seconden duren (geen thread-blokkade)")
                .isLessThan(10_000L);
    }
}
