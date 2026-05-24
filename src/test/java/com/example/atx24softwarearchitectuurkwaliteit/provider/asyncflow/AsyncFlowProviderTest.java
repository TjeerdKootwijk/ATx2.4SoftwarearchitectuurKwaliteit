package com.example.atx24softwarearchitectuurkwaliteit.provider.asyncflow;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsyncFlowProviderTest {

    private NotificationQueueMessage buildMessage() {
        return new NotificationQueueMessage(
                UUID.randomUUID(),
                "test-tenant",
                "+31687654321",
                "Afspraak herinnering",
                "U heeft over 1 uur een afspraak.",
                ProviderType.ASYNCFLOW,
                "APPOINTMENT_REMINDER_1H",
                Instant.now()
        );
    }

    @Test
    void getProviderName_returnsAsyncFlow() {
        AsyncFlowService service = mock(AsyncFlowService.class);
        AsyncFlowProvider provider = new AsyncFlowProvider(service, "normal");

        assertThat(provider.getProviderName()).isEqualTo(ProviderType.ASYNCFLOW);
    }

    @Test
    void sendMessage_returnsSuccessWithTrackingIdWhenAccepted() {
        AsyncFlowService service = mock(AsyncFlowService.class);
        AsyncFlowProvider provider = new AsyncFlowProvider(service, "high");

        AsyncFlowResponse response = new AsyncFlowResponse();
        response.setAccepted(true);
        response.setTrackingId("AF-TRACK-XYZ");
        when(service.send(any(AsyncFlowRequest.class))).thenReturn(response);

        ProviderSendResult result = provider.sendMessage(buildMessage());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderMessageId()).isEqualTo("AF-TRACK-XYZ");
    }

    @Test
    void sendMessage_returnsErrorWhenNotAccepted() {
        AsyncFlowService service = mock(AsyncFlowService.class);
        AsyncFlowProvider provider = new AsyncFlowProvider(service, "normal");

        AsyncFlowResponse response = new AsyncFlowResponse();
        response.setAccepted(false);
        response.setMessage("Queue full");
        when(service.send(any(AsyncFlowRequest.class))).thenReturn(response);

        ProviderSendResult result = provider.sendMessage(buildMessage());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Queue full");
    }

    @Test
    void sendMessage_returnsErrorWhenServiceThrowsException() {
        AsyncFlowService service = mock(AsyncFlowService.class);
        AsyncFlowProvider provider = new AsyncFlowProvider(service, "normal");
        when(service.send(any(AsyncFlowRequest.class))).thenThrow(new RuntimeException("Timeout"));

        ProviderSendResult result = provider.sendMessage(buildMessage());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Timeout");
    }

    @Test
    void sendMessage_usesFallbackPriorityWhenNoneConfigured() {
        AsyncFlowService service = mock(AsyncFlowService.class);
        AsyncFlowProvider provider = new AsyncFlowProvider(service, "");

        AsyncFlowResponse response = new AsyncFlowResponse();
        response.setAccepted(true);
        response.setTrackingId("AF-001");
        when(service.send(any(AsyncFlowRequest.class))).thenReturn(response);

        ProviderSendResult result = provider.sendMessage(buildMessage());

        assertThat(result.isSuccess()).isTrue();
    }
}
