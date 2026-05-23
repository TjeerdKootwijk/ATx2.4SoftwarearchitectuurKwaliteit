package com.example.atx24softwarearchitectuurkwaliteit.messaging.queue;

import com.example.atx24softwarearchitectuurkwaliteit.config.RabbitMQConfig;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.retry.RetryContext;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.retry.RetryHandler;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProvider;
import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProviderFactory;
import com.example.atx24softwarearchitectuurkwaliteit.provider.ProviderSendResult;
import com.example.atx24softwarearchitectuurkwaliteit.service.DataService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link NotificationQueueMessage} objects from the notification queue
 * and dispatches them through the appropriate {@link MessagingProvider}.
 *
 * On failure, delegates to {@link RetryHandler} which applies exponential backoff
 * via dedicated retry queues. After the maximum number of attempts the message
 * is moved to the dead-letter queue for manual inspection.
 *
 * Every delivery outcome (success or failure) is persisted via {@link DataService}
 * so that sent notifications can be audited and provider invoices can be verified (FR2).
 */
@Component
public class RabbitMQConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConsumer.class);

    private final MessagingProviderFactory providerFactory;
    private final MeterRegistry meterRegistry;
    private final RetryHandler retryHandler;
    private final Jackson2JsonMessageConverter messageConverter;
    private final DataService dataService;

    public RabbitMQConsumer(MessagingProviderFactory providerFactory,
                            MeterRegistry meterRegistry,
                            RetryHandler retryHandler,
                            Jackson2JsonMessageConverter messageConverter,
                            DataService dataService) {
        this.providerFactory = providerFactory;
        this.meterRegistry = meterRegistry;
        this.retryHandler = retryHandler;
        this.messageConverter = messageConverter;
        this.dataService = dataService;
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void consume(Message rawMessage) {
        NotificationQueueMessage message = (NotificationQueueMessage) messageConverter.fromMessage(rawMessage);
        RetryContext context = RetryContext.fromMessageProperties(rawMessage.getMessageProperties());

        log.info("------------------------------------------------");
        log.info("Notification received | attempt={}/{} | id={}",
                context.retryCount() == 0 ? "1" : context.retryCount() + 1,
                3 + 1,
                message.getNotificationId());
        log.info("  Provider  : {}", message.getProvider());
        log.info("  Ontvanger : {}", message.getRecipient());
        log.info("  Onderwerp : {}", message.getSubject());
        log.info("  MessageType : {}", message.getMessageType());
        log.info("  body : {}", message.getBody());
        log.info("------------------------------------------------");

        MessagingProvider provider = providerFactory.get(message.getProvider());
        ProviderSendResult result = provider.sendMessage(message);

        // Resolve context fields used for both metrics and logging
        String tenantId      = message.getTenantId() != null ? message.getTenantId() : "unknown";
        String notificationId = message.getNotificationId().toString();
        String providerName  = message.getProvider().name();

        if (!result.isSuccess()) {
            log.error("Delivery FAILED via {} | notificationId={}", providerName, notificationId);

            meterRegistry.counter("notifications_sent_total",
                    "status", "failed",
                    "provider", providerName).increment();

            // Persist the failure for auditing and invoice verification (FR2)
            dataService.logNotificationFailed(
                    notificationId, tenantId, providerName,
                    result.getErrorMessage(), context.retryCount());

            retryHandler.onFailure(rawMessage, message);
            return;
        }

        log.info("Delivery SUCCESSFUL via {} | providerMessageId={}", providerName, result.getProviderMessageId());

        meterRegistry.counter("notifications_sent_total",
                "status", "success",
                "provider", providerName).increment();

        // Persist the success for auditing and invoice verification (FR2)
        dataService.logNotificationSent(
                notificationId, tenantId, providerName,
                result.getProviderMessageId(), context.retryCount());
    }
}
