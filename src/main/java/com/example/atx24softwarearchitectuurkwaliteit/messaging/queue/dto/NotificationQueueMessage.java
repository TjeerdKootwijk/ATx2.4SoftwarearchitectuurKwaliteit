package com.example.atx24softwarearchitectuurkwaliteit.messaging.queue.dto;


import java.time.Instant;
import java.util.UUID;

public class NotificationQueueMessage {

    private UUID notificationId;
    private String tenantId;
    private String recipient;
    private String subject;
    private String body;
    private String provider;
    private String messageType;
    private Instant queuedAt;

    public NotificationQueueMessage() {
    }

    public NotificationQueueMessage(
            UUID notificationId,
            String tenantId,
            String recipient,
            String subject,
            String body,
            String provider,
            String messageType,
            Instant queuedAt) {
        this.notificationId = notificationId;
        this.tenantId = tenantId;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.provider = provider;
        this.messageType = messageType;
        this.queuedAt = queuedAt;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(UUID notificationId) {
        this.notificationId = notificationId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(Instant queuedAt) {
        this.queuedAt = queuedAt;
    }
}