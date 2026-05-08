package com.example.atx24softwarearchitectuurkwaliteit.dto;

public class NotificationRequest {
    private String title;
    private String message;
    private String type;  // e.g., "ALERT", "INFO", "WARNING"
    private String source; // e.g., "OPENMRS"
    private String recipientId;

    public NotificationRequest() {}

    public NotificationRequest(String title, String message, String type, String source, String recipientId) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.source = source;
        this.recipientId = recipientId;
    }

    // Getters and Setters
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
}
