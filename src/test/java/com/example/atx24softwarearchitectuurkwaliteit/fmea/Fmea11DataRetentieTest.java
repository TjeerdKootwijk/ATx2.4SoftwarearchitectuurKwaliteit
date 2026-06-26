package com.example.atx24softwarearchitectuurkwaliteit.fmea;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.NotificationLogEntity;
import com.example.atx24softwarearchitectuurkwaliteit.model.entity.ProcessedEventEntity;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FMEA Scenario 11 - RPN 10 - MIDDEN - NFR10, NFR11
 *
 * Scenario: Data-retentie cron job faalt (patientdata ouder dan 14 dagen blijft staan).
 * Effect:   AVG-overtreding; patientgegevens langer bewaard dan toegestaan.
 * Maatregel: cleanupExpiredData() verwijdert:
 *              - processed_events ouder dan 14 dagen (NFR10)
 *              - notification_logs ouder dan 1 jaar (NFR11)
 *            Scheduler draait dagelijks om 02:00 UTC.
 */
class Fmea11DataRetentieTest extends FmeaBaseTest {

    @Test
    void notification_logs_ouder_dan_een_jaar_worden_verwijderd_nfr11() {
        NotificationLogEntity oudLog = new NotificationLogEntity();
        oudLog.setNotificationId("fmea11-oud-" + UUID.randomUUID());
        oudLog.setTenantId("fmea11-tenant");
        oudLog.setProvider(ProviderType.SWIFTSEND);
        oudLog.setStatus("SUCCESS");
        oudLog.setSentAt(LocalDateTime.now().minusYears(2));
        notificationLogDAO.save(oudLog);

        NotificationLogEntity nieuwLog = new NotificationLogEntity();
        nieuwLog.setNotificationId("fmea11-nieuw-" + UUID.randomUUID());
        nieuwLog.setTenantId("fmea11-tenant");
        nieuwLog.setProvider(ProviderType.SWIFTSEND);
        nieuwLog.setStatus("SUCCESS");
        nieuwLog.setSentAt(LocalDateTime.now().minusDays(30));
        notificationLogDAO.save(nieuwLog);

        dataService.cleanupExpiredData();

        List<NotificationLogEntity> resterend = notificationLogDAO.findByTenantId("fmea11-tenant");
        assertThat(resterend)
                .as("Notification logs ouder dan 1 jaar moeten worden verwijderd (NFR11)")
                .noneMatch(l -> l.getSentAt().isBefore(LocalDateTime.now().minusYears(1)));
        assertThat(resterend)
                .as("Recente notification logs moeten behouden blijven")
                .anyMatch(l -> l.getSentAt().isAfter(LocalDateTime.now().minusMonths(6)));
    }

    @Test
    void processed_events_ouder_dan_14_dagen_worden_verwijderd_nfr10() {
        processedEventDAO.save(new ProcessedEventEntity(
                "fmea11-evt-oud", LocalDateTime.now().minusDays(20)));
        processedEventDAO.save(new ProcessedEventEntity(
                "fmea11-evt-nieuw", LocalDateTime.now().minusDays(5)));

        dataService.cleanupExpiredData();

        assertThat(dataService.isEventProcessed("fmea11-evt-oud"))
                .as("Processed events ouder dan 14 dagen moeten worden verwijderd (NFR10)")
                .isFalse();
        assertThat(dataService.isEventProcessed("fmea11-evt-nieuw"))
                .as("Recente processed events moeten behouden blijven")
                .isTrue();
    }
}
