package com.example.atx24softwarearchitectuurkwaliteit.service;

import com.example.atx24softwarearchitectuurkwaliteit.model.AppointmentChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StartupRecoveryService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRecoveryService.class);

    private final DataService dataService;
    private final AppointmentService appointmentService;        // ← Veranderd
    private final IdempotencyService idempotencyService;       // Nog steeds nodig voor check

    public StartupRecoveryService(DataService dataService,
                                  AppointmentService appointmentService,
                                  IdempotencyService idempotencyService) {
        this.dataService = dataService;
        this.appointmentService = appointmentService;
        this.idempotencyService = idempotencyService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("🚀 Startup Recovery Service gestart - Herstellen van pending/failed afspraken...");

        try {
            List<AppointmentChangedEvent> pendingAppointments =
                    dataService.findPendingOrFailedAppointments();

            log.info("Gevonden {} afspraken om te herverwerken", pendingAppointments.size());

            int successCount = 0;
            int failedCount = 0;

            for (AppointmentChangedEvent event : pendingAppointments) {
                try {
                    // Idempotency check (aanbevolen om te houden)
                    if (idempotencyService.isAlreadyProcessed(event.getEventId())) {
                        log.info("Event {} is al verwerkt, markeer als processed", event.getEventId());
                        dataService.markAsProcessed(event.getId());
                        successCount++;
                        continue;
                    }

                    log.info("Her verwerken via AppointmentService → Afspraak: {}",
                            event.getAppointmentId());

                    // === Belangrijkste verandering ===
                    appointmentService.processAppointment(event);

                    dataService.markAsProcessed(event.getId());
                    successCount++;

                } catch (Exception e) {
                    log.error("❌ Herverwerking mislukt voor afspraak {}: {}",
                            event.getAppointmentId(), e.getMessage(), e);
                    dataService.markAsFailed(event.getId(), e.getMessage());
                    failedCount++;
                }
            }

            log.info("✅ Startup Recovery afgerond | Succes: {} | Mislukt: {}", successCount, failedCount);

        } catch (Exception e) {
            log.error("❌ Kritieke fout tijdens startup recovery", e);
        }
    }
}