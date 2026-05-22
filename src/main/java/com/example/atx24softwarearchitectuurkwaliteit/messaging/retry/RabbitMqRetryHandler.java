package com.example.atx24softwarearchitectuurkwaliteit.messaging.retry;

import com.example.atx24softwarearchitectuurkwaliteit.config.RabbitMQConfig;
import com.example.atx24softwarearchitectuurkwaliteit.config.RetryQueueConfig;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqRetryHandler implements RetryHandler {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqRetryHandler.class);

    private final RabbitTemplate rabbitTemplate;
    private final RetryPolicy retryPolicy;
    private final MeterRegistry meterRegistry;

    public RabbitMqRetryHandler(RabbitTemplate rabbitTemplate,
                                RetryPolicy retryPolicy,
                                MeterRegistry meterRegistry) {
        this.rabbitTemplate = rabbitTemplate;
        this.retryPolicy = retryPolicy;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onFailure(Message rawMessage, NotificationQueueMessage payload) {
        RetryContext context = RetryContext.fromMessageProperties(rawMessage.getMessageProperties());
        int retryCount = context.retryCount();

        if (retryPolicy.shouldRetry(retryCount)) {
            scheduleRetry(payload, context);
        } else {
            sendToDeadLetter(payload, retryCount);
        }
    }

    private void scheduleRetry(NotificationQueueMessage payload, RetryContext context) {
        int nextAttempt = context.retryCount() + 1;
        long delayMs = retryPolicy.getDelayMillis(context.retryCount());
        String retryExchange = RetryQueueConfig.retryExchangeName(nextAttempt);

        log.warn("Retry ingepland (poging {}/{}) | exchange={} | vertraging={}ms | notificatieId={}",
                nextAttempt, retryPolicy.getMaxRetries(),
                retryExchange, delayMs, payload.getNotificationId());

        rabbitTemplate.convertAndSend(retryExchange, "", payload, message -> {
            message.getMessageProperties().setHeader(RetryContext.HEADER_RETRY_COUNT, nextAttempt);
            message.getMessageProperties().setHeader(
                    RetryContext.HEADER_FIRST_FAILED_AT,
                    context.firstFailedAt().toString());
            return message;
        });

        meterRegistry.counter("notifications_retry_total",
                "attempt", String.valueOf(nextAttempt),
                "provider", payload.getProvider().name()).increment();
    }

    private void sendToDeadLetter(NotificationQueueMessage payload, int retryCount) {
        log.error("Max retries ({}) bereikt — bericht naar dead-letter | notificatieId={}",
                retryCount, payload.getNotificationId());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.DEAD_LETTER_EXCHANGE,
                RabbitMQConfig.DEAD_LETTER_ROUTING_KEY,
                payload);

        meterRegistry.counter("notifications_dead_letter_total",
                "provider", payload.getProvider().name()).increment();
    }
}
