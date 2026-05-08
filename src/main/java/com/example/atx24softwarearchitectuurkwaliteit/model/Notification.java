package com.example.atx24softwarearchitectuurkwaliteit.model;

import java.time.LocalDateTime;

public class Notification {
    private String id;
    private String title;
    private String message;
    private String type;  // e.g., "ALERT", "INFO", "WARNING"
    private String source; // e.g., "OPENMRS"
    private String recipientId;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private String status;  // e.g., "PENDING", "SENT", "FAILED"

    public Notification() {}

    public Notification(String title, String message, String type, String source, String recipientId) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.source = source;
        this.recipientId = recipientId;
        this.createdAt = LocalDateTime.now();
        this.status = "PENDING";
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Notification{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", message='" + message + '\'' +
                ", type='" + type + '\'' +
                ", source='" + source + '\'' +
                ", recipientId='" + recipientId + '\'' +
                ", createdAt=" + createdAt +
                ", sentAt=" + sentAt +
                ", status='" + status + '\'' +
                '}';
    }
}
