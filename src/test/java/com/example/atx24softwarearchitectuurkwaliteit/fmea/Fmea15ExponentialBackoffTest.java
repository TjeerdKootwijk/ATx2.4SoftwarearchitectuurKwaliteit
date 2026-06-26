package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FMEA Scenario 15 - RPN 8 - MIDDEN - NFR7
 *
 * Scenario: DNS-resolutie van externe provider mislukt.
 * Effect:   Notificaties kunnen niet worden verstuurd; berichten eindigen in DLQ.
 * Maatregel: Exponential backoff herprobeert met oplopende vertragingen:
 *            5s -> 30s -> 2min -> 5min -> 10min -> DLQ na 5 pogingen.
 */
class Fmea15ExponentialBackoffTest extends FmeaBaseTest {

    @Test
    void backoff_delays_zijn_oplopend_conform_fmea_specificatie() {
        long[] verwacht = {5_000L, 30_000L, 120_000L, 300_000L, 600_000L};

        for (int i = 0; i < verwacht.length; i++) {
            assertThat(retryPolicy.getDelayMillis(i))
                    .as("Vertraging bij poging %d moet %d ms zijn", i + 1, verwacht[i])
                    .isEqualTo(verwacht[i]);
        }
    }

    @Test
    void backoff_staat_retry_toe_voor_het_bereiken_van_max_pogingen() {
        for (int i = 0; i < retryPolicy.getMaxRetries(); i++) {
            assertThat(retryPolicy.shouldRetry(i))
                    .as("Poging %d moet als her-probeerbaar worden beschouwd", i + 1)
                    .isTrue();
        }
    }

    @Test
    void backoff_weigert_retry_na_bereiken_van_max_pogingen() {
        assertThat(retryPolicy.shouldRetry(retryPolicy.getMaxRetries()))
                .as("Na %d pogingen moet shouldRetry false zijn zodat bericht naar DLQ gaat",
                        retryPolicy.getMaxRetries())
                .isFalse();
    }

    @Test
    void backoff_delays_zijn_strikt_oplopend() {
        long vorigeDelay = 0;
        for (int i = 0; i < retryPolicy.getMaxRetries(); i++) {
            long huidigeDelay = retryPolicy.getDelayMillis(i);
            assertThat(huidigeDelay)
                    .as("Delay bij poging %d moet groter zijn dan vorige delay (exponential)", i + 1)
                    .isGreaterThan(vorigeDelay);
            vorigeDelay = huidigeDelay;
        }
    }
}
