package com.example.atx24softwarearchitectuurkwaliteit.dashboard;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.atx24softwarearchitectuurkwaliteit.fhir.AppointmentEventConverter;
import com.example.atx24softwarearchitectuurkwaliteit.fhir.AppointmentFetcher;
import com.example.atx24softwarearchitectuurkwaliteit.fhir.AppointmentMapper;
import com.example.atx24softwarearchitectuurkwaliteit.fhir.AppointmentValidator;
import com.example.atx24softwarearchitectuurkwaliteit.model.TenantConfiguration;
import com.example.atx24softwarearchitectuurkwaliteit.service.AppointmentService;
import com.example.atx24softwarearchitectuurkwaliteit.service.IdempotencyService;
import com.example.atx24softwarearchitectuurkwaliteit.service.PollingJob;
import com.example.atx24softwarearchitectuurkwaliteit.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FMEA-scenario 11 — <em>Credential-rotatie door ziekenhuis (API-key vervangen zonder update)</em>.
 *
 * <p>Effect uit de FMEA: "Alle polls en notificaties voor die tenant mislukken." De maatregel
 * is: "Fout gelogd als ERROR met tenantId" + "Grafana-alert bij aaneengesloten poll-fouten".
 * Op het dashboard verschijnt die ERROR in het paneel <em>"Errors &amp; Warnings"</em>
 * ({@code detected_level=~"error|warn"}).</p>
 *
 * <p>Deze test simuleert een mislukte poll (zoals een HTTP 401 na key-rotatie) door de fetcher
 * een exception te laten gooien, en toont automatisch aan dat de backend:</p>
 * <ol>
 *   <li>de fout als ERROR logt mét de tenantId — herleidbaar op het dashboard;</li>
 *   <li>de overige tenants gewoon blijft pollen (één kapotte tenant blokkeert de rest niet —
 *       raakt ook scenario 12, geen thread-starvation).</li>
 * </ol>
 */
class TenantPollFailureDashboardTest {

    private TenantService tenantService;
    private AppointmentFetcher fetcher;
    private PollingJob pollingJob;

    private Logger pollingLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        tenantService = mock(TenantService.class);
        fetcher = mock(AppointmentFetcher.class);
        AppointmentMapper mapper = mock(AppointmentMapper.class);
        AppointmentEventConverter converter = mock(AppointmentEventConverter.class);
        AppointmentValidator validator = mock(AppointmentValidator.class);
        AppointmentService appointmentService = mock(AppointmentService.class);
        IdempotencyService idempotencyService = mock(IdempotencyService.class);

        pollingJob = new PollingJob(tenantService, fetcher, mapper, converter,
                validator, appointmentService, idempotencyService);

        pollingLogger = (Logger) LoggerFactory.getLogger(PollingJob.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        pollingLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        pollingLogger.detachAppender(logAppender);
    }

    private TenantConfiguration tenant(String id) {
        TenantConfiguration tenant = new TenantConfiguration(id, "Org " + id);
        tenant.setTimezone("UTC");
        return tenant;
    }

    @Test
    void mislukteTenantPoll_logtErrorMetTenantId() {
        TenantConfiguration kapot = tenant("ziekenhuis-key-gerouteerd");
        when(tenantService.getAllActiveTenants()).thenReturn(List.of(kapot));
        // Simuleert een 401 na credential-rotatie: de fetch faalt voor deze tenant.
        when(fetcher.fetchAppointments(kapot))
                .thenThrow(new RuntimeException("401 Unauthorized — API-key ongeldig"));

        pollingJob.pollOpenMrsAppointments();

        ILoggingEvent error = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Verwacht een ERROR-logregel voor de mislukte poll"));

        assertTrue(error.getFormattedMessage().contains("ziekenhuis-key-gerouteerd"),
                "De foutregel moet de tenantId bevatten zodat het dashboard de aaneengesloten "
                        + "poll-fouten per tenant toont, was: " + error.getFormattedMessage());
    }

    @Test
    void foutBijEenTenant_blokkeertHetPollenVanAndereTenantsNiet() {
        TenantConfiguration kapot = tenant("ziekenhuis-kapot");
        TenantConfiguration gezond = tenant("ziekenhuis-gezond");
        when(tenantService.getAllActiveTenants()).thenReturn(List.of(kapot, gezond));
        when(fetcher.fetchAppointments(kapot))
                .thenThrow(new RuntimeException("401 Unauthorized"));
        when(fetcher.fetchAppointments(gezond)).thenReturn(List.of());

        pollingJob.pollOpenMrsAppointments();

        // Resilience: ondanks de fout bij de eerste tenant wordt de tweede tenant nog gepolled.
        verify(fetcher, times(1)).fetchAppointments(gezond);
    }
}
