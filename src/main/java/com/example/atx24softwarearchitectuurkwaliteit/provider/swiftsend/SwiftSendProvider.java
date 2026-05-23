package com.example.atx24softwarearchitectuurkwaliteit.provider.swiftsend;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProvider;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SwiftSendProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(SwiftSendProvider.class);
    private final SwiftSendClient swiftSendClient;

    public SwiftSendProvider(SwiftSendClient swiftSendClient) {
        this.swiftSendClient = swiftSendClient;
    }

    @Override
    public ProviderType GetType() {
        return ProviderType.SWIFTSEND;
    }

    @Override
    public ProviderSendResult sendMessage(NotificationQueueMessage message) {
        try {
            log.info("Sending SwiftSend message via SwiftSend provider to recipient: {}", message.getRecipient());

            SwiftSendRequest request = new SwiftSendRequest();
            request.setType("SMS");
            request.setRecipients(List.of(message.getRecipient()));
            request.setContent(message.getBody());

            SwiftSendResponse response = swiftSendClient.send(request);

            if (response != null && response.isSuccess()) {
                log.info("SwiftSend message sent successfully");
                return ProviderSendResult.send(message.getNotificationId().toString());
            } else {
                String errMsg = response != null ? response.getError() : "No response from SwiftSend";
                log.warn("SwiftSend message failed to send: {}", errMsg);
                return ProviderSendResult.error(message.getNotificationId().toString(), errMsg);
            }

        } catch (Exception e) {
            log.error("Error sending SwiftSend message to {}: {}", message.getRecipient(), e.getMessage(), e);
            return ProviderSendResult.error(message.getNotificationId().toString(), e.getMessage());
        }
    }
}
