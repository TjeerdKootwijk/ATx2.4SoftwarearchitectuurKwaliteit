package com.example.atx24softwarearchitectuurkwaliteit.provider.legacylink;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyLinkProviderTest {

    @Test
    void sendMessage_returnsSuccessWhenClientReturns200Response() {
        LegacyLinkClient legacyLinkClient = mock(LegacyLinkClient.class);
        LegacyLinkProvider provider = new LegacyLinkProvider(legacyLinkClient);

        UUID notificationId = UUID.randomUUID();
        NotificationQueueMessage message = new NotificationQueueMessage(
            notificationId,
            "+31612345678",
            "YourCompany",
            "Hello from LegacyLink",
            ProviderType.LEGACYLINK,
            "SMS",
            Instant.now()
        );

        LegacyLinkResponse response = new LegacyLinkResponse();
        response.setStatusCode(200);
        response.setStatusMessage("SMS sent successfully");
        response.setMessageReference("LGC-A1B2C3D4E5F67890");
        when(legacyLinkClient.send(org.mockito.ArgumentMatchers.any(LegacyLinkRequest.class))).thenReturn(response);

        ProviderSendResult result = provider.sendMessage(message);

        assertTrue(result.isSuccess());
        assertEquals("LGC-A1B2C3D4E5F67890", result.getProviderMessageId());
        assertEquals("SEND", result.getStatus());
    }

    @Test
    void getType_returnsLegacyLink() {
        LegacyLinkProvider provider = new LegacyLinkProvider(mock(LegacyLinkClient.class));

        assertEquals(ProviderType.LEGACYLINK, provider.GetType());
    }
}