package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * FMEA Scenario 12 - RPN 10 - MIDDEN - NFR5
 *
 * Scenario: AES-256-sleutel gelekt via omgevingsvariabele in logs.
 * Effect:   Versleutelde credentials kunnen worden ontsleuteld; privacybreuk.
 * Maatregel: Gevoelige velden (API-key, wachtwoord, AES-sleutel) worden
 *            nooit als plaintext opgeslagen in de audit-log of foutberichten.
 */
class Fmea12AesSleutelInLogsTest extends FmeaBaseTest {

    private static final String TEST_API_KEY    = "test-swiftsend-key";
    private static final String TEST_AES_KEY    = "EZLzg025mt6HbHesB93kP1X60RMYml1fsNY76P1ibpQ=";
    private static final String TEST_WACHTWOORD = "admin";

    @Test
    void audit_log_bevat_geen_api_key_bij_succesvolle_verzending() {
        stubProviderSucces();

        String tenantId = "fmea12-tenant-succes";
        consumer.consume(toAmqpMessage(swiftSendBericht(tenantId)));

        List<NotificationLogEntity> logs = notificationLogDAO.findByTenantId(tenantId);
        assertThat(logs).hasSize(1);

        String errorMessage = logs.get(0).getErrorMessage();
        if (errorMessage != null) {
            assertThat(errorMessage)
                    .doesNotContain(TEST_API_KEY)
                    .doesNotContain(TEST_AES_KEY)
                    .doesNotContain(TEST_WACHTWOORD);
        }
    }

    @Test
    void audit_log_bevat_geen_credentials_bij_mislukte_verzending() {
        stubProviderFout(401);

        String tenantId = "fmea12-tenant-fout";
        consumer.consume(toAmqpMessage(swiftSendBericht(tenantId)));

        List<NotificationLogEntity> logs = notificationLogDAO.findByTenantId(tenantId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo("FAILED");

        String errorMessage = logs.get(0).getErrorMessage();
        if (errorMessage != null) {
            assertThat(errorMessage)
                    .as("Foutbericht in audit-log mag nooit de AES-sleutel of API-key bevatten")
                    .doesNotContain(TEST_API_KEY)
                    .doesNotContain(TEST_AES_KEY)
                    .doesNotContain(TEST_WACHTWOORD);
        }
    }
}
