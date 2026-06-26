package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * FMEA Scenario 14 - RPN 9 - MIDDEN - NFR1, NFR7
 *
 * Scenario: Multi-tenant piekbelasting (>50 tenants starten tegelijk).
 * Effect:   Thread pool uitgeput; vertraging voor alle tenants.
 * Maatregel: Tenant-validatie werkt onafhankelijk per tenant zonder data-interferentie.
 *            Berichten van onbekende tenants worden geauditlogd (geen stille verwerking).
 */
class Fmea14MultiTenantTest extends FmeaBaseTest {

    @Test
    void actieve_tenant_wordt_herkend_als_geldig() {
        opslaanActieveTenant("fmea14-actieve-tenant");

        assertThat(dataService.isTenantValid("fmea14-actieve-tenant"))
                .as("Actieve tenant moet als geldig worden herkend")
                .isTrue();
    }

    @Test
    void onbekende_tenant_wordt_afgewezen_als_ongeldig() {
        assertThat(dataService.isTenantValid("niet-bestaande-tenant-xyz"))
                .as("Onbekende tenant moet als ongeldig worden afgewezen")
                .isFalse();
    }

    @Test
    void berichten_van_meerdere_tenants_worden_onafhankelijk_geauditlogd() {
        stubProviderSucces();

        consumer.consume(toAmqpMessage(swiftSendBericht("fmea14-tenant-a")));
        consumer.consume(toAmqpMessage(swiftSendBericht("fmea14-tenant-b")));
        consumer.consume(toAmqpMessage(swiftSendBericht("fmea14-tenant-c")));

        List<NotificationLogEntity> logsA = notificationLogDAO.findByTenantId("fmea14-tenant-a");
        List<NotificationLogEntity> logsB = notificationLogDAO.findByTenantId("fmea14-tenant-b");
        List<NotificationLogEntity> logsC = notificationLogDAO.findByTenantId("fmea14-tenant-c");

        assertThat(logsA).as("Tenant A moet exact 1 auditlog-entry hebben").hasSize(1);
        assertThat(logsB).as("Tenant B moet exact 1 auditlog-entry hebben").hasSize(1);
        assertThat(logsC).as("Tenant C moet exact 1 auditlog-entry hebben").hasSize(1);

        // Geen cross-tenant data-interferentie
        assertThat(logsA.get(0).getTenantId()).isEqualTo("fmea14-tenant-a");
        assertThat(logsB.get(0).getTenantId()).isEqualTo("fmea14-tenant-b");
        assertThat(logsC.get(0).getTenantId()).isEqualTo("fmea14-tenant-c");
    }
}
