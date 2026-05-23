package com.example.atx24softwarearchitectuurkwaliteit.provider.asyncflow;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProvider;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AsyncFlowProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(AsyncFlowProvider.class);

    private final AsyncFlowService asyncFlowService;
    private final String defaultPriority;

    public AsyncFlowProvider(AsyncFlowService asyncFlowService,
                             @Value("${PROVIDERS_ASYNCFLOW_DEFAULT_PRIORITY:}") String defaultPriority) {
        this.asyncFlowService = asyncFlowService;
        this.defaultPriority = defaultPriority;
    }

    @Override
    public String getProviderName() {
        return ProviderType.ASYNCFLOW;
    }

    @Override
    public ProviderSendResult sendMessage(NotificationQueueMessage message) {
        try {
            log.info("Sending message via AsyncFlow provider to recipient: {}", message.getRecipient());

            AsyncFlowRequest request = new AsyncFlowRequest();
            request.setRecipient(message.getRecipient());
            request.setMessage(message.getBody());

            // Priority logica zonder afhankelijkheid van getPriority() op het bericht
            String priority = determinePriority();
            request.setPriority(priority);

            AsyncFlowResponse response = asyncFlowService.send(request);

            if (response != null && response.isAccepted()) {
                log.info("AsyncFlow message queued successfully with ID: {}", response.getTrackingId());
                return ProviderSendResult.send(response.getTrackingId());
            } else {
                String errMsg = response != null ? response.getMessage() : "No response from AsyncFlow";
                log.warn("AsyncFlow message failed to queue: {}", errMsg);
                return ProviderSendResult.error(message.getNotificationId().toString(), errMsg);
            }
        } catch (Exception e) {
            log.error("Exception while sending via AsyncFlow", e);
            return ProviderSendResult.error(message.getNotificationId().toString(), e.getMessage());
        }
    }

    private String determinePriority() {
        // Gebruik geconfigureerde waarde uit .env / application.properties
        if (defaultPriority != null && !defaultPriority.trim().isEmpty()) {
            return defaultPriority.trim();
        }

        // Laatste fallback
        log.warn("No priority configured for AsyncFlow. Using 'normal' as last resort.");
        return "normal";
    }
}