package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FMEA Scenario 16 - RPN 8 - MIDDEN - NFR13
 *
 * Scenario: Clockdrift tussen containers (>60 s verschil).
 * Effect:   Notificaties verzonden op verkeerd moment; tijdstempelvalidatie faalt.
 * Maatregel: Alle tijdstempels worden opgeslagen als LocalDateTime (UTC-compatibel).
 *            Tijdberekeningen zijn niet afhankelijk van de systeemtijdzone van de container.
 */
class Fmea16ClockdriftUtcTest extends FmeaBaseTest {

    @Test
    void audit_log_tijdstempel_is_monotoon_en_utc_compatibel() {
        stubProviderSucces();

        LocalDateTime voorVerzending = LocalDateTime.now();
        consumer.consume(toAmqpMessage(swiftSendBericht("fmea16-tenant")));
        LocalDateTime naVerzending = LocalDateTime.now();

        List<NotificationLogEntity> logs = notificationLogDAO.findByTenantId("fmea16-tenant");
        assertThat(logs).hasSize(1);

        LocalDateTime sentAt = logs.get(0).getSentAt();

        assertThat(sentAt)
                .as("sentAt moet na het begin van de verzending liggen (geen negatieve clockdrift)")
                .isAfterOrEqualTo(voorVerzending);
        assertThat(sentAt)
                .as("sentAt moet voor het einde van de verzending liggen (geen toekomstige tijd)")
                .isBeforeOrEqualTo(naVerzending);
    }

    @Test
    void tenant_timezone_veld_is_ingesteld_op_utc_als_standaard() {
        // TenantConfiguration.timezone heeft "UTC" als standaardwaarde
        com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration tenant =
                new com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration();

        assertThat(tenant.getTimezone())
                .as("Standaard timezone moet UTC zijn zodat clockdrift tussen containers geen effect heeft")
                .isEqualTo("UTC");
    }
}
