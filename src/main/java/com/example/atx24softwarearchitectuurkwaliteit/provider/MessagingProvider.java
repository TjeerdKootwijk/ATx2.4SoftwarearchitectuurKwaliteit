package com.example.atx24softwarearchitectuurkwaliteit.provider;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;

public interface MessagingProvider {
    ProviderType GetType();

    ProviderSendResult sendMessage(NotificationQueueMessage message);
}

