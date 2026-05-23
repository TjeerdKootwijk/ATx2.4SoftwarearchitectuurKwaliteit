package com.example.atx24softwarearchitectuurkwaliteit.provider.legacylink;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProvider;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Concrete implementatie van de MessagingProvider interface, speciaal voor de "ouderwetse" 
 * XML provider LegacyLink. De Service integreert naadloos in de bestaande ProviderFactory
 * zodat NotificationQueue-berichten automatisch kunnen worden doorgestuurd via deze klasse.
 */
@Service
public class LegacyLinkProvider implements MessagingProvider {


    private static final Logger log = LoggerFactory.getLogger(LegacyLinkProvider.class);
    private final LegacyLinkClient legacyLinkClient;

    public LegacyLinkProvider(LegacyLinkClient legacyLinkClient) {
        this.legacyLinkClient = legacyLinkClient;
    }

    /**
     * Zorgt ervoor dat de ProviderFactory deze instance kiest wanneer berichten in de
     * queue staan vlagd met type 'LEGACYLINK'.
     */
    @Override
    public ProviderType GetType() {
        return ProviderType.LEGACYLINK;
    }

    /**
     * Mapping van het algemene inter-service queue bericht (NotificationQueueMessage) 
     * naar de specifieke attributen voor LegacyLink. Activeert vervolgens de daadwerkelijke verzending
     * in de LegacyLinkClient, waarna het resultaat teruggegeven wordt voor logs/verwerking.
     */
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

            String errMsg = response != null
                    ? "StatusCode=" + response.getStatusCode()
                    : "No response from LegacyLink";
            log.warn("LegacyLink message failed to send: {}", errMsg);
            return ProviderSendResult.error(message.getNotificationId().toString(), errMsg);
        } catch (Exception e) {
            log.error("Error sending LegacyLink message to {}: {}", message.getRecipient(), e.getMessage(), e);
            return ProviderSendResult.error(message.getNotificationId().toString(), e.getMessage());
        }
    }
}