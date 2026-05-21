package com.example.atx24softwarearchitectuurkwaliteit.messaging.queue;

import com.example.atx24softwarearchitectuurkwaliteit.config.RabbitMQConfig;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProvider;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProviderFactory;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class RabbitMQConsumer {
    private static final Logger log = LoggerFactory.getLogger(RabbitMQConsumer.class);
    private final MessagingProviderFactory providerFactory;

    public RabbitMQConsumer(MessagingProviderFactory providerFactory) {this.providerFactory = providerFactory;}

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void consume(NotificationQueueMessage message) {
        MessagingProvider provider = providerFactory.get(message.getProvider());
        ProviderSendResult result = provider.sendMessage(message);

        if (!result.isSuccess()) {
            log.error("Failed to send notification {} via {}: {}", message.getNotificationId(), message.getProvider(), result.getErrorMessage());
            throw new AmqpRejectAndDontRequeueException("Provider send failed: " + result.getErrorMessage());
        }

        log.info("Notification {} sent successfully via {}", message.getNotificationId(), message.getProvider());
    }
}
