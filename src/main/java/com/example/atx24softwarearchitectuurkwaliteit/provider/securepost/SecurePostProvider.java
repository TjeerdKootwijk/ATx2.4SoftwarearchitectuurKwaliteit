package com.example.atx24softwarearchitectuurkwaliteit.provider.securepost;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProvider;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SecurePostProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(SecurePostProvider.class);
    private final SecurePostClient securePostClient;

    public SecurePostProvider(SecurePostClient securePostClient) {
        this.securePostClient = securePostClient;
    }

    @Override
    public ProviderType GetType() {
        return ProviderType.SECUREPOST;
    }

    @Override
    public ProviderSendResult sendMessage(NotificationQueueMessage message) {
        try {
            log.info("Sending message via SecurePost provider to recipient: {}", message.getRecipient());

            SecurePostRequest request = new SecurePostRequest();
            request.setFormat("SMS");
            request.setRecipient(message.getRecipient());
            request.setBody(message.getBody());
            request.setSubject("Notification");

            SecurePostResponse response = securePostClient.send(request);

            if (response != null && response.isDelivered()) {
                log.info("SecurePost message delivered, trackingId: {}", response.getTrackingId());
                return ProviderSendResult.send(message.getNotificationId().toString());
            } else {
                String error = response != null ? response.getErrorMessage() : "null response";
                log.warn("SecurePost message failed to deliver: {}", error);
                return ProviderSendResult.error(message.getNotificationId().toString());
            }

        } catch (Exception e) {
            log.error("Error sending SecurePost message to {}: {}", message.getRecipient(), e.getMessage(), e);
            return ProviderSendResult.error(message.getNotificationId().toString());
        }
    }
}
