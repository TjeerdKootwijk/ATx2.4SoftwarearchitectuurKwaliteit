package com.example.atx24softwarearchitectuurkwaliteit.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
//import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {


    // Appointment Events Configuration
    public static final String APPOINTMENT_EXCHANGE = "appointment.events";
    public static final String APPOINTMENT_QUEUE = "appointment.events.queue";
    public static final String APPOINTMENT_ROUTING_KEY = "appointment.changed";

    // Notification Configuration
    public static final String NOTIFICATION_EXCHANGE = "notifications.exchange";
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String NOTIFICATION_ROUTING_KEY = "notification.created";
    public static final String DEAD_LETTER_QUEUE = "notification.dead-letter.queue";
    public static final String DEAD_LETTER_EXCHANGE = "notification.dead-letter.exchange";
    public static final String DEAD_LETTER_ROUTING_KEY = "notification.dead-letter";

    // ========== Appointment Events ==========
    @Bean
    public DirectExchange appointmentExchange() {
        return new DirectExchange(APPOINTMENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue appointmentQueue() {
        return new Queue(APPOINTMENT_QUEUE, true);
    }

    @Bean
    public Binding appointmentBinding(Queue appointmentQueue, DirectExchange appointmentExchange) {
        return BindingBuilder.bind(appointmentQueue)
                .to(appointmentExchange)
                .with(APPOINTMENT_ROUTING_KEY);
    }

    // ========== Notification Events ==========
    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding notificationBinding(
            @Qualifier("notificationQueue") Queue notificationQueue,
            @Qualifier("notificationExchange") DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(notificationExchange)
                .with(NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding(
            @Qualifier("deadLetterQueue") Queue deadLetterQueue,
            @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange) {

        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
