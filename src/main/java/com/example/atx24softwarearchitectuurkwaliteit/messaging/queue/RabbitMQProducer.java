package com.example.atx24softwarearchitectuurkwaliteit.messaging.queue;

import com.example.atx24softwarearchitectuurkwaliteit.config.RabbitMQConfig;
import com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto.NotificationQueueMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitMQProducer {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(NotificationQueueMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATION_EXCHANGE,
                RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                message);
    }

    public void publish24hDelayed(NotificationQueueMessage message,
                                  long delayMillis) {

        rabbitTemplate.convertAndSend(
                "",
                "reminder-24h-delay-queue",
                message,
                msg -> {

                    msg.getMessageProperties()
                            .setExpiration(String.valueOf(delayMillis));

                    return msg;
                }
        );
    }

    public void publish1hDelayed(NotificationQueueMessage message,
                                 long delayMillis) {

        rabbitTemplate.convertAndSend(
                "",
                "reminder-1h-delay-queue",
                message,
                msg -> {

                    msg.getMessageProperties()
                            .setExpiration(String.valueOf(delayMillis));

                    return msg;
                }
        );
    }
}
