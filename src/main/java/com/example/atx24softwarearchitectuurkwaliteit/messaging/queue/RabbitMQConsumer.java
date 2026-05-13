package com.example.atx24softwarearchitectuurkwaliteit.messaging.queue;

import com.example.atx24softwarearchitectuurkwaliteit.config.RabbitMQConfig;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProvider;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProviderFactory;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class RabbitMQConsumer {
    private final MessagingProviderFactory providerFactory;

    public RabbitMQConsumer(MessagingProviderFactory providerFactory) {this.providerFactory = providerFactory;}

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void consume(NotificationQueueMessage message) {
        MessagingProvider provider = providerFactory.get(message.getProvider());

        ProviderSendResult result = provider.sendMessage(message);

        System.out.println("Message sent to " + message.getProvider());
        System.out.println("Status " + result.getStatus());
    }


}
