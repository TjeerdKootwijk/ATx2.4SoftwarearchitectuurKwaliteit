package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import org.junit.jupiter.api.Test;

import java.nio.file.ProviderNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FMEA Scenario 5 - RPN 12 - HOOG - NFR1, NFR7
 *
 * Scenario: Onbekende tenantId in binnenkomend bericht.
 * Effect:   Bericht valt stilzwijgend weg; patient krijgt geen notificatie.
 * Maatregel: MessagingProviderFactory gooit ProviderNotFoundException
 *            met duidelijke foutmelding zodat de consumer het bericht als
 *            FAILED kan behandelen en naar DLQ kan sturen.
 */
class Fmea05OnbekendeTenantTest extends FmeaBaseTest {

    @Test
    void onbekende_provider_naam_gooit_provider_not_found_exception() {
        assertThatThrownBy(() -> providerFactory.get("ONBEKENDE_PROVIDER_XYZ"))
                .as("Onbekende provider moet een duidelijke ProviderNotFoundException geven")
                .isInstanceOf(ProviderNotFoundException.class)
                .hasMessageContaining("ONBEKENDE_PROVIDER_XYZ");
    }

    @Test
    void provider_not_found_exception_bevat_lijst_van_bekende_providers() {
        try {
            providerFactory.get("NIET_BESTAAND");
        } catch (ProviderNotFoundException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            assertThat(msg).containsAnyOf("SWIFTSEND", "LEGACYLINK", "ASYNCFLOW", "SECUREPOST");
        }
    }
}
