package com.example.atx24softwarearchitectuurkwaliteit.listener;

import com.example.atx24softwarearchitectuurkwaliteit.model.Notification;
import com.example.atx24softwarearchitectuurkwaliteit.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class NotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(NotificationListener.class);

    @Autowired
    private NotificationService notificationService;

    @RabbitListener(queues = "notification.queue")
    public void receiveMessage(Notification notification) {
        logger.info("Message received from queue: {}", notification);
        notificationService.handleNotificationMessage(notification);
    }
}
