package com.example.atx24softwarearchitectuurkwaliteit.provider.swiftsend;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwiftSendProviderTest {

    private SwiftSendClient swiftSendClient;
    private SwiftSendProvider provider;

    @BeforeEach
    void setUp() {
        swiftSendClient = mock(SwiftSendClient.class);
        provider = new SwiftSendProvider(swiftSendClient);
    }

    private NotificationQueueMessage buildMessage() {
        return new NotificationQueueMessage(
                UUID.randomUUID(),
                "test-tenant",
                "+31612345678",
                "Afspraak herinnering",
                "U heeft morgen een afspraak.",
                ProviderType.SWIFTSEND,
                "APPOINTMENT_REMINDER_24H",
                Instant.now()
        );
    }

    @Test
    void getProviderName_returnsSwiftSend() {
        assertThat(provider.getProviderName()).isEqualTo(ProviderType.SWIFTSEND);
    }

    @Test
    void sendMessage_returnsSuccessWhenClientReturnsSuccessResponse() {
        SwiftSendResponse response = new SwiftSendResponse();
        response.setSuccess(true);
        response.setMessageId("SS-MSG-001");
        when(swiftSendClient.send(any(SwiftSendRequest.class))).thenReturn(response);

        ProviderSendResult result = provider.sendMessage(buildMessage());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo("SEND");
    }

    @Test
    void sendMessage_returnsErrorWhenClientReturnsFailureResponse() {
        SwiftSendResponse response = new SwiftSendResponse();
        response.setSuccess(false);
        response.setError("Recipient unreachable");
        when(swiftSendClient.send(any(SwiftSendRequest.class))).thenReturn(response);

        ProviderSendResult result = provider.sendMessage(buildMessage());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo("ERROR");
        assertThat(result.getErrorMessage()).isEqualTo("Recipient unreachable");
    }

    @Test
    void sendMessage_returnsErrorWhenClientReturnsNull() {
        when(swiftSendClient.send(any(SwiftSendRequest.class))).thenReturn(null);

        ProviderSendResult result = provider.sendMessage(buildMessage());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("No response from SwiftSend");
    }

    @Test
    void sendMessage_returnsErrorWhenClientThrowsException() {
        when(swiftSendClient.send(any(SwiftSendRequest.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        ProviderSendResult result = provider.sendMessage(buildMessage());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo("ERROR");
        assertThat(result.getErrorMessage()).contains("Connection refused");
    }
}
