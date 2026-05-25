package com.example.atx24softwarearchitectuurkwaliteit.provider.securepost;

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

class SecurePostProviderTest {

    private SecurePostClient securePostClient;
    private SecurePostProvider provider;

    @BeforeEach
    void setUp() {
        securePostClient = mock(SecurePostClient.class);
        provider = new SecurePostProvider(securePostClient);
    }

    private NotificationQueueMessage buildMessage() {
        return new NotificationQueueMessage(
                UUID.randomUUID(),
                "test-tenant",
                "+31699887766",
                "Herinnering",
                "Uw afspraak is morgen om 10:00.",
                ProviderType.SECUREPOST,
                "APPOINTMENT_REMINDER_24H",
                Instant.now()
        );
    }

    @Test
    void getProviderName_returnsSecurePost() {
        assertThat(provider.getProviderName()).isEqualTo(ProviderType.SECUREPOST);
    }

    @Test
    void sendMessage_returnsSuccessWhenDelivered() {
        SecurePostResponse response = new SecurePostResponse();
        response.setDelivered(true);
        when(securePostClient.send(any(SecurePostRequest.class))).thenReturn(response);

        ProviderSendResult result = provider.sendMessage(buildMessage());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo("SEND");
    }

    @Test
    void sendMessage_returnsErrorWhenNotDelivered() {
        SecurePostResponse response = new SecurePostResponse();
        response.setDelivered(false);
        when(securePostClient.send(any(SecurePostRequest.class))).thenReturn(response);

        ProviderSendResult result = provider.sendMessage(buildMessage());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo("ERROR");
    }

    @Test
    void sendMessage_returnsErrorWhenClientReturnsNull() {
        when(securePostClient.send(any(SecurePostRequest.class))).thenReturn(null);

        ProviderSendResult result = provider.sendMessage(buildMessage());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("No response from SecurePost");
    }

    @Test
    void sendMessage_returnsErrorWhenClientThrowsException() {
        when(securePostClient.send(any(SecurePostRequest.class)))
                .thenThrow(new RuntimeException("SSL handshake failed"));

        ProviderSendResult result = provider.sendMessage(buildMessage());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("SSL handshake failed");
    }
}
