package com.example.atx24softwarearchitectuurkwaliteit.provider.asyncflow;

import com.example.atx24softwarearchitectuurkwaliteit.model.entity.AsyncFlowTrackingEntity;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import com.example.atx24softwarearchitectuurkwaliteit.service.DataService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests voor {@link AsyncFlowStatusPoller}.
 *
 * Verifieert dat de poller de asynchrone afleverstatus correct vertaalt naar een
 * definitieve uitkomst: Completed → SUCCESS, Failed → FAILED, nog bezig → opnieuw
 * proberen, en te oud → FAILED (timeout).
 */
class AsyncFlowStatusPollerTest {

    private AsyncFlowService service;
    private DataService dataService;
    private MeterRegistry registry;
    private AsyncFlowStatusPoller poller;

    private static final int MAX_AGE_MINUTES = 30;

    @BeforeEach
    void setUp() {
        service = mock(AsyncFlowService.class);
        dataService = mock(DataService.class);
        registry = new SimpleMeterRegistry();
        poller = new AsyncFlowStatusPoller(service, dataService, registry, 200, MAX_AGE_MINUTES);
    }

    private AsyncFlowTrackingEntity pending(LocalDateTime submittedAt) {
        return new AsyncFlowTrackingEntity("ASF-1", "notif-1", "test-tenant", 0, submittedAt);
    }

    private AsyncFlowStatusResponse status(String s, String errorDetails) {
        AsyncFlowStatusResponse r = new AsyncFlowStatusResponse();
        r.setTrackingId("ASF-1");
        r.setStatus(s);
        r.setErrorDetails(errorDetails);
        return r;
    }

    @Test
    void completedStatus_logsSuccessAndDeletesTracking() {
        AsyncFlowTrackingEntity record = pending(LocalDateTime.now());
        when(dataService.findPendingAsyncFlow(anyInt())).thenReturn(List.of(record));
        when(service.getStatus("ASF-1")).thenReturn(status("Completed", null));

        poller.pollPendingStatuses();

        verify(dataService).logNotificationSent("notif-1", "test-tenant", ProviderType.ASYNCFLOW, "ASF-1", 0);
        verify(dataService).deleteAsyncFlowTracking(record);
        verify(dataService, never()).logNotificationFailed(any(), any(), any(), any(), anyInt());
        assertEquals(1.0, registry.counter("notifications_sent_total",
                "status", "success", "provider", ProviderType.ASYNCFLOW).count());
    }

    @Test
    void failedStatus_logsFailedWithErrorAndDeletes() {
        AsyncFlowTrackingEntity record = pending(LocalDateTime.now());
        when(dataService.findPendingAsyncFlow(anyInt())).thenReturn(List.of(record));
        when(service.getStatus("ASF-1")).thenReturn(status("Failed", "recipient unreachable"));

        poller.pollPendingStatuses();

        verify(dataService).logNotificationFailed("notif-1", "test-tenant", ProviderType.ASYNCFLOW,
                "recipient unreachable", 0);
        verify(dataService).deleteAsyncFlowTracking(record);
        assertEquals(1.0, registry.counter("notifications_sent_total",
                "status", "failed", "provider", ProviderType.ASYNCFLOW).count());
    }

    @Test
    void stillProcessing_isRetriedNotFinalized() {
        AsyncFlowTrackingEntity record = pending(LocalDateTime.now());
        when(dataService.findPendingAsyncFlow(anyInt())).thenReturn(List.of(record));
        when(service.getStatus("ASF-1")).thenReturn(status("Processing", null));

        poller.pollPendingStatuses();

        verify(dataService).updateAsyncFlowTracking(record);
        verify(dataService, never()).logNotificationSent(any(), any(), any(), any(), anyInt());
        verify(dataService, never()).logNotificationFailed(any(), any(), any(), any(), anyInt());
        verify(dataService, never()).deleteAsyncFlowTracking(any());
    }

    @Test
    void stuckBeyondMaxAge_isMarkedFailed() {
        AsyncFlowTrackingEntity record = pending(LocalDateTime.now().minusMinutes(MAX_AGE_MINUTES + 1));
        when(dataService.findPendingAsyncFlow(anyInt())).thenReturn(List.of(record));
        when(service.getStatus("ASF-1")).thenReturn(status("Processing", null));

        poller.pollPendingStatuses();

        verify(dataService).logNotificationFailed(eq("notif-1"), eq("test-tenant"),
                eq(ProviderType.ASYNCFLOW), anyString(), eq(0));
        verify(dataService).deleteAsyncFlowTracking(record);
    }

    @Test
    void unavailableStatus_whenFresh_isRetriedNotFinalized() {
        AsyncFlowTrackingEntity record = pending(LocalDateTime.now());
        when(dataService.findPendingAsyncFlow(anyInt())).thenReturn(List.of(record));
        when(service.getStatus("ASF-1")).thenReturn(null); // status (tijdelijk) niet op te halen

        poller.pollPendingStatuses();

        verify(dataService).updateAsyncFlowTracking(record);
        verify(dataService, never()).deleteAsyncFlowTracking(any());
    }
}
