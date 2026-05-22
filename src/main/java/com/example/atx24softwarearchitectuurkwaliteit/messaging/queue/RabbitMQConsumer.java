package com.example.atx24softwarearchitectuurkwaliteit.messaging.queue;

import com.example.atx24softwarearchitectuurkwaliteit.config.RabbitMQConfig;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.retry.RetryContext;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.retry.RetryHandler;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProvider;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProviderFactory;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Component;

/**
 * Consumeert NotificationQueueMessages en stuurt ze via de juiste MessagingProvider.
 * Bij een fout delegeert de consumer aan RetryHandler, die exponential backoff
 * toepast via aparte retry-queues. Pas na het maximum aantal pogingen belandt
 * een bericht in de dead-letter queue.
 */
@Component
public class RabbitMQConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConsumer.class);

    private final MessagingProviderFactory providerFactory;
    private final MeterRegistry meterRegistry;
    private final RetryHandler retryHandler;
    private final Jackson2JsonMessageConverter messageConverter;

    public RabbitMQConsumer(MessagingProviderFactory providerFactory,
                            MeterRegistry meterRegistry,
                            RetryHandler retryHandler,
                            Jackson2JsonMessageConverter messageConverter) {
        this.providerFactory = providerFactory;
        this.meterRegistry = meterRegistry;
        this.retryHandler = retryHandler;
        this.messageConverter = messageConverter;
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void consume(Message rawMessage) {
        NotificationQueueMessage message = (NotificationQueueMessage) messageConverter.fromMessage(rawMessage);
        RetryContext context = RetryContext.fromMessageProperties(rawMessage.getMessageProperties());

        log.info("------------------------------------------------");
        log.info("Notificatie ontvangen | poging={}/{} | id={}",
                context.retryCount() == 0 ? "1" : context.retryCount() + 1,
                3 + 1,
                message.getNotificationId());
        log.info("  Provider  : {}", message.getProvider());
        log.info("  Ontvanger : {}", message.getRecipient());
        log.info("  Onderwerp : {}", message.getSubject());
        log.info("------------------------------------------------");

        MessagingProvider provider = providerFactory.get(message.getProvider());
        ProviderSendResult result = provider.sendMessage(message);

        if (!result.isSuccess()) {
            log.error("Bericht MISLUKT via {} | notificatieId={}",
                    message.getProvider(), message.getNotificationId());
            meterRegistry.counter("notifications_sent_total",
                    "status", "failed",
                    "provider", message.getProvider().name()).increment();
            retryHandler.onFailure(rawMessage, message);
            return;
        }

        log.info("Bericht VERSTUURD via {} | providerMessageId={}",
                message.getProvider(), result.getProviderMessageId());
        meterRegistry.counter("notifications_sent_total",
                "status", "success",
                "provider", message.getProvider().name()).increment();
    }
}
