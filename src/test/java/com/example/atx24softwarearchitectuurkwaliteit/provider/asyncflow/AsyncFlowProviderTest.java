package com.example.atx24softwarearchitectuurkwaliteit.provider.asyncflow;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests voor {@link AsyncFlowProvider}.
 *
 * Kern: AsyncFlow is asynchroon. Een geaccepteerd bericht mag NIET als definitief
 * succes worden behandeld, maar als PENDING — de echte afleverstatus wordt later
 * door de {@link AsyncFlowStatusPoller} opgehaald.
 */
class AsyncFlowProviderTest {

    private NotificationQueueMessage message() {
        return new NotificationQueueMessage(
                UUID.randomUUID(), "test-tenant", "+31612345678",
                "Appointment Reminder", "Hallo", ProviderType.ASYNCFLOW,
                "APPOINTMENT_REMINDER", Instant.now());
    }

    @Test
    void acceptedMessage_returnsPendingNotSuccess() {
        AsyncFlowService service = mock(AsyncFlowService.class);
        AsyncFlowProvider provider = new AsyncFlowProvider(service, "normal");

        AsyncFlowResponse response = new AsyncFlowResponse();
        response.setAccepted(true);
        response.setTrackingId("ASF-12345");
        when(service.send(any(AsyncFlowRequest.class))).thenReturn(response);

        ProviderSendResult result = provider.sendMessage(message());

        assertTrue(result.isSuccess(), "geaccepteerd => geen retry, dus isSuccess true");
        assertTrue(result.isPending(), "AsyncFlow-acceptatie moet PENDING zijn, niet definitief succes");
        assertEquals("PENDING", result.getStatus());
        assertEquals("ASF-12345", result.getProviderMessageId(), "trackingId moet doorgegeven worden");
    }

    @Test
    void notAcceptedMessage_returnsError() {
        AsyncFlowService service = mock(AsyncFlowService.class);
        AsyncFlowProvider provider = new AsyncFlowProvider(service, "normal");

        AsyncFlowResponse response = new AsyncFlowResponse();
        response.setAccepted(false);
        response.setMessage("rejected");
        when(service.send(any(AsyncFlowRequest.class))).thenReturn(response);

        ProviderSendResult result = provider.sendMessage(message());

        assertFalse(result.isSuccess());
        assertFalse(result.isPending());
    }

    @Test
    void noResponse_returnsError() {
        AsyncFlowService service = mock(AsyncFlowService.class);
        AsyncFlowProvider provider = new AsyncFlowProvider(service, "normal");
        when(service.send(any(AsyncFlowRequest.class))).thenReturn(null);

        ProviderSendResult result = provider.sendMessage(message());

        assertFalse(result.isSuccess());
    }

    @Test
    void getProviderName_returnsAsyncFlow() {
        AsyncFlowProvider provider = new AsyncFlowProvider(mock(AsyncFlowService.class), "normal");
        assertEquals(ProviderType.ASYNCFLOW, provider.getProviderName());
    }
}
