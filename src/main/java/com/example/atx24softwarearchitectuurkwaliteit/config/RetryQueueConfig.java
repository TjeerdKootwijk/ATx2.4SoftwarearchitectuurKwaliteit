package com.example.atx24softwarearchitectuurkwaliteit.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declareert vijf retry-queues met oplopende TTL (exponential backoff).
 * Na afloop van de TTL routeert RabbitMQ het bericht automatisch terug
 * naar de hoofdqueue via de Dead Letter Exchange.
 *
 * Retry 1:  5s  | Retry 2: 30s | Retry 3: 2min
 * Retry 4:  5min | Retry 5: 10min → daarna dead-letter queue
 */
@Configuration
public class RetryQueueConfig {

    private static final int[] RETRY_DELAYS_MS = {
            5_000,
            30_000,
            120_000,
            300_000,
            600_000
    };

    public static String retryExchangeName(int retryNumber) {
        return "notification.retry.exchange." + retryNumber;
    }

    public static String retryQueueName(int retryNumber) {
        return "notification.retry.queue." + retryNumber;
    }

    // ========== Retry 1: 5 seconden ==========

    @Bean
    public FanoutExchange retryExchange1() {
        return new FanoutExchange(retryExchangeName(1), true, false);
    }

    @Bean
    public Queue retryQueue1() {
        return buildRetryQueue(1);
    }

    @Bean
    public Binding retryBinding1(
            @Qualifier("retryQueue1") Queue retryQueue1,
            @Qualifier("retryExchange1") FanoutExchange retryExchange1) {
        return BindingBuilder.bind(retryQueue1).to(retryExchange1);
    }

    // ========== Retry 2: 30 seconden ==========

    @Bean
    public FanoutExchange retryExchange2() {
        return new FanoutExchange(retryExchangeName(2), true, false);
    }

    @Bean
    public Queue retryQueue2() {
        return buildRetryQueue(2);
    }

    @Bean
    public Binding retryBinding2(
            @Qualifier("retryQueue2") Queue retryQueue2,
            @Qualifier("retryExchange2") FanoutExchange retryExchange2) {
        return BindingBuilder.bind(retryQueue2).to(retryExchange2);
    }

    // ========== Retry 3: 2 minuten ==========

    @Bean
    public FanoutExchange retryExchange3() {
        return new FanoutExchange(retryExchangeName(3), true, false);
    }

    @Bean
    public Queue retryQueue3() {
        return buildRetryQueue(3);
    }

    @Bean
    public Binding retryBinding3(
            @Qualifier("retryQueue3") Queue retryQueue3,
            @Qualifier("retryExchange3") FanoutExchange retryExchange3) {
        return BindingBuilder.bind(retryQueue3).to(retryExchange3);
    }

    // ========== Retry 4: 5 minuten ==========

    @Bean
    public FanoutExchange retryExchange4() {
        return new FanoutExchange(retryExchangeName(4), true, false);
    }

    @Bean
    public Queue retryQueue4() {
        return buildRetryQueue(4);
    }

    @Bean
    public Binding retryBinding4(
            @Qualifier("retryQueue4") Queue retryQueue4,
            @Qualifier("retryExchange4") FanoutExchange retryExchange4) {
        return BindingBuilder.bind(retryQueue4).to(retryExchange4);
    }

    // ========== Retry 5: 10 minuten ==========

    @Bean
    public FanoutExchange retryExchange5() {
        return new FanoutExchange(retryExchangeName(5), true, false);
    }

    @Bean
    public Queue retryQueue5() {
        return buildRetryQueue(5);
    }

    @Bean
    public Binding retryBinding5(
            @Qualifier("retryQueue5") Queue retryQueue5,
            @Qualifier("retryExchange5") FanoutExchange retryExchange5) {
        return BindingBuilder.bind(retryQueue5).to(retryExchange5);
    }

    private Queue buildRetryQueue(int retryNumber) {
        return QueueBuilder.durable(retryQueueName(retryNumber))
                .ttl(RETRY_DELAYS_MS[retryNumber - 1])
                .deadLetterExchange(RabbitMQConfig.NOTIFICATION_EXCHANGE)
                .deadLetterRoutingKey(RabbitMQConfig.NOTIFICATION_ROUTING_KEY)
                .build();
    }
}
