package com.example.atx24softwarearchitectuurkwaliteit.messaging.queue;

import com.example.atx24softwarearchitectuurkwaliteit.config.RabbitMQConfig;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProvider;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProviderFactory;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumeert NotificationQueueMessages en stuurt ze door naar de juiste MessagingProvider.
 * De provider wordt bepaald door het 'provider' veld in het bericht (gezet door AppointmentEventListener).
 */
@Component
public class RabbitMQConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConsumer.class);

    private final MessagingProviderFactory providerFactory;

    public RabbitMQConsumer(MessagingProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void consume(NotificationQueueMessage message) {
        log.info("------------------------------------------------");
        log.info("[STAP 3] Notificatie ontvangen uit notification queue");
        log.info("  Notification ID : {}", message.getNotificationId());
        log.info("  Provider        : {}", message.getProvider());
        log.info("  Ontvanger       : {}", message.getRecipient());
        log.info("  Onderwerp       : {}", message.getSubject());
        log.info("  Bericht         : {}", message.getBody());
        log.info("------------------------------------------------");

        try {
            MessagingProvider provider = providerFactory.get(message.getProvider());
            ProviderSendResult result = provider.sendMessage(message);

            if (result.isSuccess()) {
                log.info("[STAP 3✓] Bericht VERSTUURD via {} | providerMessageId={}",
                        message.getProvider(), result.getProviderMessageId());
            } else {
                log.warn("[STAP 3✗] Bericht MISLUKT via {} | providerMessageId={}",
                        message.getProvider(), result.getProviderMessageId());
            }

        } catch (Exception e) {
            log.error("[STAP 3✗] Fout bij versturen via {} voor notificatie {}: {}",
                    message.getProvider(), message.getNotificationId(), e.getMessage(), e);
        }
    }
}
