package com.example.atx24softwarearchitectuurkwaliteit.provider.legacylink;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProvider;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LegacyLinkProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(LegacyLinkProvider.class);
    private final LegacyLinkClient legacyLinkClient;

    public LegacyLinkProvider(LegacyLinkClient legacyLinkClient) {
        this.legacyLinkClient = legacyLinkClient;
    }

    @Override
    public ProviderType GetType() {
        return ProviderType.LEGACYLINK;
    }

    @Override
    public ProviderSendResult sendMessage(NotificationQueueMessage message) {
        try {
            log.info("Sending LegacyLink message via LegacyLink provider to recipient: {}", message.getRecipient());

            LegacyLinkRequest request = new LegacyLinkRequest();
            request.setPhoneNumber(message.getRecipient());
            request.setMessageText(message.getBody());
            request.setSenderIdentification(message.getSubject());

            LegacyLinkResponse response = legacyLinkClient.send(request);

            if (response != null && response.getStatusCode() == 200 && response.getMessageReference() != null && !response.getMessageReference().isBlank()) {
                log.info("LegacyLink message sent successfully with reference {}", response.getMessageReference());
                return ProviderSendResult.send(response.getMessageReference());
            }

            log.warn("LegacyLink message failed to send");
            return ProviderSendResult.error(message.getNotificationId().toString());
        } catch (Exception e) {
            log.error("Error sending LegacyLink message to {}: {}", message.getRecipient(), e.getMessage(), e);
            return ProviderSendResult.error(message.getNotificationId().toString());
        }
    }
}