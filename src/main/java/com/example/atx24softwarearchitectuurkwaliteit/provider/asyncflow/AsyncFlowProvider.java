package com.example.atx24softwarearchitectuurkwaliteit.provider.asyncflow;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProvider;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AsyncFlowProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(AsyncFlowProvider.class);
    private final AsyncFlowClient asyncFlowClient;

    public AsyncFlowProvider(AsyncFlowClient asyncFlowClient) {
        this.asyncFlowClient = asyncFlowClient;
    }

    @Override
    public ProviderType GetType() {
        return ProviderType.ASYNCFLOW;
    }

    @Override
    public ProviderSendResult sendMessage(NotificationQueueMessage message) {
        try {
            log.info("Sending message via AsyncFlow provider to recipient: {}", message.getRecipient());

            AsyncFlowRequest request = new AsyncFlowRequest();
            request.setRecipient(message.getRecipient());
            request.setMessage(message.getBody());
            request.setPriority("normal");

            AsyncFlowResponse response = asyncFlowClient.send(request);

            if (response != null && response.isAccepted()) {
                log.info("AsyncFlow message queued successfully with ID: {}", response.getTrackingId());
                return ProviderSendResult.send(response.getTrackingId());
            } else {
                log.warn("AsyncFlow message failed to queue: {}",
                        response != null ? response.getMessage() : "No response");
                return ProviderSendResult.error(message.getNotificationId().toString());
            }

        } catch (Exception e) {
            return ProviderSendResult.error(message.getNotificationId().toString());
        }
    }
}
