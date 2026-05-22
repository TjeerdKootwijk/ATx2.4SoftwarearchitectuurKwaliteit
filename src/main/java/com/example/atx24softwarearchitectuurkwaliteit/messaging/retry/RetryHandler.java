package com.example.atx24softwarearchitectuurkwaliteit.messaging.retry;

import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import org.springframework.amqp.core.Message;

public interface RetryHandler {
    void onFailure(Message rawMessage, NotificationQueueMessage payload);
}
